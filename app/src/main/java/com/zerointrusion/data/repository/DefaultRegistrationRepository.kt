package com.zerointrusion.data.repository

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.zerointrusion.core.AppConstants
import com.zerointrusion.data.firebase.FirebaseService
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.AccountRegistrationRequestDto
import com.zerointrusion.data.network.AuthenticationResponseRequestDto
import com.zerointrusion.data.network.DekDto
import com.zerointrusion.data.network.DeviceDto
import com.zerointrusion.data.network.NewDeviceRegistrationDto
import com.zerointrusion.data.network.NamespaceRegistrationRequestDto
import com.zerointrusion.data.network.NamespaceDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.network.RootDeviceDekResponseDto
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.DeviceService
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.NamespaceService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.NamespaceEntry
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
    private val namespaceStore: NamespaceStore,
    private val stateStore: RegistrationStateStore,
    private val validator: RegistrationValidator,
    private val gson: Gson,
) : RegistrationRepository {
    private companion object {
        const val QR_LOG_TAG = "ZeroIntrusionQr"
        const val REG_LOG_TAG = "ZeroIntrusionRegistration"
        const val ACCOUNT_LOG_TAG = "ZeroIntrusionAccount"
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

    private data class NamespaceResponseDto(
        val id: Long?,
        val accountId: String?,
        val namespaceId: String?,
        val namespace: String?,
        val url: String?,
    )

    override fun observeRegistrationState(): Flow<RegistrationState?> = stateStore.observeState()

    override fun observeNamespaces(): Flow<List<NamespaceEntry>> = namespaceStore.observeNamespaces()

    override suspend fun fetchNamespaces(): List<NamespaceEntry> = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        if (registrationState.accountId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid accountId.")
        }
        if (registrationState.deviceId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid deviceId.")
        }

        val requestUrl = buildNamespaceFetchUrl(
            baseUrl = registrationState.namespaceUrl,
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
        )
        val signaturePayload = RootDeviceFetchSignaturePayload(
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
        )
        val signature = signatureService.signJsonString(gson.toJson(signaturePayload))

        Log.d(ACCOUNT_LOG_TAG, "NAMESPACE fetch start. url=$requestUrl")
        val response = try {
            registrationApi.fetchNamespaces(
                url = requestUrl,
                signature = signature,
            )
        } catch (exception: Exception) {
            Log.e(ACCOUNT_LOG_TAG, "NAMESPACE fetch failed before HTTP response. url=$requestUrl", exception)
            throw mapNetworkException(requestUrl, exception)
        }

        if (!response.isSuccessful) {
            Log.e(ACCOUNT_LOG_TAG, "NAMESPACE fetch failed. http=${response.code()} url=$requestUrl")
            throw serverFailure(response.errorBody()?.charStream(), response.code(), requestUrl)
        }

        val body = response.body()?.string()?.trim().orEmpty()
        if (body.isEmpty()) {
            throw RegistrationFailure.Server("The backend returned an empty namespace response from $requestUrl.")
        }

        val namespaces = parseNamespaceEntries(
            rawBody = body,
            fallbackDeviceId = registrationState.deviceId,
        )
        namespaceStore.saveNamespaces(namespaces)
        Log.d(ACCOUNT_LOG_TAG, "NAMESPACE fetch success. count=${namespaces.size} url=$requestUrl")
        namespaces
    }

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

    override suspend fun saveNamespace(
        namespaceName: String,
        namespaceUrl: String,
    ): NamespaceEntry = withContext(Dispatchers.IO) {
        val validation = validator.validateNamespaceForm(namespaceName, namespaceUrl)
        if (!validation.isValid) {
            throw RegistrationFailure.Validation(
                validation.namespaceNameError ?: validation.namespaceUrlError ?: "Namespace form is invalid.",
            )
        }

        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        if (registrationState.accountId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid accountId.")
        }
        if (registrationState.deviceId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid deviceId.")
        }

        val namespace = namespaceService.createNamespace(namespaceName, namespaceUrl)
        val request = NamespaceRegistrationRequestDto(
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
            namespaceId = namespace.namespaceId,
            name = namespace.name,
            url = namespace.url,
        )
        val requestUrl = buildNamespaceRegistrationUrl(registrationState.namespaceUrl)
        val signature = signatureService.signJsonString(gson.toJson(request))

        Log.d(
            REG_LOG_TAG,
            "NAMESPACE save start. url=$requestUrl namespace=${request.name} accountId=${request.accountId}",
        )

        val response = try {
            registrationApi.registerNamespace(
                url = requestUrl,
                signature = signature,
                request = request,
            )
        } catch (exception: Exception) {
            Log.e(REG_LOG_TAG, "NAMESPACE save failed before HTTP response. url=$requestUrl", exception)
            throw mapNetworkException(requestUrl, exception)
        }

        if (!response.isSuccessful) {
            Log.e(REG_LOG_TAG, "NAMESPACE save failed. http=${response.code()} url=$requestUrl")
            throw serverFailure(response.errorBody()?.charStream(), response.code(), requestUrl)
        }

        val namespaceEntry = NamespaceEntry(
            namespaceName = request.name,
            namespaceId = request.namespaceId,
            namespaceUrl = request.url,
            accountId = request.accountId,
            deviceId = request.deviceId,
        )
        val currentNamespaces = namespaceStore.observeNamespaces().first()
        val updatedNamespaces = currentNamespaces
            .filterNot { it.namespaceId == namespaceEntry.namespaceId }
            .plus(namespaceEntry)
            .sortedBy { it.namespaceName.lowercase() }
        namespaceStore.saveNamespaces(updatedNamespaces)
        Log.d(REG_LOG_TAG, "NAMESPACE save success. namespaceId=${namespaceEntry.namespaceId}")

        val encDek = registrationState.encDek
        if (encDek.isNullOrBlank()) {
            Log.w(REG_LOG_TAG, "NAMESPACE account registration skipped: no encDek in registration state.")
        } else {
            val newNamespaceRegistrationUrl = buildRegistrationUrl(namespaceEntry.namespaceUrl)
            val publicKey = Base64.encodeToString(keyStoreService.getPublicKey().encoded, Base64.NO_WRAP)
            val newNamespaceRequest = AccountRegistrationRequestDto(
                namespace = NamespaceDto(
                    namespaceId = namespaceEntry.namespaceId,
                    name = namespaceEntry.namespaceName,
                    url = namespaceEntry.namespaceUrl,
                ),
                device = DeviceDto(
                    deviceId = registrationState.deviceId,
                    publicKey = publicKey,
                ),
                dek = DekDto(
                    deviceId = registrationState.deviceId,
                    encDek = encDek,
                ),
                email = registrationState.email,
                phone = registrationState.phone,
            )
            Log.d(
                REG_LOG_TAG,
                "NAMESPACE account registration start. url=$newNamespaceRegistrationUrl namespaceId=${namespaceEntry.namespaceId}",
            )
            val newNamespaceResponse = try {
                registrationApi.registerAccount(
                    url = newNamespaceRegistrationUrl,
                    request = newNamespaceRequest,
                )
            } catch (exception: Exception) {
                Log.e(REG_LOG_TAG, "NAMESPACE account registration failed before HTTP response. url=$newNamespaceRegistrationUrl", exception)
                throw mapNetworkException(newNamespaceRegistrationUrl, exception)
            }
            if (!newNamespaceResponse.isSuccessful) {
                Log.e(REG_LOG_TAG, "NAMESPACE account registration failed. http=${newNamespaceResponse.code()} url=$newNamespaceRegistrationUrl")
                throw serverFailure(newNamespaceResponse.errorBody()?.charStream(), newNamespaceResponse.code(), newNamespaceRegistrationUrl)
            }

            val newNamespaceBody = newNamespaceResponse.body()
                ?: throw RegistrationFailure.Server("The backend returned an empty response from $newNamespaceRegistrationUrl.")
            if (!newNamespaceBody.success || newNamespaceBody.accountId.isNullOrBlank()) {
                throw RegistrationFailure.Server("The backend did not return a valid account ID from $newNamespaceRegistrationUrl.")
            }

            val updatedEntry = namespaceEntry.copy(accountId = newNamespaceBody.accountId)
            val refreshedNamespaces = namespaceStore.observeNamespaces().first()
                .filterNot { it.namespaceId == updatedEntry.namespaceId }
                .plus(updatedEntry)
                .sortedBy { it.namespaceName.lowercase() }
            namespaceStore.saveNamespaces(refreshedNamespaces)
            Log.d(REG_LOG_TAG, "NAMESPACE account registration success. namespaceId=${updatedEntry.namespaceId} accountId=${newNamespaceBody.accountId}")

            val syncUrl = buildNamespaceRegistrationUrl(updatedEntry.namespaceUrl)
            refreshedNamespaces.forEach { ns ->
                val syncRequest = NamespaceRegistrationRequestDto(
                    accountId = newNamespaceBody.accountId,
                    deviceId = registrationState.deviceId,
                    namespaceId = ns.namespaceId,
                    name = ns.namespaceName,
                    url = ns.namespaceUrl,
                )
                val syncSignature = signatureService.signJsonString(gson.toJson(syncRequest))
                Log.d(REG_LOG_TAG, "NAMESPACE sync start. targetUrl=$syncUrl namespaceId=${ns.namespaceId}")
                val syncResponse = try {
                    registrationApi.registerNamespace(
                        url = syncUrl,
                        signature = syncSignature,
                        request = syncRequest,
                    )
                } catch (exception: Exception) {
                    Log.e(REG_LOG_TAG, "NAMESPACE sync failed before HTTP response. targetUrl=$syncUrl namespaceId=${ns.namespaceId}", exception)
                    throw mapNetworkException(syncUrl, exception)
                }
                if (!syncResponse.isSuccessful) {
                    Log.e(REG_LOG_TAG, "NAMESPACE sync failed. http=${syncResponse.code()} targetUrl=$syncUrl namespaceId=${ns.namespaceId}")
                    throw serverFailure(syncResponse.errorBody()?.charStream(), syncResponse.code(), syncUrl)
                }
                Log.d(REG_LOG_TAG, "NAMESPACE sync success. targetUrl=$syncUrl namespaceId=${ns.namespaceId}")
            }

            return@withContext updatedEntry
        }

        namespaceEntry
    }

    override suspend fun setActiveNamespace(namespace: NamespaceEntry): RegistrationState = withContext(Dispatchers.IO) {
        val currentState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        if (namespace.accountId.isBlank() || namespace.deviceId.isBlank()) {
            throw RegistrationFailure.Validation("The selected namespace does not contain a valid accountId or deviceId.")
        }

        val updatedState = currentState.copy(
            accountId = namespace.accountId,
            deviceId = namespace.deviceId,
            namespaceName = namespace.namespaceName,
            namespaceUrl = namespace.namespaceUrl,
            namespaceId = namespace.namespaceId,
        )
        stateStore.save(updatedState)
        Log.d(REG_LOG_TAG, "NAMESPACE active changed. namespaceId=${namespace.namespaceId} url=${namespace.namespaceUrl}")
        updatedState
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
            fetchedEncDek = rootDeviceBody.dek!!.encDek!!,
            storedEncDek = registrationState.encDek,
        )
        Log.d(QR_LOG_TAG, "DEK decrypt confirmed in repository. rawDekSize=${rawDek.size}")
        val reEncryptedDek = dekService.encryptDek(rawDek, qrData.publicKey)
        Log.d(QR_LOG_TAG, "DEK re-encrypt confirmed in repository. scannedDeviceId=${qrData.deviceId}, encDekLength=${reEncryptedDek.length}")
        val dto = NewDeviceRegistrationDto(
            accountId = rootDeviceBody.dek.accountId!!,
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

    override suspend fun fetchConnectedDevices(): String = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        if (registrationState.accountId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid accountId.")
        }
        if (registrationState.deviceId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid deviceId.")
        }

        val requestUrl = buildConnectedDevicesUrl(
            baseUrl = registrationState.namespaceUrl,
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
        )
        val signaturePayload = RootDeviceFetchSignaturePayload(
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
        )
        val signaturePayloadJson = gson.toJson(signaturePayload)
        val signature = signatureService.signJsonString(signaturePayloadJson)

        Log.d(ACCOUNT_LOG_TAG, "ACCOUNT details fetch start. url=$requestUrl")
        val response = try {
            registrationApi.getAccountDetails(
                url = requestUrl,
                signature = signature,
            )
        } catch (exception: Exception) {
            Log.e(ACCOUNT_LOG_TAG, "ACCOUNT details fetch failed before HTTP response. url=$requestUrl", exception)
            throw mapNetworkException(requestUrl, exception)
        }

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            Log.e(ACCOUNT_LOG_TAG, "ACCOUNT details fetch failed. http=${response.code()} url=$requestUrl")
            throw RegistrationFailure.Server(
                errorBody ?: "Request failed with HTTP ${response.code()} at $requestUrl.",
            )
        }

        val body = response.body()?.string()?.trim().orEmpty()
        if (body.isEmpty()) {
            throw RegistrationFailure.Server("The backend returned an empty account response from $requestUrl.")
        }

        runCatching {
            gson.fromJson(body, Any::class.java)
        }.onSuccess { parsed ->
            return@withContext gson.toJson(parsed)
        }

        body
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

    private fun buildNamespaceFetchUrl(
        baseUrl: String,
        accountId: String,
        deviceId: String,
    ): String = baseUrl.trim().trimEnd('/') +
        AppConstants.NAMESPACE_ENDPOINT_PATH + "/" + accountId.trim() + "/" + deviceId.trim()

    private fun buildAuthenticationResponseUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.AUTHENTICATION_RESPONSE_ENDPOINT_PATH

    private fun buildNamespaceRegistrationUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.NAMESPACE_ENDPOINT_PATH

    private fun buildRootDeviceFetchUrl(
        baseUrl: String,
        accountId: String,
        deviceId: String,
    ): String = baseUrl.trim().trimEnd('/') + AppConstants.ROOT_DEVICE_ENDPOINT_PATH + "/$accountId/$deviceId"

    private fun buildRootDeviceRegistrationUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.ROOT_DEVICE_ENDPOINT_PATH

    private fun buildConnectedDevicesUrl(
        baseUrl: String,
        accountId: String,
        deviceId: String,
    ): String = baseUrl.trim().trimEnd('/') +
        AppConstants.REGISTRATION_ENDPOINT_PATH + "/" + accountId.trim() + "/" + deviceId.trim()

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

    private fun parseNamespaceEntries(
        rawBody: String,
        fallbackDeviceId: String,
    ): List<NamespaceEntry> {
        val element = try {
            gson.fromJson(rawBody, JsonElement::class.java)
        } catch (_: JsonSyntaxException) {
            throw RegistrationFailure.Server("The backend returned invalid JSON for the namespace response.")
        }

        val payloadElements = when {
            element.isJsonArray -> element.asJsonArray.toList()
            element.isJsonObject -> element.asJsonObject.extractNamespaceElements()
            else -> throw RegistrationFailure.Server("The backend returned an unsupported namespace response format.")
        }

        return payloadElements.map { entryElement ->
            val dto = runCatching { gson.fromJson(entryElement, NamespaceResponseDto::class.java) }
                .getOrElse {
                    throw RegistrationFailure.Server("The backend returned a namespace entry with an invalid schema.")
                }

            val accountId = dto.accountId?.trim().orEmpty()
            val namespaceId = dto.namespaceId?.trim().orEmpty()
            val namespaceName = dto.namespace?.trim().orEmpty()
            val namespaceUrl = dto.url?.trim().orEmpty()

            if (accountId.isEmpty() || namespaceId.isEmpty() || namespaceName.isEmpty() || namespaceUrl.isEmpty()) {
                throw RegistrationFailure.Server("The backend returned a namespace entry with missing required fields.")
            }

            NamespaceEntry(
                namespaceName = namespaceName,
                namespaceId = namespaceId,
                namespaceUrl = namespaceUrl,
                accountId = accountId,
                deviceId = fallbackDeviceId,
            )
        }.distinctBy { it.namespaceId }
            .sortedBy { it.namespaceName.lowercase() }
    }

    private fun JsonObject.extractNamespaceElements(): List<JsonElement> {
        val arrayCandidate = get("namespaces")
        return when {
            arrayCandidate?.isJsonArray == true -> arrayCandidate.asJsonArray.toList()
            has("accountId") && has("namespaceId") && has("namespace") && has("url") -> listOf(this)
            else -> throw RegistrationFailure.Server("The backend returned an unsupported namespace response format.")
        }
    }

    private fun RootDeviceDekResponseDto.validateRootDeviceResponse(
        registrationState: RegistrationState,
    ): RootDeviceDekResponseDto {
        val dekPayload = dek
            ?: throw RegistrationFailure.Server("The root-device response did not contain a dek object.")

        if (dekPayload.accountId.isNullOrBlank() || dekPayload.accountId != registrationState.accountId) {
            throw RegistrationFailure.Server("The root-device response did not contain the expected accountId.")
        }
        if (dekPayload.deviceId.isNullOrBlank() || dekPayload.deviceId != registrationState.deviceId) {
            throw RegistrationFailure.Server("The root-device response did not contain the expected root deviceId.")
        }
        if (dekPayload.encDek.isNullOrBlank()) {
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