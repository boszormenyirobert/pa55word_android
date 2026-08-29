package com.zerointrusion.data.repository.qr

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.core.AppConstants
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.NewDeviceRegistrationDto
import com.zerointrusion.data.network.NewDeviceRegistrationDekDto
import com.zerointrusion.data.network.NewDeviceRegistrationDeviceDto
import com.zerointrusion.data.network.NamespaceRegistrationRequestDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.qr.DeviceRegistrationFromQrFlow
import com.zerointrusion.data.qr.NamespaceRegistrationFromQrFlow
import com.zerointrusion.data.qr.QrPayloadParser
import com.zerointrusion.data.repository.common.QR_LOG_TAG
import com.zerointrusion.data.repository.common.buildRootDeviceRegistrationUrl
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.repository.common.serverFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.data.repository.registration.FcmTokenSynchronizer
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.qr.DeviceRegistrationQrPayload
import com.zerointrusion.domain.qr.NamespaceQrPayload
import com.zerointrusion.domain.qr.QrNamespaceRegistrationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class QrNamespaceRegistrationHandler(
    private val registrationApi: RegistrationApi,
    private val stateStore: RegistrationStateStore,
    private val dekService: DekService,
    private val signatureService: SignatureService,
    private val gson: Gson,
    private val fcmTokenSynchronizer: FcmTokenSynchronizer,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val qrPayloadParser: QrPayloadParser,
    private val deviceRegistrationFromQrFlow: DeviceRegistrationFromQrFlow,
    private val namespaceRegistrationFromQrFlow: NamespaceRegistrationFromQrFlow,
) {
    suspend fun registerNamespaceFromQr(
        qrPayload: String,
        namespaceLinkDeviceId: String? = null,
    ): QrNamespaceRegistrationResult = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        if (registrationState.accountId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid accountId.")
        }
        if (registrationState.deviceId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid root deviceId.")
        }
        val storedEncDek = registrationState.encDek?.trim().takeUnless { it.isNullOrEmpty() }
            ?: throw RegistrationFailure.Validation("The stored registration state does not contain an encrypted DEK.")

        val qrData = qrPayloadParser.parse(qrPayload)
        val preparation = when (qrData) {
            is DeviceRegistrationQrPayload -> {
                Log.d(
                    QR_LOG_TAG,
                    "QR device registration selected. currentAccountId=${registrationState.accountId} namespaceUrl=${registrationState.namespaceUrl} namespaceId=${registrationState.namespaceId} desktopDeviceId=${qrData.desktopDeviceId}",
                )
                deviceRegistrationFromQrFlow.prepare(registrationState, qrData)
            }

            is NamespaceQrPayload -> namespaceRegistrationFromQrFlow.prepare(
                registrationState = registrationState,
                payload = qrData,
                storedEncDek = storedEncDek,
            )
        }

        if (qrData is NamespaceQrPayload) {
            val initialNamespaceLinkRequest = buildInitialNamespaceLinkRequest(
                currentAccountId = registrationState.accountId,
                scannedNamespaceAccountId = preparation.targetNamespaceAccountId,
                currentMobileDeviceId = registrationState.deviceId,
                selectedDesktopDeviceId = namespaceLinkDeviceId?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: throw RegistrationFailure.Validation("Select a connected device before linking the scanned namespace."),
                qrData = qrData,
            )
            registerNamespaceLink(
                requestUrl = registrationState.namespaceUrl.trim().trimEnd('/') + AppConstants.NAMESPACE_ENDPOINT_PATH,
                request = initialNamespaceLinkRequest,
                logLabel = "Namespace link submit",
            )
        }

        val rawDek = decryptRootDeviceDek(
            dekService = dekService,
            fetchedEncDek = storedEncDek,
            storedEncDek = storedEncDek,
        )
        val reEncryptedDek = dekService.encryptDek(rawDek, preparation.scannedPublicKey)
        val nonce = newRequestNonce()
        val desktopRegistrationDto = NewDeviceRegistrationDto(
            accountId = preparation.targetNamespaceAccountId,
            device = NewDeviceRegistrationDeviceDto(
                mobileDeviceId = registrationState.deviceId,
                publicKey = preparation.scannedPublicKey,
                deviceName = preparation.scannedDeviceName,
                desktopDeviceId = preparation.scannedDesktopDeviceId,
            ),
            dek = NewDeviceRegistrationDekDto(
                desktopDeviceId = preparation.scannedDesktopDeviceId,
                encDek = reEncryptedDek,
            ),
            nonce = nonce,
        )
        val requestBodyJson = gson.toJson(desktopRegistrationDto)
        val submitSignature = signatureService.signJsonString(requestBodyJson)
        val submitUrl = buildRootDeviceRegistrationUrl(preparation.namespaceEntry.namespaceUrl)
        Log.d(QR_LOG_TAG, "HTTP POST $submitUrl payload=$requestBodyJson")
        Log.d(QR_LOG_TAG, "Desktop registration submit start. url=$submitUrl scannedDesktopDeviceId=${preparation.scannedDesktopDeviceId}")
        val submitResponse = try {
            registrationApi.registerNewDevice(submitUrl, submitSignature, desktopRegistrationDto)
        } catch (exception: Exception) {
            Log.e(QR_LOG_TAG, "Desktop registration submit failed before HTTP response. url=$submitUrl", exception)
            throw mapNetworkException(submitUrl, exception)
        }

        if (!submitResponse.isSuccessful) {
            Log.e(QR_LOG_TAG, "Desktop registration submit returned HTTP ${submitResponse.code()} for $submitUrl")
            val backendError = submitResponse.errorBody()?.charStream()?.use {
                runCatching { gson.fromJson(it, AccountErrorResponseDto::class.java) }.getOrNull()
            }
            throw mapDesktopRegistrationFailure(
                statusCode = submitResponse.code(),
                url = submitUrl,
                backendError = backendError,
            )
        }

        Log.d(QR_LOG_TAG, "Desktop registration submit success. url=$submitUrl scannedDesktopDeviceId=${preparation.scannedDesktopDeviceId}")

        if (qrData is NamespaceQrPayload) {
            val reverseNamespaceLinkRequest = buildReverseNamespaceLinkRequest(
                scannedNamespaceAccountId = preparation.targetNamespaceAccountId,
                currentMobileDeviceId = registrationState.deviceId,
                scannedDesktopDeviceId = qrData.desktopDeviceId.trim(),
                qrData = qrData,
            )
            registerNamespaceLink(
                requestUrl = preparation.namespaceEntry.namespaceUrl.trim().trimEnd('/') + AppConstants.NAMESPACE_ENDPOINT_PATH,
                request = reverseNamespaceLinkRequest,
                logLabel = "Namespace reverse link submit",
            )
        }

        fcmTokenSynchronizer.syncNamespace(
            namespaceEntry = preparation.namespaceEntry,
            registrationState = registrationState,
        )
        runCatching { connectedDevicesRepository.fetchConnectedDevices() }
            .onFailure { exception ->
                Log.w(
                    QR_LOG_TAG,
                    "QR registration refresh failed after successful server registration. namespaceUrl=${preparation.namespaceEntry.namespaceUrl}",
                    exception,
                )
            }

        QrNamespaceRegistrationResult(
            namespaceName = preparation.namespaceEntry.namespaceName,
            desktopDeviceId = preparation.scannedDesktopDeviceId,
        )
    }

    private suspend fun registerNamespaceLink(
        requestUrl: String,
        request: NamespaceRegistrationRequestDto,
        logLabel: String,
    ) {
        val requestJson = gson.toJson(request)
        val signature = signatureService.signJsonString(requestJson)

        Log.d(
            QR_LOG_TAG,
            "$logLabel start. url=$requestUrl accountId=${request.accountId} foreignAccountId=${request.foreignAccountId} mobileDeviceId=${request.mobileDeviceId} desktopDeviceId=${request.desktopDeviceId} namespaceId=${request.namespaceId}",
        )
        Log.d(QR_LOG_TAG, "HTTP POST $requestUrl payload=$requestJson")
        Log.d(QR_LOG_TAG, "$logLabel signature: $signature")
        val response = try {
            registrationApi.registerNamespace(
                url = requestUrl,
                signature = signature,
                request = request,
            )
        } catch (exception: Exception) {
            Log.e(QR_LOG_TAG, "$logLabel failed before HTTP response. url=$requestUrl", exception)
            throw mapNetworkException(requestUrl, exception)
        }

        if (!response.isSuccessful) {
            Log.e(QR_LOG_TAG, "$logLabel returned HTTP ${response.code()} for $requestUrl")
            throw serverFailure(gson, response.errorBody()?.charStream(), response.code(), requestUrl)
        }
    }

    private fun buildInitialNamespaceLinkRequest(
        currentAccountId: String,
        scannedNamespaceAccountId: String,
        currentMobileDeviceId: String,
        selectedDesktopDeviceId: String,
        qrData: NamespaceQrPayload,
    ): NamespaceRegistrationRequestDto = NamespaceRegistrationRequestDto(
        accountId = currentAccountId,
        foreignAccountId = scannedNamespaceAccountId,
        mobileDeviceId = currentMobileDeviceId,
        desktopDeviceId = selectedDesktopDeviceId,
        namespaceId = qrData.namespaceId,
        name = qrData.namespaceName,
        url = qrData.namespaceUrl.trim().trimEnd('/'),
        nonce = newRequestNonce(),
    )

    private fun buildReverseNamespaceLinkRequest(
        scannedNamespaceAccountId: String,
        currentMobileDeviceId: String,
        scannedDesktopDeviceId: String,
        qrData: NamespaceQrPayload,
    ): NamespaceRegistrationRequestDto = NamespaceRegistrationRequestDto(
        accountId = scannedNamespaceAccountId,
        foreignAccountId = "",
        mobileDeviceId = currentMobileDeviceId,
        desktopDeviceId = scannedDesktopDeviceId,
        namespaceId = qrData.namespaceId,
        name = qrData.namespaceName,
        url = qrData.namespaceUrl.trim().trimEnd('/'),
        nonce = newRequestNonce(),
    )

    private fun decryptRootDeviceDek(
        dekService: DekService,
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

    private fun mapDesktopRegistrationFailure(
        statusCode: Int,
        url: String,
        backendError: AccountErrorResponseDto?,
    ): RegistrationFailure {
        val backendMessage = backendError?.message?.trim().orEmpty()
        val backendCode = backendError?.internalCode?.trim().orEmpty()
        val duplicateConflict = statusCode == 409 ||
            backendMessage.contains("already", ignoreCase = true) ||
            backendMessage.contains("exists", ignoreCase = true) ||
            backendMessage.contains("registered", ignoreCase = true) ||
            backendMessage.contains("duplicate", ignoreCase = true) ||
            backendMessage.contains("conflict", ignoreCase = true) ||
            backendCode.contains("already", ignoreCase = true) ||
            backendCode.contains("exists", ignoreCase = true) ||
            backendCode.contains("registered", ignoreCase = true) ||
            backendCode.contains("duplicate", ignoreCase = true) ||
            backendCode.contains("conflict", ignoreCase = true)

        if (duplicateConflict) {
            return RegistrationFailure.Validation(
                "This desktop device is already registered on the server for the scanned namespace.",
            )
        }

        return RegistrationFailure.Server(
            backendMessage.ifBlank { "Request failed with HTTP $statusCode at $url." },
        )
    }
}