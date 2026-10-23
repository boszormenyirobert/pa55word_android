package com.zerointrusion.data.repository.authentication

import com.google.gson.Gson
import com.zerointrusion.data.network.AuthenticationApi
import com.zerointrusion.data.repository.connecteddevices.AccountDetailsStateHolder
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.authentication.AuthenticationRepository
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository

class AuthenticationRepositoryImpl(
    authenticationApi: AuthenticationApi,
    accountDetailsStateHolder: AccountDetailsStateHolder,
    stateStore: RegistrationStateStore,
    signatureService: SignatureService,
    gson: Gson,
    connectedDevicesRepository: ConnectedDevicesRepository,
    connectedDevicesLocalStore: ConnectedDevicesLocalStore,
) : AuthenticationRepository {
    private val authenticationResponseHandler = AuthenticationResponseHandler(
        authenticationApi = authenticationApi,
        accountDetailsStateHolder = accountDetailsStateHolder,
        stateStore = stateStore,
        signatureService = signatureService,
        gson = gson,
        connectedDevicesRepository = connectedDevicesRepository,
        connectedDevicesLocalStore = connectedDevicesLocalStore,
    )
    private val authenticationSessionHandler = AuthenticationSessionHandler(
        authenticationApi = authenticationApi,
        accountDetailsStateHolder = accountDetailsStateHolder,
        stateStore = stateStore,
        signatureService = signatureService,
        gson = gson,
        connectedDevicesRepository = connectedDevicesRepository,
    )
    private val mobileAuthenticationStatusHandler = MobileAuthenticationStatusHandler(
        authenticationApi = authenticationApi,
        signatureService = signatureService,
        gson = gson,
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

    override suspend fun fetchMobileAuthenticationStatus(
        namespaceUrl: String,
        accountId: String,
        mobileDeviceId: String,
        notificationId: String,
    ) = mobileAuthenticationStatusHandler.fetch(
        namespaceUrl = namespaceUrl,
        accountId = accountId,
        mobileDeviceId = mobileDeviceId,
        notificationId = notificationId,
    )
}
