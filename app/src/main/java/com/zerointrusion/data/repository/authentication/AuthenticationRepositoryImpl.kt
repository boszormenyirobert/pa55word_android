package com.zerointrusion.data.repository.authentication

import com.google.gson.Gson
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.authentication.AuthenticationRepository

class AuthenticationRepositoryImpl(
    registrationApi: RegistrationApi,
    namespaceStore: NamespaceStore,
    stateStore: RegistrationStateStore,
    signatureService: SignatureService,
    gson: Gson,
) : AuthenticationRepository {
    private val authenticationResponseHandler = AuthenticationResponseHandler(
        registrationApi = registrationApi,
        namespaceStore = namespaceStore,
        stateStore = stateStore,
        signatureService = signatureService,
        gson = gson,
    )
    private val authenticationSessionHandler = AuthenticationSessionHandler(
        registrationApi = registrationApi,
        namespaceStore = namespaceStore,
        stateStore = stateStore,
        signatureService = signatureService,
        gson = gson,
    )

    override suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        approved: Boolean,
    ) = authenticationResponseHandler.respondToAuthentication(
        notificationId = notificationId,
        accountId = accountId,
        deviceId = deviceId,
        approved = approved,
    )

    override suspend fun fetchActiveDesktopSessions() =
        authenticationSessionHandler.fetchActiveDesktopSessions()

    override suspend fun revokeDesktopSession(
        attestationId: String,
        desktopDeviceId: String,
    ) = authenticationSessionHandler.revokeDesktopSession(
        attestationId = attestationId,
        desktopDeviceId = desktopDeviceId,
    )
}