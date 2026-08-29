package com.zerointrusion.data.qr

import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.qr.DeviceRegistrationQrPayload
import com.zerointrusion.domain.registration.RegistrationState

internal data class QrRegistrationPreparation(
    val namespaceEntry: NamespaceEntry,
    val targetNamespaceAccountId: String,
    val scannedDesktopDeviceId: String,
    val scannedPublicKey: String,
    val scannedDeviceName: String? = null,
)

internal class DeviceRegistrationFromQrFlow(
)
{
    suspend fun prepare(
        registrationState: RegistrationState,
        payload: DeviceRegistrationQrPayload,
    ): QrRegistrationPreparation {
        val namespaceEntry = NamespaceEntry(
            namespaceName = registrationState.namespaceName,
            namespaceId = registrationState.namespaceId,
            namespaceUrl = registrationState.namespaceUrl,
            accountId = registrationState.accountId,
            desktopDeviceId = payload.desktopDeviceId,
            deviceName = payload.deviceName,
            publicKey = payload.publicKey,
            foreignAccountId = registrationState.accountId,
        )

        return QrRegistrationPreparation(
            namespaceEntry = namespaceEntry,
            targetNamespaceAccountId = registrationState.accountId,
            scannedDesktopDeviceId = payload.desktopDeviceId,
            scannedPublicKey = payload.publicKey,
            scannedDeviceName = payload.deviceName,
        )
    }
}