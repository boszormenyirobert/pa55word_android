package com.zerointrusion.data.repository.registration

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.REG_LOG_TAG
import com.zerointrusion.data.repository.common.buildRegistrationUrl
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.repository.common.toDto
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.DeviceService
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.NamespaceService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.registration.RegistrationFormInput
import com.zerointrusion.domain.registration.RegistrationPayload
import com.zerointrusion.domain.registration.RegistrationResult
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AccountRegistrationHandler(
    private val registrationApi: RegistrationApi,
    private val keyStoreService: KeyStoreService,
    private val deviceService: DeviceService,
    private val namespaceService: NamespaceService,
    private val dekService: DekService,
    private val signatureService: SignatureService,
    private val stateStore: RegistrationStateStore,
    private val validator: RegistrationValidator,
    private val gson: Gson,
    private val fcmTokenSynchronizer: FcmTokenSynchronizer,
) {
    suspend fun register(input: RegistrationFormInput): RegistrationResult {
        val validation = validator.validateForm(input)
        if (!validation.isValid) {
            throw RegistrationFailure.Validation(
                validation.emailError ?: validation.phoneError ?: validation.namespaceNameError ?: validation.apiUrlError
                    ?: "Registration form is invalid.",
            )
        }

        return withContext(Dispatchers.IO) {
            val registrationUrl = buildRegistrationUrl(input.apiUrl)
            try {
                Log.d(REG_LOG_TAG, "REG start. url=$registrationUrl namespace=${input.namespaceName.trim()}")
                keyStoreService.ensureKeyPair()
                val publicKey = keyStoreService.getPublicKey()
                val device = deviceService.createDevice(publicKey)
                val namespace = namespaceService.createNamespace(input.namespaceName, input.apiUrl)
                val dek = dekService.createEncryptedDek(publicKey, device.deviceId)
                val nonce = newRequestNonce()
                val payload = RegistrationPayload(
                    namespace = namespace,
                    device = device,
                    dek = dek,
                    email = input.email.trim(),
                    phone = input.phone.trim(),
                )

                validator.validatePayload(payload)
                val request = payload.toDto(nonce)

                Log.d(
                    REG_LOG_TAG,
                    "REG payload ready. deviceId=${payload.device.deviceId}, namespaceUrl=${payload.namespace.url}, publicKeyLength=${payload.device.publicKey.length}, encDekLength=${payload.dek.encDek.length}",
                )

                val response = registrationApi.registerAccount(
                    url = registrationUrl,
                    request = request,
                )

                Log.d(REG_LOG_TAG, "REG response received. http=${response.code()} url=$registrationUrl")

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.charStream()?.use {
                        gson.fromJson(it, AccountErrorResponseDto::class.java)
                    }
                    Log.e(
                        REG_LOG_TAG,
                        "REG failed. http=${response.code()} url=$registrationUrl message=${errorBody?.message ?: "no server message"}",
                    )
                    throw RegistrationFailure.Server(
                        errorBody?.message ?: "Registration failed with HTTP ${response.code()} at $registrationUrl.",
                    )
                }

                val body = response.body()
                    ?: throw RegistrationFailure.Server("The backend returned an empty response from $registrationUrl.")
                if (!body.success || body.accountId.isNullOrBlank()) {
                    throw RegistrationFailure.Server("The backend did not return a valid account ID from $registrationUrl.")
                }

                Log.d(REG_LOG_TAG, "REG success. accountId=${body.accountId} deviceId=${payload.device.deviceId}")

                val state = RegistrationState(
                    accountId = body.accountId,
                    deviceId = payload.device.deviceId,
                    deviceName = payload.device.deviceName,
                    namespaceName = payload.namespace.name,
                    namespaceUrl = payload.namespace.url,
                    namespaceId = payload.namespace.namespaceId,
                    email = payload.email,
                    phone = payload.phone,
                    encDek = payload.dek.encDek,
                )
                stateStore.save(state)

                runCatching { fcmTokenSynchronizer.sync(state) }

                RegistrationResult(accountId = body.accountId)
            } catch (failure: RegistrationFailure) {
                Log.e(REG_LOG_TAG, "REG registration failure: ${failure.message}", failure)
                throw failure
            } catch (exception: UnknownServiceException) {
                Log.e(REG_LOG_TAG, "REG network failure: ${exception.message}", exception)
                if (exception.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
                    throw RegistrationFailure.Network(
                        "HTTP cleartext traffic is blocked for $registrationUrl. Use HTTPS or allow cleartext traffic for the app.",
                    )
                }
                throw RegistrationFailure.Network("The server connection to $registrationUrl could not be established.")
            } catch (_: ConnectException) {
                Log.e(REG_LOG_TAG, "REG connect failure. url=$registrationUrl")
                throw RegistrationFailure.Network("The host is reachable, but the backend is not accepting connections at $registrationUrl.")
            } catch (_: UnknownHostException) {
                Log.e(REG_LOG_TAG, "REG unknown host. url=$registrationUrl")
                throw RegistrationFailure.Network("The host name or IP address for $registrationUrl could not be resolved from the device.")
            } catch (_: SocketTimeoutException) {
                Log.e(REG_LOG_TAG, "REG timeout. url=$registrationUrl")
                throw RegistrationFailure.Network("The registration request to $registrationUrl timed out before the backend responded.")
            } catch (_: IOException) {
                Log.e(REG_LOG_TAG, "REG io failure. url=$registrationUrl")
                throw RegistrationFailure.Network("No internet connection or the server is unreachable at $registrationUrl.")
            } catch (exception: Exception) {
                Log.e(REG_LOG_TAG, "REG unexpected failure: ${exception.message}", exception)
                throw RegistrationFailure.Unexpected(exception.message ?: "Registration failed unexpectedly.")
            }
        }
    }
}