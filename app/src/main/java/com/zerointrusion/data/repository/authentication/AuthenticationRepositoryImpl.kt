package com.zerointrusion.data.repository.authentication

import com.google.gson.Gson
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.connecteddevices.AccountDetailsStateHolder
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.authentication.AuthenticationRepository
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository

class AuthenticationRepositoryImpl(
    registrationApi: RegistrationApi,
    accountDetailsStateHolder: AccountDetailsStateHolder,
    stateStore: RegistrationStateStore,
    signatureService: SignatureService,
    gson: Gson,
    connectedDevicesRepository: ConnectedDevicesRepository,
) : AuthenticationRepository {
    private val authenticationResponseHandler = AuthenticationResponseHandler(
        registrationApi = registrationApi,
        accountDetailsStateHolder = accountDetailsStateHolder,
        stateStore = stateStore,
        signatureService = signatureService,
        gson = gson,
        connectedDevicesRepository = connectedDevicesRepository,
    )
    private val authenticationSessionHandler = AuthenticationSessionHandler(
        registrationApi = registrationApi,
        accountDetailsStateHolder = accountDetailsStateHolder,
        stateStore = stateStore,
        signatureService = signatureService,
        gson = gson,
        connectedDevicesRepository = connectedDevicesRepository,
    )

    override suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        requestingDeviceId: String,
        approved: Boolean,
        namespaceId: String?,
        namespaceUrl: String?,
    ) = authenticationResponseHandler.respondToAuthentication(
        notificationId = notificationId,
        accountId = accountId,
        deviceId = deviceId,
        requestingDeviceId = requestingDeviceId,
        approved = approved,
        namespaceId = namespaceId,
        namespaceUrl = namespaceUrl,
    )

    override suspend fun fetchActiveDesktopSessions(
        namespaceId: String?,
        namespaceUrl: String?,
    ) = authenticationSessionHandler.fetchActiveDesktopSessions(
        namespaceId = namespaceId,
        namespaceUrl = namespaceUrl,
    )

    override suspend fun revokeDesktopSession(
        session: com.zerointrusion.domain.authentication.ActiveDesktopSession,
    ) = authenticationSessionHandler.revokeDesktopSession(
        session = session,
    )
}
