package com.zerointrusion.data.repository.registration

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.repository.common.buildMobileDeviceBootstrapUrl
import com.zerointrusion.data.repository.common.buildSignedMobileDeviceNonceJson
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.security.DeviceService
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.NamespaceService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.registration.MobileDeviceRegistrationPreparation
import com.zerointrusion.domain.registration.RegistrationFormInput
import com.zerointrusion.domain.registration.RegistrationResult
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class MobileDeviceBootstrapHandler(
    okHttpClient: OkHttpClient,
    private val keyStoreService: KeyStoreService,
    private val deviceService: DeviceService,
    private val namespaceService: NamespaceService,
    private val signatureService: SignatureService,
    private val stateStore: RegistrationStateStore,
    private val validator: RegistrationValidator,
    private val gson: Gson,
    private val fcmTokenSynchronizer: FcmTokenSynchronizer,
) {
    private val sseClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private data class AdditionMobileDeviceQrDto(
        val publicKey: String,
        val deviceName: String,
        val mobileDeviceId: String,
        val namespaceUrls: List<String>,
        val type: String = "addition_mobile_device",
    )

    private data class BootstrapDekDto(
        val accountId: String?,
        val mobileDeviceId: String?,
        val desktopDeviceId: String?,
        val encDek: String?,
    )

    private data class BootstrapEventDto(
        val accountId: String?,
        val mobileDeviceId: String?,
        val dek: BootstrapDekDto?,
    )

    fun prepare(input: RegistrationFormInput): MobileDeviceRegistrationPreparation {
        val validation = validator.validateForm(input)
        if (!validation.isValid) {
            throw RegistrationFailure.Validation(
                validation.emailError ?: validation.phoneError ?: validation.namespaceNameError ?: validation.apiUrlError
                    ?: "Registration form is invalid.",
            )
        }

        keyStoreService.ensureKeyPair()
        val device = deviceService.createDevice(keyStoreService.getPublicKey())
        val namespace = namespaceService.createNamespace(input.namespaceName, input.apiUrl)
        val namespaceUrls = (listOf(namespace.url) + input.additionalApiUrls)
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (namespaceUrls.size > 3) {
            throw RegistrationFailure.Validation("At most three API URLs can be used for mobile registration.")
        }
        input.additionalApiUrls.forEach { optionalUrl ->
            optionalUrl.trim().takeIf { it.isNotEmpty() }?.let { url ->
                validator.validateNamespaceUrl(url)?.let { throw RegistrationFailure.Validation(it) }
            }
        }
        val qrPayload = gson.toJson(
            AdditionMobileDeviceQrDto(
                publicKey = device.publicKey,
                deviceName = device.deviceName,
                mobileDeviceId = device.mobileDeviceId,
                namespaceUrls = namespaceUrls,
            ),
        )
        return MobileDeviceRegistrationPreparation(
            qrPayload = qrPayload,
            mobileDeviceId = device.mobileDeviceId,
            deviceName = device.deviceName,
            namespaceName = namespace.name,
            namespaceUrl = namespace.url,
            namespaceUrls = namespaceUrls,
            namespaceId = namespace.namespaceId,
            email = input.email.trim(),
            phone = input.phone.trim(),
        )
    }

    suspend fun await(preparation: MobileDeviceRegistrationPreparation): RegistrationResult {
        val nonce = newRequestNonce()
        val signatureJson = buildSignedMobileDeviceNonceJson(
            gson = gson,
            mobileDeviceId = preparation.mobileDeviceId,
            nonce = nonce,
        )
        val url = buildMobileDeviceBootstrapUrl(preparation.namespaceUrl, preparation.mobileDeviceId, nonce)
        Log.d(REG_LOG_TAG, "Mobile registration SSE connect. url=$url signaturePayload=$signatureJson")
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("x-signature", signatureService.signJsonString(signatureJson))
            .build()
        val event = try {
            withTimeout(SSE_TIMEOUT_MILLIS) {
                awaitRegistrationEvent(request)
            }
        } catch (_: TimeoutCancellationException) {
            throw RegistrationFailure.Network("Mobile registration timed out after 3 minutes.")
        }
        val accountId = event.accountId?.trim().orEmpty()
        val mobileDeviceId = event.mobileDeviceId?.trim().orEmpty()
        val dek = event.dek
        val encryptedDek = dek?.encDek?.trim().orEmpty()
        if (accountId.isEmpty() || mobileDeviceId != preparation.mobileDeviceId ||
            dek?.accountId?.trim() != accountId || dek.mobileDeviceId?.trim() != mobileDeviceId || encryptedDek.isEmpty()
        ) {
            throw RegistrationFailure.Server("The mobile registration event did not contain a matching account, device, and DEK.")
        }

        val state = RegistrationState(
            accountId = accountId,
            mobileDeviceId = mobileDeviceId,
            deviceName = preparation.deviceName,
            namespaceName = preparation.namespaceName,
            namespaceUrl = preparation.namespaceUrl,
            namespaceId = preparation.namespaceId,
            email = preparation.email,
            phone = preparation.phone,
            encDek = encryptedDek,
            root = false,
            pendingAdditionalNamespacesScan = preparation.namespaceUrls.size > 1,
            completedAdditionalNamespaceKeys = emptyList(),
        )
        stateStore.save(state)
        runCatching { fcmTokenSynchronizer.sync(state) }
        return RegistrationResult(accountId)
    }

    private suspend fun awaitRegistrationEvent(request: Request): BootstrapEventDto =
        suspendCancellableCoroutine { continuation ->
            val call = sseClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            RegistrationFailure.Network(e.message ?: "Mobile registration event stream failed."),
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val errorBody = response.body.string().trim()
                            Log.e(
                                REG_LOG_TAG,
                                "Mobile registration SSE rejected. http=${response.code} url=${request.url} body=$errorBody",
                            )
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    RegistrationFailure.Server(
                                        errorBody.ifEmpty { "Mobile registration event stream failed with HTTP ${response.code}." },
                                    ),
                                )
                            }
                            return
                        }
                        try {
                            val reader = response.body.charStream().buffered()
                            val data = StringBuilder()
                            while (continuation.isActive) {
                                val line = reader.readLine() ?: break
                                if (line.startsWith("data:")) {
                                    if (data.isNotEmpty()) data.append('\n')
                                    data.append(line.removePrefix("data:").trimStart())
                                } else if (line.isBlank() && data.isNotEmpty()) {
                                    parseCompleteRegistrationEvent(data.toString())?.let { event ->
                                        continuation.resume(event)
                                        return
                                    }
                                    data.setLength(0)
                                }
                            }
                            if (continuation.isActive && data.isNotEmpty()) {
                                parseCompleteRegistrationEvent(data.toString())?.let { event ->
                                    continuation.resume(event)
                                    return
                                }
                            }
                            if (continuation.isActive) {
                                continuation.resumeWithException(RegistrationFailure.Server("Mobile registration event stream ended without registration data."))
                            }
                        } catch (exception: Exception) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    RegistrationFailure.Server(exception.message ?: "Mobile registration event could not be read."),
                                )
                            }
                        }
                    }
                }
            })
        }

    private fun parseCompleteRegistrationEvent(data: String): BootstrapEventDto? {
        val event = runCatching { gson.fromJson(data, BootstrapEventDto::class.java) }
            .onFailure { Log.w(REG_LOG_TAG, "Ignoring unreadable mobile registration SSE event.", it) }
            .getOrNull() ?: return null
        val isComplete = !event.accountId.isNullOrBlank() &&
            !event.mobileDeviceId.isNullOrBlank() &&
            !event.dek?.accountId.isNullOrBlank() &&
            !event.dek?.mobileDeviceId.isNullOrBlank() &&
            !event.dek?.encDek.isNullOrBlank()
        if (!isComplete) {
            Log.d(
                REG_LOG_TAG,
                "Ignoring incomplete mobile registration SSE event. accountIdPresent=${!event.accountId.isNullOrBlank()} mobileDeviceIdPresent=${!event.mobileDeviceId.isNullOrBlank()} dekPresent=${event.dek != null} encDekPresent=${!event.dek?.encDek.isNullOrBlank()}",
            )
            return null
        }
        return event
    }

    private companion object {
        const val REG_LOG_TAG = "ZeroIntrusionRegistration"
        const val SSE_TIMEOUT_MILLIS = 3 * 60 * 1_000L
    }
}