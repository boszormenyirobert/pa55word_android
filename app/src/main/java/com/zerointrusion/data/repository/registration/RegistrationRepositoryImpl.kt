package com.zerointrusion.data.repository.registration

import com.google.gson.Gson
import com.zerointrusion.data.firebase.FirebaseService
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.DeviceService
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.NamespaceService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.registration.RegistrationRepository
import com.zerointrusion.domain.registration.RegistrationFormInput
import com.zerointrusion.domain.registration.RegistrationResult
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import kotlinx.coroutines.flow.Flow

class RegistrationRepositoryImpl(
    registrationApi: RegistrationApi,
    firebaseService: FirebaseService,
    keyStoreService: KeyStoreService,
    deviceService: DeviceService,
    namespaceService: NamespaceService,
    dekService: DekService,
    signatureService: SignatureService,
    private val stateStore: RegistrationStateStore,
    validator: RegistrationValidator,
    gson: Gson,
) : RegistrationRepository {
    private val fcmTokenSynchronizer = FcmTokenSynchronizer(
        firebaseService = firebaseService,
        stateStore = stateStore,
    )
    private val accountRegistrationHandler = AccountRegistrationHandler(
        registrationApi = registrationApi,
        keyStoreService = keyStoreService,
        deviceService = deviceService,
        namespaceService = namespaceService,
        dekService = dekService,
        signatureService = signatureService,
        stateStore = stateStore,
        validator = validator,
        gson = gson,
        fcmTokenSynchronizer = fcmTokenSynchronizer,
    )

    override fun observeRegistrationState(): Flow<RegistrationState?> = stateStore.observeState()

    override suspend fun register(input: RegistrationFormInput): RegistrationResult =
        accountRegistrationHandler.register(input)

    override suspend fun syncFcmToken(state: RegistrationState): RegistrationState =
        fcmTokenSynchronizer.sync(state)
}