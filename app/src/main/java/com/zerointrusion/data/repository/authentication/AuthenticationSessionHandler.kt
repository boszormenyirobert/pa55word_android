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
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.serverFailure
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.authentication.ActiveDesktopSession
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
    private val namespaceStore: NamespaceStore,
    private val stateStore: RegistrationStateStore,
    private val signatureService: SignatureService,
    private val gson: Gson,
) {
    suspend fun fetchActiveDesktopSessions(): List<ActiveDesktopSession> = withContext(Dispatchers.IO) {
        val registrationState = requireRegistrationState()
        val nonce = newNonce()
        val requestPayload = AuthenticationSignedRequestPayload(
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
            nonce = nonce,
        )
        val requestUrl = buildAuthenticationSessionsUrl(
            baseUrl = registrationState.namespaceUrl,
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
            nonce = nonce,
        )
        val requestBodyJson = gson.toJson(requestPayload)
        val signature = signatureService.signJsonString(requestBodyJson)

        Log.d(AUTHENTICATION_LOG_TAG, "AUTH sessions fetch start. url=$requestUrl")
        val response = try {
            registrationApi.fetchAuthenticationSessions(
                url = requestUrl,
                signature = signature,
            )
        } catch (exception: Exception) {
            Log.e(AUTHENTICATION_LOG_TAG, "AUTH sessions fetch failed before HTTP response. url=$requestUrl", exception)
            throw mapNetworkException(requestUrl, exception)
        }

        if (!response.isSuccessful) {
            Log.e(AUTHENTICATION_LOG_TAG, "AUTH sessions fetch failed. http=${response.code()} url=$requestUrl")
            throw serverFailure(gson, response.errorBody()?.charStream(), response.code(), requestUrl)
        }

        response.body().orEmpty().map { it.toDomain() }
    }

    suspend fun revokeDesktopSession(
        attestationId: String,
        desktopDeviceId: String,
    ): DesktopSessionRevocationResult = withContext(Dispatchers.IO) {
        val normalizedAttestationId = attestationId.trim()
        val normalizedDesktopDeviceId = desktopDeviceId.trim()
        if (normalizedAttestationId.isEmpty()) {
            throw RegistrationFailure.Validation("The selected session does not contain a valid attestationId.")
        }
        if (normalizedDesktopDeviceId.isEmpty()) {
            throw RegistrationFailure.Validation("The selected session does not contain a valid desktop deviceId.")
        }

        val registrationState = requireRegistrationState()
        val requestNamespaces = namespaceStore.observeNamespaces().first()
            .distinctBy { it.namespaceUrl.trim().trimEnd('/') + "|" + it.namespaceId }
        if (requestNamespaces.isEmpty()) {
            throw RegistrationFailure.Validation(
                "No locally stored namespace URL is available for desktop session revoke.",
            )
        }

        val outcomes = coroutineScope {
            requestNamespaces.map { namespaceEntry ->
                async {
                    revokeNamespaceSession(
                        namespaceEntry = namespaceEntry,
                        mobileDeviceId = registrationState.deviceId,
                    )
                }
            }.awaitAll()
        }

        val failures = outcomes.mapNotNull { it.failureMessage }

        if (failures.isNotEmpty()) {
            throw RegistrationFailure.Server(failures.joinToString(separator = " | "))
        }

        val lastSuccessResponse = outcomes.mapNotNull { it.response }.lastOrNull()

        (lastSuccessResponse
            ?: AuthenticationSessionRevocationResponseDto(success = true, attestationId = normalizedAttestationId))
            .toDomain(normalizedAttestationId)
    }

    private suspend fun revokeNamespaceSession(
        namespaceEntry: com.zerointrusion.domain.namespaces.NamespaceEntry,
        mobileDeviceId: String,
    ): NamespaceRevocationOutcome {
        val namespaceAttestationId = namespaceEntry.attestationId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return NamespaceRevocationOutcome(
                failureMessage = "Desktop session revoke failed for ${namespaceEntry.namespaceName}: no namespace attestationId is stored for ${namespaceEntry.namespaceUrl}.",
            )

        val nonce = newNonce()
        val requestPayload = AuthenticationSessionRevocationResponseRequestDto(
            accountId = namespaceEntry.accountId,
            deviceId = mobileDeviceId,
            desktopId = namespaceEntry.deviceId,
            attestationId = namespaceAttestationId,
            nonce = nonce,
        )
        val requestUrl = buildAuthenticationSessionRevocationUrl(
            baseUrl = namespaceEntry.namespaceUrl,
            accountId = namespaceEntry.accountId,
            desktopDeviceId = namespaceEntry.deviceId,
            attestationId = namespaceAttestationId,
            nonce = nonce,
        )
        val requestBodyJson = buildRevocationRequestBodyJson(requestPayload)
        val signature = signatureService.signJsonString(requestBodyJson)
        Log.d(AUTHENTICATION_LOG_TAG, "AUTH session revoke start. url=$requestUrl")
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
                ?: AuthenticationSessionRevocationResponseDto(success = true, attestationId = namespaceAttestationId),
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
        attestationId = attestationId,
        deviceId = deviceId,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
    )

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
        append("\"deviceId\":")
        append(gson.toJson(payload.deviceId))
        append(',')
        append("\"desktopId\":")
        append(gson.toJson(payload.desktopId))
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
}