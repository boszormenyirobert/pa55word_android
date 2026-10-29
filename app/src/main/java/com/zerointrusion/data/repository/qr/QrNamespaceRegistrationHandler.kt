package com.zerointrusion.data.repository.qr

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.NewDeviceRegistrationDekDto
import com.zerointrusion.data.network.NewDeviceRegistrationDeviceDto
import com.zerointrusion.data.network.NewDeviceRegistrationDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.qr.AdditionalMobileNamespacesFromQrFlow
import com.zerointrusion.data.qr.DeviceRegistrationFromQrFlow
import com.zerointrusion.data.qr.MobileDeviceAdditionFromQrFlow
import com.zerointrusion.data.qr.NamespaceRegistrationFromQrFlow
import com.zerointrusion.data.qr.QrPayloadParser
import com.zerointrusion.data.repository.common.QR_LOG_TAG
import com.zerointrusion.data.repository.common.buildRootDeviceRegistrationUrl
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.repository.registration.FcmTokenSynchronizer
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.LocalConnectedDeviceEntry
import com.zerointrusion.data.storage.LocalConnectedNamespace
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.qr.AdditionMobileDeviceQrPayload
import com.zerointrusion.domain.qr.AdditionalMobileNamespacesQrPayload
import com.zerointrusion.domain.qr.DeviceRegistrationQrPayload
import com.zerointrusion.domain.qr.NamespaceQrPayload
import com.zerointrusion.domain.qr.NamespaceSyncQrPayload
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
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val qrPayloadParser: QrPayloadParser,
    private val deviceRegistrationFromQrFlow: DeviceRegistrationFromQrFlow,
    private val namespaceRegistrationFromQrFlow: NamespaceRegistrationFromQrFlow,
    private val mobileDeviceAdditionFromQrFlow: MobileDeviceAdditionFromQrFlow,
    private val additionalMobileNamespacesFromQrFlow: AdditionalMobileNamespacesFromQrFlow,
) {
    suspend fun registerNamespaceFromQr(
        qrPayload: String,
        rootDeviceAccountId: String? = null,
    ): QrNamespaceRegistrationResult =
        withContext(Dispatchers.IO) {
            val registrationState =
                stateStore.observeState().first()
                    ?: throw RegistrationFailure.Validation(
                        "No registered account is stored on this device."
                    )

            if (registrationState.accountId.isBlank()) {
                throw RegistrationFailure.Validation(
                    "The stored registration state does not contain a valid accountId."
                )
            }
            if (registrationState.deviceId.isBlank()) {
                throw RegistrationFailure.Validation(
                    "The stored registration state does not contain a valid root deviceId."
                )
            }
            val storedEncDek =
                registrationState.encDek?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: throw RegistrationFailure.Validation(
                        "The stored registration state does not contain an encrypted DEK."
                    )

            val qrData = qrPayloadParser.parse(qrPayload)
            if (qrData is AdditionMobileDeviceQrPayload) {
                return@withContext mobileDeviceAdditionFromQrFlow.register(
                    registrationState = registrationState,
                    payload = qrData,
                    storedEncDek = storedEncDek,
                )
            }
            if (qrData is AdditionalMobileNamespacesQrPayload) {
                return@withContext additionalMobileNamespacesFromQrFlow.register(
                    registrationState = registrationState,
                    payload = qrData,
                )
            }
            val qrType =
                when (qrData) {
                    is DeviceRegistrationQrPayload -> "deviceRegistration"
                    is NamespaceQrPayload -> "namespace"
                    is NamespaceSyncQrPayload -> "namespace_sync"
                    is AdditionMobileDeviceQrPayload -> error("Handled above")
                    is AdditionalMobileNamespacesQrPayload -> error("Handled above")
                }
            val preparation =
                when (qrData) {
                    is DeviceRegistrationQrPayload -> {
                        Log.d(
                            QR_LOG_TAG,
                            "QR device registration selected. currentAccountId=${registrationState.accountId} namespaceUrl=${registrationState.namespaceUrl} namespaceId=${registrationState.namespaceId} desktopDeviceId=${qrData.desktopDeviceId}",
                        )
                        deviceRegistrationFromQrFlow.prepare(registrationState, qrData)
                    }

                    is NamespaceQrPayload ->
                        namespaceRegistrationFromQrFlow.prepare(
                            registrationState = registrationState,
                            payload = qrData,
                            storedEncDek = storedEncDek,
                        )

                    is NamespaceSyncQrPayload ->
                        namespaceRegistrationFromQrFlow.prepareSync(
                            qrData,
                            registrationState.deviceId,
                        )
                    is AdditionMobileDeviceQrPayload -> error("Handled above")
                    is AdditionalMobileNamespacesQrPayload -> error("Handled above")
                }

            val rawDek =
                if (qrData is NamespaceSyncQrPayload) {
                    dekService.decryptDek(requireNotNull(preparation.targetEncDek))
                } else {
                    decryptRootDeviceDek(
                        dekService = dekService,
                        fetchedEncDek = storedEncDek,
                        storedEncDek = storedEncDek,
                    )
                }
            val reEncryptedDek = dekService.encryptDek(rawDek, preparation.scannedPublicKey)
            val nonce = newRequestNonce()
            val resolvedRootDeviceAccountId = preparation.targetNamespaceAccountId
            val desktopRegistrationDto =
                NewDeviceRegistrationDto(
                    accountId = resolvedRootDeviceAccountId,
                    device =
                        NewDeviceRegistrationDeviceDto(
                            mobileDeviceId = registrationState.deviceId,
                            publicKey = preparation.scannedPublicKey,
                            deviceName = preparation.scannedDeviceName,
                            desktopDeviceId = preparation.scannedDesktopDeviceId,
                        ),
                    dek =
                        NewDeviceRegistrationDekDto(
                            desktopDeviceId = preparation.scannedDesktopDeviceId,
                            encDek = reEncryptedDek,
                        ),
                    nonce = nonce,
                )
            val requestBodyJson = gson.toJson(desktopRegistrationDto)
            val submitSignature = signatureService.signJsonString(requestBodyJson)
            val submitUrl = buildRootDeviceRegistrationUrl(preparation.namespaceEntry.namespaceUrl)
            Log.d(
                QR_LOG_TAG,
                "QR request dump. type=$qrType url=$submitUrl payload=$requestBodyJson",
            )
            Log.d(QR_LOG_TAG, "HTTP POST $submitUrl payload=$requestBodyJson")
            Log.d(
                QR_LOG_TAG,
                "Desktop registration submit start. url=$submitUrl scannedDesktopDeviceId=${preparation.scannedDesktopDeviceId}",
            )
            val submitResponse =
                try {
                    registrationApi.registerNewDevice(
                        submitUrl,
                        submitSignature,
                        desktopRegistrationDto,
                    )
                } catch (exception: Exception) {
                    Log.e(
                        QR_LOG_TAG,
                        "Desktop registration submit failed before HTTP response. url=$submitUrl",
                        exception,
                    )
                    throw mapNetworkException(submitUrl, exception)
                }

            if (!submitResponse.isSuccessful) {
                Log.e(
                    QR_LOG_TAG,
                    "Desktop registration submit returned HTTP ${submitResponse.code()} for $submitUrl",
                )
                val backendError =
                    submitResponse.errorBody()?.charStream()?.use {
                        runCatching { gson.fromJson(it, AccountErrorResponseDto::class.java) }
                            .getOrNull()
                    }
                throw mapDesktopRegistrationFailure(
                    statusCode = submitResponse.code(),
                    url = submitUrl,
                    backendError = backendError,
                )
            }

            Log.d(
                QR_LOG_TAG,
                "Desktop registration submit success. url=$submitUrl scannedDesktopDeviceId=${preparation.scannedDesktopDeviceId}",
            )
            connectedDevicesLocalStore.upsertEntry(
                LocalConnectedDeviceEntry(
                    url = preparation.namespaceEntry.namespaceUrl,
                    accountId = resolvedRootDeviceAccountId,
                    desktopDeviceId = preparation.scannedDesktopDeviceId,
                    namespaces =
                        listOf(
                            LocalConnectedNamespace(
                                accountId = preparation.namespaceEntry.accountId,
                                desktopDeviceId = preparation.scannedDesktopDeviceId,
                                namespaceId = preparation.namespaceEntry.namespaceId,
                                namespace = preparation.namespaceEntry.namespaceName,
                                url = preparation.namespaceEntry.namespaceUrl,
                                email = preparation.namespaceEntry.email,
                            )
                        ),
                )
            )

            fcmTokenSynchronizer.syncNamespace(
                namespaceEntry = preparation.namespaceEntry,
                registrationState = registrationState,
            )
            if (qrData !is NamespaceQrPayload) {
                runCatching { connectedDevicesRepository.fetchConnectedDevices() }
                    .onFailure { exception ->
                        Log.w(
                            QR_LOG_TAG,
                            "QR registration refresh failed after successful server registration. namespaceUrl=${preparation.namespaceEntry.namespaceUrl}",
                            exception,
                        )
                    }
            }

            QrNamespaceRegistrationResult(
                message =
                    "Namespace connected: ${preparation.namespaceEntry.namespaceName} (desktop: ${preparation.scannedDesktopDeviceId})"
            )
        }

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
                    "Unable to decrypt the backend DEK with the device private key, and no local fallback DEK is stored on this device."
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
                    "Unable to decrypt either the backend DEK or the locally stored DEK with the current device private key. The keystore keypair on this device likely no longer matches the public key used during registration."
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
        val duplicateConflict =
            statusCode == 409 ||
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
                "This desktop device is already registered on the server for the scanned namespace."
            )
        }

        return RegistrationFailure.Server(
            backendMessage.ifBlank { "Request failed with HTTP $statusCode at $url." }
        )
    }
}
