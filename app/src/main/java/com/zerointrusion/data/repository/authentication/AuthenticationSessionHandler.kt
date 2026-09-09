package com.zerointrusion.data.repository.authentication

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.AuthenticationSessionDto
import com.zerointrusion.data.network.AuthenticationSessionRevocationResponseRequestDto
import com.zerointrusion.data.network.AuthenticationSessionRevocationResponseDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.AUTHENTICATION_LOG_TAG
import com.zerointrusion.data.repository.common.AuthenticationSignedRequestPayload
import com.zerointrusion.data.repository.common.buildAuthenticationSessionRevocationUrl
import com.zerointrusion.data.repository.common.buildAuthenticationSessionsUrl
import com.zerointrusion.data.repository.common.buildSignedAccountMobileDeviceNonceJson
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.resolveNamespaceEndpointTargets
import com.zerointrusion.data.repository.common.serverFailure
import com.zerointrusion.data.repository.connecteddevices.AccountDetailsStateHolder
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.authentication.DesktopSessionRevocationResult
import com.zerointrusion.domain.common.RegistrationFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

internal class AuthenticationSessionHandler(
    private val registrationApi: RegistrationApi,
    private val accountDetailsStateHolder: AccountDetailsStateHolder,
    private val stateStore: RegistrationStateStore,
    private val signatureService: SignatureService,
    private val gson: Gson,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
) {
    suspend fun fetchActiveDesktopSessions(
        namespaceId: String? = null,
        namespaceUrl: String? = null,
    ): List<ActiveDesktopSession> = withContext(Dispatchers.IO) {
        val registrationState = requireRegistrationState()
        val requestedNamespaceId = namespaceId?.trim().orEmpty()
        val requestedNamespaceUrl = namespaceUrl?.trim()?.trimEnd('/').orEmpty()
        runCatching { connectedDevicesRepository.fetchConnectedDevices() }
            .onFailure { exception ->
                if (accountDetailsStateHolder.currentSnapshot()?.namespaces.isNullOrEmpty()) {
                    throw exception
                }
                Log.w(
                    AUTHENTICATION_LOG_TAG,
                    "AUTH sessions proceeding with cached namespaces because account snapshot refresh failed.",
                    exception,
                )
            }

        val availableNamespaces = accountDetailsStateHolder.currentSnapshot()?.namespaces.orEmpty()
        if (availableNamespaces.isEmpty()) {
            throw RegistrationFailure.Validation(
                "No namespaces are available after refreshing account details for live-session fetch.",
            )
        }
        val requestNamespaces = resolveSessionRequests(
            availableNamespaces = availableNamespaces,
            registrationState = registrationState,
            requestedNamespaceId = requestedNamespaceId,
            requestedNamespaceUrl = requestedNamespaceUrl,
        )

        Log.d(
            AUTHENTICATION_LOG_TAG,
            "AUTH available namespaces before session fetch. requestedNamespaceId=$requestedNamespaceId requestedNamespaceUrl=$requestedNamespaceUrl availableNamespaces=${gson.toJson(availableNamespaces)} selectedNamespaces=${gson.toJson(requestNamespaces)}",
        )

        val outcomes = coroutineScope {
            requestNamespaces.map { requestNamespace ->
                async {
                    fetchNamespaceSessions(
                        requestNamespace = requestNamespace,
                        mobileDeviceId = registrationState.deviceId,
                        availableNamespaces = availableNamespaces,
                    )
                }
            }.awaitAll()
        }

        val successfulSessions = outcomes
            .flatMap { it.sessions }
            .distinctBy { session ->
                listOf(
                    session.attestationId.trim().lowercase(),
                    session.deviceId.trim().lowercase(),
                    session.namespaceUrl.trim().trimEnd('/').lowercase(),
                    session.namespaceName.trim().lowercase(),
                ).joinToString(separator = "|")
            }
        if (outcomes.any { it.wasSuccessful }) {
            return@withContext successfulSessions
        }

        val failures = outcomes.mapNotNull { it.failureMessage }
        throw RegistrationFailure.Server(
            failures.joinToString(separator = " | ").ifBlank {
                "Desktop sessions fetch failed for all known namespaces."
            },
        )
    }

    suspend fun revokeDesktopSession(
        session: ActiveDesktopSession,
    ): DesktopSessionRevocationResult = withContext(Dispatchers.IO) {
        val normalizedAttestationId = session.attestationId.trim()
        val normalizedDesktopDeviceId = session.deviceId.trim()
        val normalizedAccountId = session.accountId.trim()
        val normalizedNamespaceUrl = session.namespaceUrl.trim().trimEnd('/')
        if (normalizedAttestationId.isEmpty()) {
            throw RegistrationFailure.Validation("The selected session does not contain a valid attestationId.")
        }
        if (normalizedDesktopDeviceId.isEmpty()) {
            throw RegistrationFailure.Validation("The selected session does not contain a valid desktop deviceId.")
        }
        if (normalizedAccountId.isEmpty()) {
            throw RegistrationFailure.Validation("The selected session does not contain a valid accountId.")
        }
        if (normalizedNamespaceUrl.isEmpty()) {
            throw RegistrationFailure.Validation("The selected session does not contain a valid namespaceUrl.")
        }

        val registrationState = requireRegistrationState()
        val outcome = revokeNamespaceSession(
            baseUrl = normalizedNamespaceUrl,
            accountId = normalizedAccountId,
            mobileDeviceId = registrationState.deviceId,
            desktopDeviceId = normalizedDesktopDeviceId,
            attestationId = normalizedAttestationId,
        )

        outcome.failureMessage?.let { failureMessage ->
            throw RegistrationFailure.Server(failureMessage)
        }

        (outcome.response
            ?: AuthenticationSessionRevocationResponseDto(success = true, attestationId = normalizedAttestationId))
            .toDomain(normalizedAttestationId)
    }

    private suspend fun revokeNamespaceSession(
        baseUrl: String,
        accountId: String,
        mobileDeviceId: String,
        desktopDeviceId: String,
        attestationId: String,
    ): NamespaceRevocationOutcome {
        val nonce = newNonce()
        val requestPayload = AuthenticationSessionRevocationResponseRequestDto(
            accountId = accountId,
            mobileDeviceId = mobileDeviceId,
            desktopDeviceId = desktopDeviceId,
            attestationId = attestationId,
            nonce = nonce,
        )
        val requestUrl = buildAuthenticationSessionRevocationUrl(
            baseUrl = baseUrl,
            accountId = accountId,
            desktopDeviceId = desktopDeviceId,
            attestationId = attestationId,
            nonce = nonce,
        )
        val requestBodyJson = buildRevocationRequestBodyJson(requestPayload)
        val signature = signatureService.signJsonString(requestBodyJson)
        Log.d(AUTHENTICATION_LOG_TAG, "AUTH session revoke start. url=$requestUrl")
        Log.d(AUTHENTICATION_LOG_TAG, "HTTP DELETE $requestUrl payload=$requestBodyJson")
        val response = try {
            registrationApi.revokeAuthenticationSession(
                url = requestUrl,
                signature = signature,
                request = requestPayload,
            )
        } catch (exception: Exception) {
            Log.e(AUTHENTICATION_LOG_TAG, "AUTH session revoke failed before HTTP response. url=$requestUrl", exception)
            return NamespaceRevocationOutcome(
                failureMessage = mapNetworkException(requestUrl, exception).message
                    ?: "Desktop session revoke failed at $requestUrl.",
            )
        }

        if (!response.isSuccessful) {
            Log.e(AUTHENTICATION_LOG_TAG, "AUTH session revoke failed. http=${response.code()} url=$requestUrl")
            return NamespaceRevocationOutcome(
                failureMessage = serverFailure(gson, response.errorBody()?.charStream(), response.code(), requestUrl).message
                    ?: "Desktop session revoke failed at $requestUrl.",
            )
        }

        return NamespaceRevocationOutcome(
            response = response.body()
                ?: AuthenticationSessionRevocationResponseDto(success = true, attestationId = attestationId),
        )
    }

    private suspend fun fetchNamespaceSessions(
        requestNamespace: NamespaceSessionRequest,
        mobileDeviceId: String,
        availableNamespaces: List<com.zerointrusion.domain.namespaces.NamespaceEntry>,
    ): NamespaceSessionFetchOutcome {
        val nonce = newNonce()
        val requestPayload = AuthenticationSignedRequestPayload(
            accountId = requestNamespace.accountId,
            mobileDeviceId = mobileDeviceId,
            nonce = nonce,
        )
        val requestUrl = buildAuthenticationSessionsUrl(
            baseUrl = requestNamespace.namespaceUrl,
            accountId = requestNamespace.accountId,
            mobileDeviceId = mobileDeviceId,
            nonce = nonce,
        )
        val requestBodyJson = buildSignedAccountMobileDeviceNonceJson(
            gson = gson,
            accountId = requestPayload.accountId,
            mobileDeviceId = requestPayload.mobileDeviceId,
            nonce = requestPayload.nonce,
        )
        val signature = signatureService.signJsonString(requestBodyJson)

        Log.d(AUTHENTICATION_LOG_TAG, "AUTH sessions fetch start. url=$requestUrl namespaceId=${requestNamespace.namespaceId}")
        Log.d(AUTHENTICATION_LOG_TAG, "HTTP GET $requestUrl payload=$requestBodyJson")
        val response = try {
            registrationApi.fetchAuthenticationSessions(
                url = requestUrl,
                signature = signature,
            )
        } catch (exception: Exception) {
            Log.e(AUTHENTICATION_LOG_TAG, "AUTH sessions fetch failed before HTTP response. url=$requestUrl namespaceId=${requestNamespace.namespaceId}", exception)
            return NamespaceSessionFetchOutcome(
                failureMessage = mapNetworkException(requestUrl, exception).message
                    ?: "Desktop sessions fetch failed at $requestUrl.",
            )
        }

        if (!response.isSuccessful) {
            Log.e(AUTHENTICATION_LOG_TAG, "AUTH sessions fetch failed. http=${response.code()} url=$requestUrl namespaceId=${requestNamespace.namespaceId}")
            return NamespaceSessionFetchOutcome(
                failureMessage = serverFailure(gson, response.errorBody()?.charStream(), response.code(), requestUrl).message
                    ?: "Desktop sessions fetch failed at $requestUrl.",
            )
        }

        val responseBody = response.body().orEmpty()
        Log.d(
            AUTHENTICATION_LOG_TAG,
            "AUTH sessions raw response. url=$requestUrl namespaceId=${requestNamespace.namespaceId} namespaceName=${requestNamespace.namespaceName} body=${gson.toJson(responseBody)}",
        )

        return NamespaceSessionFetchOutcome(
            wasSuccessful = true,
            sessions = responseBody.map { sessionDto ->
                sessionDto.toDomain(
                    requestNamespace = requestNamespace,
                    availableNamespaces = availableNamespaces,
                )
            },
        )
    }

    private suspend fun requireRegistrationState() = stateStore.observeState().first()
        ?.also { state ->
            if (state.accountId.isBlank()) {
                throw RegistrationFailure.Validation("The stored registration state does not contain a valid accountId.")
            }
            if (state.deviceId.isBlank()) {
                throw RegistrationFailure.Validation("The stored registration state does not contain a valid deviceId.")
            }
            if (state.namespaceUrl.isBlank()) {
                throw RegistrationFailure.Validation("The stored registration state does not contain a valid namespaceUrl.")
            }
        }
        ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

    private fun newNonce(): String = UUID.randomUUID().toString().replace("-", "")

    private fun AuthenticationSessionDto.toDomain(): ActiveDesktopSession = ActiveDesktopSession(
        accountId = "",
        attestationId = attestationId,
        deviceId = deviceId.orEmpty(),
        deviceName = deviceId.orEmpty(),
        namespaceName = "",
        namespaceUrl = "",
        issuedAt = issuedAt,
        expiresAt = expiresAt,
    )

    private fun AuthenticationSessionDto.toDomain(
        requestNamespace: NamespaceSessionRequest,
        availableNamespaces: List<com.zerointrusion.domain.namespaces.NamespaceEntry>,
    ): ActiveDesktopSession {
        // Server may omit deviceId when the session's namespace only has a single desktop device.
        val resolvedSessionDeviceId = deviceId.orEmpty().ifBlank {
            availableNamespaces
                .firstOrNull { it.namespaceId.trim() == requestNamespace.namespaceId.trim() }
                ?.desktopDeviceId.orEmpty()
        }
        val resolvedDeviceName = resolveSessionDeviceName(
            desktopDeviceId = resolvedSessionDeviceId,
            availableNamespaces = availableNamespaces,
        )

        Log.d(
            AUTHENTICATION_LOG_TAG,
            "AUTH session device resolution. namespaceId=${requestNamespace.namespaceId} namespaceName=${requestNamespace.namespaceName} sessionDeviceId=$resolvedSessionDeviceId resolvedDeviceName=$resolvedDeviceName",
        )

        return ActiveDesktopSession(
            accountId = requestNamespace.accountId,
            attestationId = attestationId,
            deviceId = resolvedSessionDeviceId,
            deviceName = resolvedDeviceName,
            namespaceName = requestNamespace.namespaceName,
            namespaceUrl = requestNamespace.namespaceUrl,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )
    }

    private fun resolveSessionDeviceName(
        desktopDeviceId: String,
        availableNamespaces: List<com.zerointrusion.domain.namespaces.NamespaceEntry>,
    ): String {
        val normalizedDesktopDeviceId = desktopDeviceId.trim()
        if (normalizedDesktopDeviceId.isEmpty()) {
            return desktopDeviceId
        }

        val matchingNamespaceEntry = availableNamespaces.firstOrNull { namespaceEntry ->
            namespaceEntry.desktopDeviceId.trim().equals(normalizedDesktopDeviceId, ignoreCase = true)
        }

        return matchingNamespaceEntry?.deviceName?.trim()?.takeIf { it.isNotEmpty() }
            ?: normalizedDesktopDeviceId
    }

    private fun AuthenticationSessionRevocationResponseDto.toDomain(
        fallbackAttestationId: String,
    ): DesktopSessionRevocationResult = DesktopSessionRevocationResult(
        success = success,
        attestationId = attestationId?.takeIf { it.isNotBlank() } ?: fallbackAttestationId,
    )

    private fun buildRevocationRequestBodyJson(
        payload: AuthenticationSessionRevocationResponseRequestDto,
    ): String = buildString {
        append('{')
        append("\"accountId\":")
        append(gson.toJson(payload.accountId))
        append(',')
        append("\"mobileDeviceId\":")
        append(gson.toJson(payload.mobileDeviceId))
        append(',')
        append("\"desktopDeviceId\":")
        append(gson.toJson(payload.desktopDeviceId))
        append(',')
        append("\"attestationId\":")
        append(gson.toJson(payload.attestationId))
        append(',')
        append("\"nonce\":")
        append(gson.toJson(payload.nonce))
        append('}')
    }

    private data class NamespaceRevocationOutcome(
        val response: AuthenticationSessionRevocationResponseDto? = null,
        val failureMessage: String? = null,
    )

    private data class NamespaceSessionFetchOutcome(
        val sessions: List<ActiveDesktopSession> = emptyList(),
        val wasSuccessful: Boolean = false,
        val failureMessage: String? = null,
    )

    private data class NamespaceSessionRequest(
        val accountId: String,
        val namespaceId: String,
        val deviceName: String,
        val namespaceName: String,
        val namespaceUrl: String,
    ) {
        fun storageIdentity(): String = listOf(
            accountId.trim().lowercase(),
            namespaceId.trim().lowercase(),
            namespaceUrl.trim().trimEnd('/').lowercase(),
        ).joinToString(separator = "|")
    }

    private fun resolveSessionRequests(
        availableNamespaces: List<com.zerointrusion.domain.namespaces.NamespaceEntry>,
        registrationState: com.zerointrusion.domain.registration.RegistrationState,
        requestedNamespaceId: String,
        requestedNamespaceUrl: String,
    ): List<NamespaceSessionRequest> {
        val hasExplicitNamespaceRequest = requestedNamespaceId.isNotEmpty() || requestedNamespaceUrl.isNotEmpty()

        if (hasExplicitNamespaceRequest) {
            val matchingNamespaces = availableNamespaces.filter { namespaceEntry ->
                val namespaceIdMatches = requestedNamespaceId.isEmpty() ||
                    namespaceEntry.namespaceId.trim().equals(requestedNamespaceId, ignoreCase = true)
                val namespaceUrlMatches = requestedNamespaceUrl.isEmpty() ||
                    namespaceEntry.namespaceUrl.trim().trimEnd('/').equals(requestedNamespaceUrl, ignoreCase = true)
                namespaceIdMatches && namespaceUrlMatches
            }

            if (matchingNamespaces.isNotEmpty()) {
                return matchingNamespaces.toSessionRequests(registrationState)
                    .distinctBy { it.storageIdentity() }
            }

            return listOf(
                NamespaceSessionRequest(
                    accountId = registrationState.accountId,
                    namespaceId = if (requestedNamespaceId.isNotEmpty()) requestedNamespaceId else registrationState.namespaceId,
                    deviceName = registrationState.deviceName,
                    namespaceName = registrationState.namespaceName,
                    namespaceUrl = if (requestedNamespaceUrl.isNotEmpty()) requestedNamespaceUrl else registrationState.namespaceUrl,
                ),
            )
        }

        if (availableNamespaces.isNotEmpty()) {
            return availableNamespaces.toSessionRequests(registrationState)
                .distinctBy { it.storageIdentity() }
        }

        return listOf(
            NamespaceSessionRequest(
                accountId = registrationState.accountId,
                namespaceId = registrationState.namespaceId,
                deviceName = registrationState.deviceName,
                namespaceName = registrationState.namespaceName,
                namespaceUrl = registrationState.namespaceUrl,
            ),
        )
    }

    private fun List<com.zerointrusion.domain.namespaces.NamespaceEntry>.toSessionRequests(
        registrationState: com.zerointrusion.domain.registration.RegistrationState,
    ): List<NamespaceSessionRequest> = resolveNamespaceEndpointTargets(
        namespaces = this,
        referenceNamespaceUrl = registrationState.namespaceUrl,
        referenceAccountId = registrationState.accountId,
    ).map { target ->
        val representative = target.entries.first()
            NamespaceSessionRequest(
                accountId = target.accountId,
                namespaceId = target.namespaceId,
                deviceName = representative.deviceName?.trim().takeUnless { it.isNullOrEmpty() } ?: representative.desktopDeviceId,
                namespaceName = target.namespaceName,
                namespaceUrl = target.namespaceUrl,
            )
    }
}