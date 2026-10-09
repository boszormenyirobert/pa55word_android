package com.zerointrusion.data.qr

import com.google.gson.Gson
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.registration.MobileNamespaceRegistrationHandler
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.qr.NamespaceQrPayload
import com.zerointrusion.domain.qr.NamespaceSyncQrPayload
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator

internal class NamespaceRegistrationFromQrFlow(
    private val registrationApi: RegistrationApi,
    private val keyStoreService: KeyStoreService,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val gson: Gson,
    private val validator: RegistrationValidator,
) {
    private val mobileRegistrationHandler = MobileNamespaceRegistrationHandler(
        registrationApi, keyStoreService, connectedDevicesLocalStore, gson, validator,
    )

    suspend fun prepare(
        registrationState: RegistrationState,
        payload: NamespaceQrPayload,
        storedEncDek: String,
    ): QrRegistrationPreparation {
        val registration = mobileRegistrationHandler.register(
            state = registrationState,
            namespaceId = payload.namespaceId,
            namespaceName = payload.namespaceName,
            namespaceUrl = payload.namespaceUrl,
            storedEncDek = storedEncDek,
            desktopDeviceId = payload.desktopDeviceId,
        )

        val namespaceEntry = NamespaceEntry(
            namespaceName = registration.namespaceName,
            namespaceId = registration.namespaceId,
            namespaceUrl = registration.namespaceUrl,
            accountId = registration.accountId,
            desktopDeviceId = payload.desktopDeviceId,
            deviceName = payload.deviceName,
            publicKey = payload.publicKey,
        )

        return QrRegistrationPreparation(
            namespaceEntry = namespaceEntry,
            targetNamespaceAccountId = registration.accountId,
            scannedDesktopDeviceId = payload.desktopDeviceId,
            scannedPublicKey = payload.publicKey,
            scannedDeviceName = payload.deviceName,
        )
    }

    suspend fun prepareSync(
        payload: NamespaceSyncQrPayload,
        mobileDeviceId: String,
    ): QrRegistrationPreparation {
        validateNamespaceSyncPayload(payload)
        connectedDevicesRepository.fetchConnectedDevices()
        val registrations = connectedDevicesLocalStore.currentMobileRegistrations()
        val registration = resolveNamespaceSyncAccount(registrations, payload, mobileDeviceId)

        return QrRegistrationPreparation(
            namespaceEntry = NamespaceEntry(
                namespaceName = registration.namespaceName,
                namespaceId = registration.namespaceId,
                namespaceUrl = registration.namespaceUrl,
                accountId = registration.accountId,
                desktopDeviceId = payload.desktopDeviceId,
                root = registration.root,
            ),
            targetNamespaceAccountId = registration.accountId,
            scannedDesktopDeviceId = payload.desktopDeviceId,
            scannedPublicKey = payload.publicKey,
            scannedDeviceName = payload.deviceName,
            targetEncDek = registration.encDek,
        )
    }

    private fun validateNamespaceSyncPayload(payload: NamespaceSyncQrPayload) {
        if (!validator.isValidNamespaceId(payload.namespaceId)) {
            throw RegistrationFailure.Validation("The scanned QR payload namespaceId is invalid.")
        }
        val namespaceUrlError = validator.validateNamespaceUrl(payload.namespaceUrl)
        if (namespaceUrlError != null) {
            throw RegistrationFailure.Validation(namespaceUrlError)
        }
    }

}
