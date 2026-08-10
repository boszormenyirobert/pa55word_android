package com.zerointrusion.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.zerointrusion.core.AppConstants
import com.zerointrusion.data.firebase.FirebaseService
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.AccountRegistrationRequestDto
import com.zerointrusion.data.network.AuthenticationResponseRequestDto
import com.zerointrusion.data.network.DekDto
import com.zerointrusion.data.network.DeviceDto
import com.zerointrusion.data.network.NewDeviceRegistrationDto
import com.zerointrusion.data.network.NamespaceDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.network.RootDeviceDekResponseDto
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.DeviceService
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.NamespaceService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.RegistrationFailure
import com.zerointrusion.domain.RegistrationFormInput
import com.zerointrusion.domain.RegistrationPayload
import com.zerointrusion.domain.RegistrationRepository
import com.zerointrusion.domain.RegistrationResult
import com.zerointrusion.domain.RegistrationState
import com.zerointrusion.domain.RegistrationValidator
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class DefaultRegistrationRepository @Inject constructor(
    private val registrationApi: RegistrationApi,
    private val firebaseService: FirebaseService,
    private val keyStoreService: KeyStoreService,
    private val deviceService: DeviceService,
    private val namespaceService: NamespaceService,
    private val dekService: DekService,
    private val signatureService: SignatureService,
    private val stateStore: RegistrationStateStore,
    private val validator: RegistrationValidator,
    private val gson: Gson,
) : RegistrationRepository {
    private companion object {
        const val QR_LOG_TAG = "ZeroIntrusionQr"
        const val REG_LOG_TAG = "ZeroIntrusionRegistration"
    }

    private data class RawQrRegistrationPayload(
        val deviceId: String?,
        val publicKey: String?,
    )

    private data class QrRegistrationPayload(
        val deviceId: String,
        val publicKey: String,
    )

    private data class RootDeviceFetchSignaturePayload(
        val accountId: String,
        val deviceId: String,
    )

    override fun observeRegistrationState(): Flow<RegistrationState?> = stateStore.observeState()

    override suspend fun syncFcmToken(state: RegistrationState): RegistrationState = withContext(Dispatchers.IO) {
        val currentFcmToken = try {
            firebaseService.getFcmToken()
        } catch (exception: Exception) {
            throw RegistrationFailure.Unexpected(
                exception.message ?: "FCM token fetch failed: ${exception::class.java.simpleName}",
            )
        }

        try {
            firebaseService.registerFcmToken(
                accountId = state.accountId,
                deviceId = state.deviceId,
                fcmToken = currentFcmToken,
            )
        } catch (exception: Exception) {
            throw RegistrationFailure.Unexpected(
                exception.message ?: "FCM backend upload failed: ${exception::class.java.simpleName}",
            )
        }

        val syncedState = if (state.fcmToken == currentFcmToken) {
            state
        } else {
            state.copy(fcmToken = currentFcmToken)
        }

        if (syncedState != state) {
            stateStore.save(syncedState)
        }

        syncedState
    }

    override suspend fun register(input: RegistrationFormInput): RegistrationResult {
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
                val payload = RegistrationPayload(
                    namespace = namespace,
                    device = device,
                    dek = dek,
                    email = input.email.trim(),
                    phone = input.phone.trim(),
                )

                validator.validatePayload(payload)

                // Signature remains local-only in v1 for forward compatibility.
                signatureService.signPayload(payload)

                Log.d(
                    REG_LOG_TAG,
                    "REG payload ready. deviceId=${payload.device.deviceId}, namespaceUrl=${payload.namespace.url}, publicKeyLength=${payload.device.publicKey.length}, encDekLength=${payload.dek.encDek.length}",
                )

                val response = registrationApi.registerAccount(
                    url = registrationUrl,
                    request = payload.toDto(),
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
                    namespaceName = payload.namespace.name,
                    namespaceUrl = payload.namespace.url,
                    namespaceId = payload.namespace.namespaceId,
                    email = payload.email,
                    phone = payload.phone,
                    encDek = payload.dek.encDek,
                )
                stateStore.save(state)

                runCatching { syncFcmToken(state) }

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

    override suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        approved: Boolean,
    ) = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        val responseUrl = buildAuthenticationResponseUrl(registrationState.namespaceUrl)
        val requestPayload = AuthenticationResponseRequestDto(
            notificationId = notificationId,
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
            approved = approved,
        )
        val requestBodyJson = gson.toJson(requestPayload)
        val signature = signatureService.signJsonString(requestBodyJson)

        val response = registrationApi.sendAuthenticationResponse(
            url = responseUrl,
            signature = signature,
            request = requestPayload,
        )

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            throw RegistrationFailure.Server(
                errorBody ?: "Authentication response failed with HTTP ${response.code()} at $responseUrl.",
            )
        }
    }

    override suspend fun registerNewDeviceFromQr(qrPayload: String): NewDeviceRegistrationDto = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        Log.d(QR_LOG_TAG, "Starting QR registration. accountId=${registrationState.accountId}, rootDeviceId=${registrationState.deviceId}, namespaceUrl=${registrationState.namespaceUrl}")

        if (registrationState.accountId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid accountId.")
        }
        if (registrationState.deviceId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid root deviceId.")
        }

        val qrData = parseQrPayload(qrPayload)
        Log.d(QR_LOG_TAG, "QR payload parsed successfully. scannedDeviceId=${qrData.deviceId}, publicKeyLength=${qrData.publicKey.length}")
        val fetchUrl = buildRootDeviceFetchUrl(
            baseUrl = registrationState.namespaceUrl,
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
        )
        val fetchSignaturePayload = RootDeviceFetchSignaturePayload(
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
        )
        val fetchSignature = signatureService.signJsonString(gson.toJson(fetchSignaturePayload))
        Log.d(QR_LOG_TAG, "DEK fetch start. url=$fetchUrl")

        val rootDeviceResponse = try {
            registrationApi.fetchRootDeviceDek(fetchUrl, fetchSignature)
        } catch (exception: Exception) {
            Log.e(QR_LOG_TAG, "DEK fetch failed before HTTP response. url=$fetchUrl", exception)
            throw mapNetworkException(fetchUrl, exception)
        }

        if (!rootDeviceResponse.isSuccessful) {
            Log.e(QR_LOG_TAG, "DEK fetch returned HTTP ${rootDeviceResponse.code()} for $fetchUrl")
            throw serverFailure(rootDeviceResponse.errorBody()?.charStream(), rootDeviceResponse.code(), fetchUrl)
        }
        Log.d(QR_LOG_TAG, "DEK fetch success. http=${rootDeviceResponse.code()} url=$fetchUrl")

        val rootDeviceBody = rootDeviceResponse.body()
            ?.validateRootDeviceResponse(registrationState)
            ?: throw RegistrationFailure.Server("The backend returned an empty root-device response from $fetchUrl.")

        val rawDek = decryptRootDeviceDek(
            fetchedEncDek = rootDeviceBody.encDek!!,
            storedEncDek = registrationState.encDek,
        )
        Log.d(QR_LOG_TAG, "DEK decrypt confirmed in repository. rawDekSize=${rawDek.size}")
        val reEncryptedDek = dekService.encryptDek(rawDek, qrData.publicKey)
        Log.d(QR_LOG_TAG, "DEK re-encrypt confirmed in repository. scannedDeviceId=${qrData.deviceId}, encDekLength=${reEncryptedDek.length}")
        val dto = NewDeviceRegistrationDto(
            accountId = registrationState.accountId,
            device = DeviceDto(
                deviceId = registrationState.deviceId,
                publicKey = qrData.publicKey,
                deviceIdAdditional = qrData.deviceId,
            ),
            dek = DekDto(
                deviceId = qrData.deviceId,
                encDek = reEncryptedDek,
            ),
        )
        val submitSignature = signatureService.signJsonString(gson.toJson(dto))

        val submitUrl = buildRootDeviceRegistrationUrl(registrationState.namespaceUrl)
        Log.d(QR_LOG_TAG, "DEK submit start. url=$submitUrl scannedDeviceId=${qrData.deviceId}")
        val submitResponse = try {
            registrationApi.registerNewDevice(submitUrl, submitSignature, dto)
        } catch (exception: Exception) {
            Log.e(QR_LOG_TAG, "DEK submit failed before HTTP response. url=$submitUrl", exception)
            throw mapNetworkException(submitUrl, exception)
        }

        if (!submitResponse.isSuccessful) {
            Log.e(QR_LOG_TAG, "DEK submit returned HTTP ${submitResponse.code()} for $submitUrl")
            throw serverFailure(submitResponse.errorBody()?.charStream(), submitResponse.code(), submitUrl)
        }

        Log.d(QR_LOG_TAG, "DEK submit success. url=$submitUrl scannedDeviceId=${qrData.deviceId}")

        dto
    }

    private fun decryptRootDeviceDek(
        fetchedEncDek: String,
        storedEncDek: String?,
    ): ByteArray {
        return try {
            dekService.decryptDek(fetchedEncDek)
        } catch (failure: RegistrationFailure.Security) {
            val sanitizedStoredEncDek = storedEncDek?.trim().takeUnless { it.isNullOrEmpty() }
            if (sanitizedStoredEncDek == null) {
                throw RegistrationFailure.Security(
                    "Unable to decrypt the backend DEK with the device private key, and no local fallback DEK is stored on this device.",
                )
            }

            val usesDifferentCiphertext = sanitizedStoredEncDek != fetchedEncDek
            if (usesDifferentCiphertext) {
                Log.w(
                    QR_LOG_TAG,
                    "DEK decrypt fallback activated because the backend encDek differs from the locally stored registration encDek.",
                )
            } else {
                Log.w(
                    QR_LOG_TAG,
                    "DEK decrypt fallback retrying with the locally stored registration encDek because backend decrypt failed.",
                )
            }

            try {
                dekService.decryptDek(sanitizedStoredEncDek)
            } catch (localFailure: RegistrationFailure.Security) {
                throw RegistrationFailure.Security(
                    "Unable to decrypt either the backend DEK or the locally stored DEK with the current device private key. The keystore keypair on this device likely no longer matches the public key used during registration.",
                )
            }
        }
    }

    private fun RegistrationPayload.toDto(): AccountRegistrationRequestDto = AccountRegistrationRequestDto(
        namespace = NamespaceDto(
            namespaceId = namespace.namespaceId,
            name = namespace.name,
            url = namespace.url,
        ),
        device = DeviceDto(
            deviceId = device.deviceId,
            publicKey = device.publicKey,
        ),
        dek = DekDto(
            deviceId = dek.deviceId,
            encDek = dek.encDek,
        ),
        email = email,
        phone = phone,
    )

    private fun buildRegistrationUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.REGISTRATION_ENDPOINT_PATH

    private fun buildAuthenticationResponseUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.AUTHENTICATION_RESPONSE_ENDPOINT_PATH

    private fun buildRootDeviceFetchUrl(
        baseUrl: String,
        accountId: String,
        deviceId: String,
    ): String = baseUrl.trim().trimEnd('/') + AppConstants.ROOT_DEVICE_ENDPOINT_PATH + "/$accountId/$deviceId"

    private fun buildRootDeviceRegistrationUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.ROOT_DEVICE_ENDPOINT_PATH

    private fun parseQrPayload(qrPayload: String): QrRegistrationPayload {
        val trimmedPayload = qrPayload.trim()
        if (trimmedPayload.isBlank()) {
            throw RegistrationFailure.Validation("The scanned QR code did not contain a payload.")
        }

        val payload = try {
            gson.fromJson(trimmedPayload, RawQrRegistrationPayload::class.java)
        } catch (_: JsonSyntaxException) {
            throw RegistrationFailure.Validation("The scanned QR code is not valid JSON.")
        }

        if (payload == null) {
            throw RegistrationFailure.Validation("The scanned QR code is empty or unreadable.")
        }

        val deviceId = payload.deviceId?.trim().orEmpty()
        val publicKey = payload.publicKey?.trim().orEmpty()

        if (deviceId.isEmpty()) {
            throw RegistrationFailure.Validation("The scanned QR payload does not contain a deviceId.")
        }
        if (!deviceId.matches(Regex("^[A-Za-z0-9]{10}$"))) {
            throw RegistrationFailure.Validation("The scanned QR payload deviceId must be exactly 10 alphanumeric characters.")
        }
        if (publicKey.isEmpty()) {
            throw RegistrationFailure.Validation("The scanned QR payload does not contain a publicKey.")
        }
        if (publicKey.contains("BEGIN PUBLIC KEY") || publicKey.contains("END PUBLIC KEY")) {
            throw RegistrationFailure.Validation("The scanned QR payload publicKey must be Base64 DER data without PEM headers.")
        }

        return QrRegistrationPayload(deviceId = deviceId, publicKey = publicKey)
    }

    private fun RootDeviceDekResponseDto.validateRootDeviceResponse(
        registrationState: RegistrationState,
    ): RootDeviceDekResponseDto {
        if (accountId.isNullOrBlank() || accountId != registrationState.accountId) {
            throw RegistrationFailure.Server("The root-device response did not contain the expected accountId.")
        }
        if (deviceId.isNullOrBlank() || deviceId != registrationState.deviceId) {
            throw RegistrationFailure.Server("The root-device response did not contain the expected root deviceId.")
        }
        if (encDek.isNullOrBlank()) {
            throw RegistrationFailure.Server("The root-device response did not contain an encDek value.")
        }
        return this
    }

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