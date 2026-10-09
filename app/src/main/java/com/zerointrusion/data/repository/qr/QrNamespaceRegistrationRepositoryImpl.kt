package com.zerointrusion.data.repository.qr

import com.google.gson.Gson
import com.zerointrusion.data.firebase.FirebaseService
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.qr.DeviceRegistrationFromQrFlow
import com.zerointrusion.data.qr.AdditionalMobileNamespacesFromQrFlow
import com.zerointrusion.data.qr.MobileDeviceAdditionFromQrFlow
import com.zerointrusion.data.qr.NamespaceRegistrationFromQrFlow
import com.zerointrusion.data.qr.QrPayloadParser
import com.zerointrusion.data.repository.connecteddevices.AccountDetailsStateHolder
import com.zerointrusion.data.repository.registration.FcmTokenSynchronizer
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.qr.QrNamespaceRegistrationResult
import com.zerointrusion.domain.qr.QrNamespaceRegistrationRepository
import com.zerointrusion.domain.registration.RegistrationValidator

class QrNamespaceRegistrationRepositoryImpl(
    registrationApi: RegistrationApi,
    keyStoreService: KeyStoreService,
    stateStore: RegistrationStateStore,
    dekService: DekService,
    signatureService: SignatureService,
    accountDetailsStateHolder: AccountDetailsStateHolder,
    connectedDevicesRepository: ConnectedDevicesRepository,
    connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    validator: RegistrationValidator,
    firebaseService: FirebaseService,
    gson: Gson,
) : QrNamespaceRegistrationRepository {
    private val fcmTokenSynchronizer = FcmTokenSynchronizer(
        firebaseService = firebaseService,
        stateStore = stateStore,
    )
    private val qrPayloadParser = QrPayloadParser(gson, validator)
    private val deviceRegistrationFromQrFlow = DeviceRegistrationFromQrFlow()
    private val namespaceRegistrationFromQrFlow = NamespaceRegistrationFromQrFlow(
        registrationApi = registrationApi,
        keyStoreService = keyStoreService,
        connectedDevicesRepository = connectedDevicesRepository,
        connectedDevicesLocalStore = connectedDevicesLocalStore,
        gson = gson,
        validator = validator,
    )
    private val mobileDeviceAdditionFromQrFlow = MobileDeviceAdditionFromQrFlow(
        registrationApi = registrationApi,
        accountDetailsStateHolder = accountDetailsStateHolder,
        connectedDevicesLocalStore = connectedDevicesLocalStore,
        dekService = dekService,
        signatureService = signatureService,
        gson = gson,
    )
    private val additionalMobileNamespacesFromQrFlow = AdditionalMobileNamespacesFromQrFlow(
        stateStore = stateStore,
        connectedDevicesLocalStore = connectedDevicesLocalStore,
        fcmTokenSynchronizer = fcmTokenSynchronizer,
        connectedDevicesRepository = connectedDevicesRepository,
    )
    private val qrNamespaceRegistrationHandler = QrNamespaceRegistrationHandler(
        registrationApi = registrationApi,
        stateStore = stateStore,
        dekService = dekService,
        signatureService = signatureService,
        gson = gson,
        fcmTokenSynchronizer = fcmTokenSynchronizer,
        connectedDevicesRepository = connectedDevicesRepository,
        connectedDevicesLocalStore = connectedDevicesLocalStore,
        qrPayloadParser = qrPayloadParser,
        deviceRegistrationFromQrFlow = deviceRegistrationFromQrFlow,
        namespaceRegistrationFromQrFlow = namespaceRegistrationFromQrFlow,
        mobileDeviceAdditionFromQrFlow = mobileDeviceAdditionFromQrFlow,
        additionalMobileNamespacesFromQrFlow = additionalMobileNamespacesFromQrFlow,
    )

    override suspend fun registerNamespaceFromQr(
        qrPayload: String,
        rootDeviceAccountId: String?,
    ): QrNamespaceRegistrationResult =
        qrNamespaceRegistrationHandler.registerNamespaceFromQr(
            qrPayload = qrPayload,
            rootDeviceAccountId = rootDeviceAccountId,
        )
}