package com.zerointrusion.data.qr

import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.qr.DeviceRegistrationQrPayload
import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.flow.first

internal data class QrRegistrationPreparation(
    val namespaceEntry: NamespaceEntry,
    val scannedDeviceId: String,
    val scannedPublicKey: String,
    val scannedDeviceName: String? = null,
)

internal class DeviceRegistrationFromQrFlow(
    private val namespaceStore: NamespaceStore,
) {
    suspend fun prepare(
        registrationState: RegistrationState,
        payload: DeviceRegistrationQrPayload,
    ): QrRegistrationPreparation {
        val namespaceEntry = NamespaceEntry(
            namespaceName = registrationState.namespaceName,
            namespaceId = registrationState.namespaceId,
            namespaceUrl = registrationState.namespaceUrl,
            accountId = registrationState.accountId,
            deviceId = payload.deviceId,
            deviceName = payload.deviceName,
            publicKey = payload.publicKey,
        )
        val mergedNamespaces = mergeNamespaces(
            existingNamespaces = namespaceStore.observeNamespaces().first(),
            newNamespace = namespaceEntry,
        )
        namespaceStore.saveNamespaces(mergedNamespaces)

        return QrRegistrationPreparation(
            namespaceEntry = namespaceEntry,
            scannedDeviceId = payload.deviceId,
            scannedPublicKey = payload.publicKey,
            scannedDeviceName = payload.deviceName,
        )
    }
}