package com.zerointrusion.data.qr

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.core.AppConstants
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.AccountRegistrationRequestDto
import com.zerointrusion.data.network.DekDto
import com.zerointrusion.data.network.DeviceDto
import com.zerointrusion.data.network.NamespaceDto
import com.zerointrusion.data.network.NamespaceRegistrationRequestDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.qr.NamespaceQrPayload
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import kotlinx.coroutines.flow.first

internal class NamespaceRegistrationFromQrFlow(
    private val registrationApi: RegistrationApi,
    private val keyStoreService: KeyStoreService,
    private val namespaceStore: NamespaceStore,
    private val signatureService: SignatureService,
    private val gson: Gson,
    private val validator: RegistrationValidator,
) {
    private companion object {
        const val QR_LOG_TAG = "ZeroIntrusionQr"
        const val REG_LOG_TAG = "ZeroIntrusionRegistration"
    }

    suspend fun prepare(
        registrationState: RegistrationState,
        payload: NamespaceQrPayload,
        storedEncDek: String,
    ): QrRegistrationPreparation {
        val namespaceValidation = validator.validateNamespaceForm(payload.namespaceName, payload.namespaceUrl)
        if (!namespaceValidation.isValid) {
            throw RegistrationFailure.Validation(
                namespaceValidation.namespaceNameError ?: namespaceValidation.namespaceUrlError ?: "QR namespace payload is invalid.",
            )
        }

        val normalizedNamespaceName = payload.namespaceName.trim()
        val normalizedNamespaceUrl = payload.namespaceUrl.trim().trimEnd('/')
        val namespaceId = payload.namespaceId.trim()

        val encodedPhonePublicKey = Base64.encodeToString(keyStoreService.getPublicKey().encoded, Base64.NO_WRAP)
        val phoneRegistrationUrl = buildRegistrationUrl(normalizedNamespaceUrl)
        val phoneRegistrationRequest = AccountRegistrationRequestDto(
            namespace = NamespaceDto(
                namespaceId = namespaceId,
                name = normalizedNamespaceName,
                url = normalizedNamespaceUrl,
            ),
            device = DeviceDto(
                deviceId = registrationState.deviceId,
                publicKey = encodedPhonePublicKey,
                deviceName = registrationState.deviceName,
            ),
            dek = DekDto(
                deviceId = registrationState.deviceId,
                encDek = storedEncDek,
            ),
            email = registrationState.email,
            phone = registrationState.phone,
        )

        val phoneRegistrationResponse = try {
            Log.d(QR_LOG_TAG, "QR phone registration start. url=$phoneRegistrationUrl namespaceId=$namespaceId")
            registrationApi.registerAccount(
                url = phoneRegistrationUrl,
                request = phoneRegistrationRequest,
            )
        } catch (exception: Exception) {
            Log.e(QR_LOG_TAG, "QR phone registration failed before HTTP response. url=$phoneRegistrationUrl", exception)
            throw mapNetworkException(phoneRegistrationUrl, exception)
        }

        if (!phoneRegistrationResponse.isSuccessful) {
            Log.e(QR_LOG_TAG, "QR phone registration failed. http=${phoneRegistrationResponse.code()} url=$phoneRegistrationUrl")
            throw serverFailure(phoneRegistrationResponse.errorBody()?.charStream(), phoneRegistrationResponse.code(), phoneRegistrationUrl)
        }

        val phoneRegistrationBody = phoneRegistrationResponse.body()
            ?: throw RegistrationFailure.Server("The backend returned an empty response from $phoneRegistrationUrl.")
        if (!phoneRegistrationBody.success || phoneRegistrationBody.accountId.isNullOrBlank()) {
            throw RegistrationFailure.Server("The backend did not return a valid account ID from $phoneRegistrationUrl.")
        }

        val namespaceEntry = NamespaceEntry(
            namespaceName = normalizedNamespaceName,
            namespaceId = namespaceId,
            namespaceUrl = normalizedNamespaceUrl,
            accountId = phoneRegistrationBody.accountId,
            deviceId = payload.deviceId,
            deviceName = registrationState.deviceName,
            publicKey = payload.publicKey,
        )

        saveNamespace(namespaceEntry)

        return QrRegistrationPreparation(
            namespaceEntry = namespaceEntry,
            scannedDeviceId = payload.deviceId,
            scannedPublicKey = payload.publicKey,
        )
    }

    private suspend fun saveNamespace(
        namespaceEntry: NamespaceEntry,
    ): List<NamespaceEntry> {
        val existingNamespaces = namespaceStore.observeNamespaces().first()
        val mergedNamespaces = mergeNamespaces(
            existingNamespaces = existingNamespaces,
            newNamespace = namespaceEntry,
        )
        namespaceStore.saveNamespaces(mergedNamespaces)
        return mergedNamespaces
    }

    private suspend fun registerNamespaceLink(
        requestUrl: String,
        request: NamespaceRegistrationRequestDto,
        logPrefix: String,
    ) {
        val signature = signatureService.signJsonString(gson.toJson(request))
        Log.d(REG_LOG_TAG, "$logPrefix start. url=$requestUrl namespaceId=${request.namespaceId}")
        val response = try {
            registrationApi.registerNamespace(
                url = requestUrl,
                signature = signature,
                request = request,
            )
        } catch (exception: Exception) {
            Log.e(REG_LOG_TAG, "$logPrefix failed before HTTP response. url=$requestUrl namespaceId=${request.namespaceId}", exception)
            throw mapNetworkException(requestUrl, exception)
        }

        if (!response.isSuccessful) {
            Log.e(REG_LOG_TAG, "$logPrefix failed. http=${response.code()} url=$requestUrl namespaceId=${request.namespaceId}")
            throw serverFailure(response.errorBody()?.charStream(), response.code(), requestUrl)
        }
        Log.d(REG_LOG_TAG, "$logPrefix success. url=$requestUrl namespaceId=${request.namespaceId}")
    }

    private fun buildRegistrationUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.REGISTRATION_ENDPOINT_PATH

    private fun buildNamespaceRegistrationUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.NAMESPACE_ENDPOINT_PATH

    private fun serverFailure(
        errorBodyReader: java.io.Reader?,
        statusCode: Int,
        url: String,
    ): RegistrationFailure.Server {
        val errorBody = errorBodyReader?.use {
            runCatching { gson.fromJson(it, AccountErrorResponseDto::class.java) }.getOrNull()
        }
        return RegistrationFailure.Server(
            errorBody?.message ?: "Request failed with HTTP $statusCode at $url.",
        )
    }

    private fun mapNetworkException(url: String, exception: Exception): RegistrationFailure = when (exception) {
        is RegistrationFailure -> exception
        is UnknownServiceException -> {
            if (exception.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
                RegistrationFailure.Network(
                    "HTTP cleartext traffic is blocked for $url. Use HTTPS or allow cleartext traffic for the app.",
                )
            } else {
                RegistrationFailure.Network("The server connection to $url could not be established.")
            }
        }
        is ConnectException -> RegistrationFailure.Network("The host is reachable, but the backend is not accepting connections at $url.")
        is UnknownHostException -> RegistrationFailure.Network("The host name or IP address for $url could not be resolved from the device.")
        is SocketTimeoutException -> RegistrationFailure.Network("The request to $url timed out before the backend responded.")
        is IOException -> RegistrationFailure.Network("No internet connection or the server is unreachable at $url.")
        else -> RegistrationFailure.Unexpected(exception.message ?: "Request failed unexpectedly.")
    }
}

internal fun mergeNamespaces(
    existingNamespaces: List<NamespaceEntry>,
    newNamespace: NamespaceEntry,
): List<NamespaceEntry> = existingNamespaces
    .plus(newNamespace)
    .associateBy { namespace ->
        namespace.namespaceId.trim().lowercase() +
            "|" + namespace.namespaceUrl.trim().trimEnd('/').lowercase() +
            "|" + namespace.deviceId.trim().lowercase()
    }
    .values
    .sortedBy { it.namespaceName.lowercase() }