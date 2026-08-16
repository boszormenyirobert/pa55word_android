package com.zerointrusion.data.repository.qr

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.DekDto
import com.zerointrusion.data.network.DeviceDto
import com.zerointrusion.data.network.NewDeviceRegistrationDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.qr.DeviceRegistrationFromQrFlow
import com.zerointrusion.data.qr.NamespaceRegistrationFromQrFlow
import com.zerointrusion.data.qr.QrPayloadParser
import com.zerointrusion.data.repository.common.QR_LOG_TAG
import com.zerointrusion.data.repository.common.buildRootDeviceRegistrationUrl
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.serverFailure
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
    private val qrPayloadParser: QrPayloadParser,
    private val deviceRegistrationFromQrFlow: DeviceRegistrationFromQrFlow,
    private val namespaceRegistrationFromQrFlow: NamespaceRegistrationFromQrFlow,
) {
    suspend fun registerNamespaceFromQr(qrPayload: String): QrNamespaceRegistrationResult = withContext(Dispatchers.IO) {
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
                    "QR device registration selected. currentAccountId=${registrationState.accountId} namespaceUrl=${registrationState.namespaceUrl} namespaceId=${registrationState.namespaceId} desktopDeviceId=${qrData.deviceId}",
                )
                deviceRegistrationFromQrFlow.prepare(registrationState, qrData)
            }

            is NamespaceQrPayload -> namespaceRegistrationFromQrFlow.prepare(
                registrationState = registrationState,
                payload = qrData,
                storedEncDek = storedEncDek,
            )
        }

        val rawDek = decryptRootDeviceDek(
            dekService = dekService,
            fetchedEncDek = storedEncDek,
            storedEncDek = storedEncDek,
        )
        val reEncryptedDek = dekService.encryptDek(rawDek, preparation.scannedPublicKey)
        val desktopRegistrationDto = NewDeviceRegistrationDto(
            accountId = preparation.namespaceEntry.accountId,
            device = DeviceDto(
                deviceId = registrationState.deviceId,
                publicKey = preparation.scannedPublicKey,
                deviceIdAdditional = preparation.scannedDeviceId,
            ),
            dek = DekDto(
                deviceId = preparation.scannedDeviceId,
                encDek = reEncryptedDek,
            ),
        )
        val submitSignature = signatureService.signJsonString(gson.toJson(desktopRegistrationDto))
        val submitUrl = buildRootDeviceRegistrationUrl(preparation.namespaceEntry.namespaceUrl)
        Log.d(QR_LOG_TAG, "Desktop registration submit start. url=$submitUrl scannedDeviceId=${preparation.scannedDeviceId}")
        val submitResponse = try {
            registrationApi.registerNewDevice(submitUrl, submitSignature, desktopRegistrationDto)
        } catch (exception: Exception) {
            Log.e(QR_LOG_TAG, "Desktop registration submit failed before HTTP response. url=$submitUrl", exception)
            throw mapNetworkException(submitUrl, exception)
        }

        if (!submitResponse.isSuccessful) {
            Log.e(QR_LOG_TAG, "Desktop registration submit returned HTTP ${submitResponse.code()} for $submitUrl")
            throw serverFailure(gson, submitResponse.errorBody()?.charStream(), submitResponse.code(), submitUrl)
        }

        Log.d(QR_LOG_TAG, "Desktop registration submit success. url=$submitUrl scannedDeviceId=${preparation.scannedDeviceId}")

        fcmTokenSynchronizer.syncNamespace(
            namespaceEntry = preparation.namespaceEntry,
            registrationState = registrationState,
        )

        QrNamespaceRegistrationResult(
            namespaceName = preparation.namespaceEntry.namespaceName,
            desktopDeviceId = preparation.scannedDeviceId,
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
}