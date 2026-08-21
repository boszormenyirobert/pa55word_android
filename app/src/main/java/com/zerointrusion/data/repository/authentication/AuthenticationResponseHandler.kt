package com.zerointrusion.data.repository.authentication

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.AuthenticationAttestationEnvelopeDto
import com.zerointrusion.data.network.AuthenticationResponseRequestDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.buildAuthenticationDirectResponseUrl
import com.zerointrusion.data.repository.common.REG_LOG_TAG
import com.zerointrusion.data.repository.common.buildAuthenticationResponseUrl
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

internal class AuthenticationResponseHandler(
    private val registrationApi: RegistrationApi,
    private val namespaceStore: NamespaceStore,
    private val stateStore: RegistrationStateStore,
    private val signatureService: SignatureService,
    private val gson: Gson,
) {
    suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        approved: Boolean,
    ) = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        val availableNamespaces = namespaceStore.observeNamespaces().first()
        val targetNamespace = availableNamespaces
            .firstOrNull { it.deviceId == deviceId && it.accountId == accountId }
            ?: throw RegistrationFailure.Validation(
                "No locally stored namespace matches deviceId=$deviceId and accountId=$accountId for authentication response.",
            )
        val responseNamespaces = availableNamespaces
            .distinctBy { it.namespaceUrl.trim().trimEnd('/') + "|" + it.namespaceId }
        if (responseNamespaces.isEmpty()) {
            throw RegistrationFailure.Validation(
                "No locally stored namespace URL is available for authentication response.",
            )
        }
        Log.d(REG_LOG_TAG, "Authentication response start. namespaceDeviceId=${targetNamespace.deviceId} signingDeviceId=${registrationState.deviceId} accountId=${targetNamespace.accountId} approved=$approved")

        val issuedAtEpochSeconds = Instant.now().epochSecond
        val expiryEpochSeconds = issuedAtEpochSeconds + MAX_ATTESTATION_LIFETIME_SECONDS
        val failures = mutableListOf<String>()
        responseNamespaces.forEach { namespaceEntry ->
            val requestPayload = AuthenticationResponseRequestDto(
                notificationId = notificationId,
                accountId = namespaceEntry.accountId,
                deviceId = registrationState.deviceId,
                approved = approved,
                nonce = newNonce(),
                envelop = AuthenticationAttestationEnvelopeDto(
                    desktopId = namespaceEntry.deviceId,
                    deviceId = registrationState.deviceId,
                    namespaces = if (approved) listOf(namespaceEntry.namespaceId) else emptyList(),
                    iat = issuedAtEpochSeconds,
                    exp = expiryEpochSeconds,
                    nonce = newNonce(),
                ),
            )
            val responseUrl = if (namespaceEntry.deviceId == targetNamespace.deviceId && namespaceEntry.accountId == targetNamespace.accountId) {
                buildAuthenticationResponseUrl(namespaceEntry.namespaceUrl)
            } else {
                buildAuthenticationDirectResponseUrl(namespaceEntry.namespaceUrl)
            }
            val requestBodyJson = gson.toJson(requestPayload)
            val signature = signatureService.signJsonString(requestBodyJson)
            val response = try {
                if (namespaceEntry.deviceId == targetNamespace.deviceId && namespaceEntry.accountId == targetNamespace.accountId) {
                    registrationApi.sendAuthenticationResponse(
                        url = responseUrl,
                        signature = signature,
                        request = requestPayload,
                    )
                } else {
                    registrationApi.sendAuthenticationDirectResponse(
                        url = responseUrl,
                        signature = signature,
                        request = requestPayload,
                    )
                }
            } catch (exception: Exception) {
                failures += mapFailureMessage(responseUrl, exception)
                return@forEach
            }

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                failures += errorBody ?: "Authentication response failed with HTTP ${response.code()} at $responseUrl."
            }
        }

        if (failures.isNotEmpty()) {
            throw RegistrationFailure.Server(failures.joinToString(separator = " | "))
        }
    }

    private fun newNonce(): String = UUID.randomUUID().toString().replace("-", "")

    private fun mapFailureMessage(
        url: String,
        exception: Exception,
    ): String = com.zerointrusion.data.repository.common.mapNetworkException(url, exception).message
        ?: "Authentication response failed at $url."

    private companion object {
        const val MAX_ATTESTATION_LIFETIME_SECONDS = 7200L
    }

}