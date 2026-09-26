package com.zerointrusion.data.repository.authentication

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.zerointrusion.data.network.AuthenticationAttestationEnvelopeDto
import com.zerointrusion.data.network.AuthenticationResponseRequestDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.REG_LOG_TAG
import com.zerointrusion.data.repository.common.buildAuthenticationResponseUrl
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.repository.connecteddevices.AccountDetailsStateHolder
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.data.storage.toNamespaceEntries
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.authentication.AuthenticationResponseResult
import com.zerointrusion.domain.authentication.NamespaceAuthenticationResult
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant

internal class AuthenticationResponseHandler(
    private val registrationApi: RegistrationApi,
    private val accountDetailsStateHolder: AccountDetailsStateHolder,
    private val stateStore: RegistrationStateStore,
    private val signatureService: SignatureService,
    private val gson: Gson,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
) {
    suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        requestingDeviceId: String,
        approved: Boolean,
        namespaceId: String? = null,
        namespaceUrl: String? = null,
    ): AuthenticationResponseResult = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")
        val normalizedMobileDeviceId = deviceId.trim()
        val normalizedRequestingDeviceId = requestingDeviceId.trim()
        val requiredNamespaceId = namespaceId?.trim().orEmpty()
        if (requiredNamespaceId.isEmpty()) {
            throw RegistrationFailure.Validation(
                "Authentication response blocked because the notification did not include namespaceId.",
            )
        }
        if (normalizedMobileDeviceId.isEmpty()) {
            throw RegistrationFailure.Validation(
                "Authentication response blocked because the notification did not include the target mobile deviceId.",
            )
        }
        if (normalizedRequestingDeviceId.isEmpty()) {
            throw RegistrationFailure.Validation(
                "Authentication response blocked because the notification did not include desktopDeviceId.",
            )
        }
        if (!registrationState.deviceId.equals(normalizedMobileDeviceId, ignoreCase = true)) {
            throw RegistrationFailure.Validation(
                "Authentication response blocked because the notification targeted mobileDeviceId=$normalizedMobileDeviceId, but this phone is registered as deviceId=${registrationState.deviceId}.",
            )
        }

        var availableNamespaces = currentAvailableNamespaces()
        var matchingNamespaceTargets = findMatchingAuthenticationTargets(
            availableNamespaces = availableNamespaces,
            notificationAccountId = accountId,
            mobileDeviceId = normalizedMobileDeviceId,
            requestingDeviceId = normalizedRequestingDeviceId,
            namespaceId = requiredNamespaceId,
            namespaceUrl = namespaceUrl,
            registrationState = registrationState,
        )

        if (matchingNamespaceTargets.isEmpty()) {
            runCatching { connectedDevicesRepository.fetchConnectedDevices() }
            availableNamespaces = currentAvailableNamespaces()
            matchingNamespaceTargets = findMatchingAuthenticationTargets(
                availableNamespaces = availableNamespaces,
                notificationAccountId = accountId,
                mobileDeviceId = normalizedMobileDeviceId,
                requestingDeviceId = normalizedRequestingDeviceId,
                namespaceId = requiredNamespaceId,
                namespaceUrl = namespaceUrl,
                registrationState = registrationState,
            )
        }

        if (matchingNamespaceTargets.isEmpty()) {
            Log.d(
                REG_LOG_TAG,
                "Authentication response namespace resolution failed. notificationAccountId=$accountId notificationMobileDeviceId=$normalizedMobileDeviceId notificationDesktopDeviceId=$normalizedRequestingDeviceId notificationNamespaceId=$requiredNamespaceId notificationNamespaceUrl=${namespaceUrl.orEmpty()} availableNamespaces=${gson.toJson(availableNamespaces.map { namespaceEntry ->
                    mapOf(
                        "accountId" to namespaceEntry.accountId,
                        "desktopDeviceId" to namespaceEntry.desktopDeviceId,
                        "namespaceId" to namespaceEntry.namespaceId,
                        "namespaceUrl" to namespaceEntry.namespaceUrl,
                    )
                })}",
            )
        }

        val targetNamespaceTarget = when {
            matchingNamespaceTargets.size == 1 -> matchingNamespaceTargets.first()
            matchingNamespaceTargets.size > 1 -> {
                throw RegistrationFailure.Validation(
                    "Authentication response blocked because namespaceId=$requiredNamespaceId still matches multiple local namespaces for accountId=$accountId and mobileDeviceId=$normalizedMobileDeviceId.",
                )
            }
            else -> matchingNamespaceTargets.firstOrNull()
        }

        val resolvedTargetNamespace = targetNamespaceTarget
            ?: throw RegistrationFailure.Validation(
                "No locally stored namespace matches mobileDeviceId=$normalizedMobileDeviceId, accountId=$accountId, namespaceId=$requiredNamespaceId, namespaceUrl=${namespaceUrl.orEmpty()} and desktopDeviceId=$normalizedRequestingDeviceId for authentication response.",
            )
        val responseAccountId = resolvedTargetNamespace.accountId.trim()
        Log.d(REG_LOG_TAG, "Authentication response start. namespaceDesktopDeviceId=${resolvedTargetNamespace.desktopDeviceId} mobileDeviceId=$normalizedMobileDeviceId desktopDeviceId=$normalizedRequestingDeviceId signingDeviceId=${registrationState.deviceId} accountId=$responseAccountId approved=$approved")

        val issuedAtEpochSeconds = Instant.now().epochSecond
        val expiryEpochSeconds = issuedAtEpochSeconds + MAX_ATTESTATION_LIFETIME_SECONDS
        fun failureOutcome(message: String) = NamespaceResponseOutcome(
            namespaceEntry = resolvedTargetNamespace,
            authenticationResult = resolvedTargetNamespace.toAuthenticationResult(
                success = false,
                message = message,
            ),
        )

        val requestPayload = AuthenticationResponseRequestDto(
            notificationId = notificationId,
            accountId = responseAccountId,
            mobileDeviceId = registrationState.deviceId,
            approved = approved,
            nonce = newRequestNonce(),
            envelop = AuthenticationAttestationEnvelopeDto(
                desktopDeviceId = normalizedRequestingDeviceId,
                mobileDeviceId = registrationState.deviceId,
                namespaces = if (approved) listOf(resolvedTargetNamespace.namespaceId) else emptyList(),
                iat = issuedAtEpochSeconds,
                exp = expiryEpochSeconds,
                nonce = newRequestNonce(),
            ),
        )
        val responseUrl = buildAuthenticationResponseUrl(resolvedTargetNamespace.namespaceUrl)
        val requestBodyJson = gson.toJson(requestPayload)
        val signature = signatureService.signJsonString(requestBodyJson)
        Log.d(
            REG_LOG_TAG,
            "Authentication response namespace mapping. namespaceId=${resolvedTargetNamespace.namespaceId} namespaceUrl=${resolvedTargetNamespace.namespaceUrl} namespaceAccountId=${resolvedTargetNamespace.accountId} responseAccountId=$responseAccountId",
        )
        Log.d(REG_LOG_TAG, "HTTP POST $responseUrl payload=$requestBodyJson")
        Log.d(REG_LOG_TAG, "Authentication response signature: $signature")
        val namespaceOutcome = try {
            val response = registrationApi.sendAuthenticationResponse(
                url = responseUrl,
                signature = signature,
                request = requestPayload,
            )
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                failureOutcome(
                    errorBody ?: "Authentication response failed with HTTP ${response.code()} at $responseUrl.",
                )
            } else {
                NamespaceResponseOutcome(
                    namespaceEntry = resolvedTargetNamespace,
                    authenticationResult = resolvedTargetNamespace.toAuthenticationResult(success = true),
                    attestationId = response.body().extractAttestationId(),
                )
            }
        } catch (exception: Exception) {
            failureOutcome(mapFailureMessage(responseUrl, exception))
        }
        val namespaceOutcomes = listOf(namespaceOutcome)

        persistAttestationIds(
            existingNamespaces = availableNamespaces,
            namespaceOutcomes = namespaceOutcomes,
        )

        val namespaceResults = namespaceOutcomes.map { it.authenticationResult }

        val result = AuthenticationResponseResult(namespaceResults = namespaceResults)

        if (!result.hasAnySuccess) {
            throw RegistrationFailure.Server(
                namespaceResults.joinToString(separator = " | ") { it.message ?: "Authentication response failed." },
            )
        }

        result
    }

    private fun currentAvailableNamespaces(): List<NamespaceEntry> = (
        accountDetailsStateHolder.currentSnapshot()?.namespaces.orEmpty() +
            connectedDevicesLocalStore.currentEntries().toNamespaceEntries()
        ).distinctBy { namespaceEntry ->
            listOf(
                namespaceEntry.accountId.trim().lowercase(),
                namespaceEntry.desktopDeviceId.trim().lowercase(),
                namespaceEntry.namespaceId.trim().lowercase(),
                namespaceEntry.namespaceUrl.trim().trimEnd('/').lowercase(),
            ).joinToString(separator = "|")
        }

    private suspend fun persistAttestationIds(
        existingNamespaces: List<NamespaceEntry>,
        namespaceOutcomes: List<NamespaceResponseOutcome>,
    ) {
        val successfulOutcomes = namespaceOutcomes.filter { outcome ->
            outcome.attestationId
                ?.trim()
                ?.isNotEmpty() == true
        }

        if (successfulOutcomes.isEmpty()) {
            return
        }

        val updatedNamespaces = existingNamespaces.map { namespaceEntry ->
            val matchingOutcome = successfulOutcomes.firstOrNull { outcome ->
                outcome.namespaceEntry.storageIdentity() == namespaceEntry.storageIdentity()
            } ?: return@map namespaceEntry

            val attestationId = matchingOutcome.attestationId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@map namespaceEntry

            if (namespaceEntry.attestationId == attestationId) {
                namespaceEntry
            } else {
                namespaceEntry.copy(attestationId = attestationId)
            }
        }

        if (updatedNamespaces != existingNamespaces) {
            val currentSnapshot = accountDetailsStateHolder.currentSnapshot() ?: return
            accountDetailsStateHolder.replaceSnapshot(currentSnapshot.copy(namespaces = updatedNamespaces))
        }
    }

    private fun mapFailureMessage(
        url: String,
        exception: Exception,
    ): String = com.zerointrusion.data.repository.common.mapNetworkException(url, exception).message
        ?: "Authentication response failed at $url."

    private fun NamespaceEntry.toAuthenticationResult(
        success: Boolean,
        message: String? = null,
    ) = NamespaceAuthenticationResult(
        namespaceId = namespaceId,
        namespaceName = namespaceName,
        namespaceUrl = namespaceUrl,
        success = success,
        message = message,
    )

    private fun NamespaceEntry.storageIdentity(): String = listOf(
        accountId.trim().lowercase(),
        desktopDeviceId.trim().lowercase(),
        namespaceId.trim().lowercase(),
        namespaceUrl.trim().trimEnd('/').lowercase(),
    ).joinToString(separator = "|")

    private fun NamespaceEntry.matchesAuthenticationTarget(
        accountId: String,
        mobileDeviceId: String,
        requestingDeviceId: String,
        namespaceId: String?,
        namespaceUrl: String?,
        registrationState: RegistrationState,
    ): Boolean {
        val normalizedAccountId = accountId.trim()
        val accountMatches = this.accountId.trim() == normalizedAccountId

        if (!accountMatches) {
            return false
        }

        if (!registrationState.deviceId.equals(mobileDeviceId, ignoreCase = true)) {
            return false
        }

        if (!desktopDeviceId.equals(requestingDeviceId, ignoreCase = true)) {
            return false
        }

        val hasNamespaceId = !namespaceId.isNullOrBlank()
        val hasNamespaceUrl = !namespaceUrl.isNullOrBlank()
        if (!hasNamespaceId) {
            return false
        }

        if (!hasNamespaceUrl) {
            return this.namespaceId.trim().lowercase() == namespaceId.orEmpty().trim().lowercase()
        }

        if (this.namespaceId.trim().lowercase() != namespaceId.orEmpty().trim().lowercase()) {
            return false
        }

        if (this.namespaceUrl.trim().trimEnd('/').lowercase() != namespaceUrl.orEmpty().trim().trimEnd('/').lowercase()) {
            return false
        }

        return true
    }

    private fun findMatchingAuthenticationTargets(
        availableNamespaces: List<NamespaceEntry>,
        notificationAccountId: String,
        mobileDeviceId: String,
        requestingDeviceId: String,
        namespaceId: String,
        namespaceUrl: String?,
        registrationState: RegistrationState,
    ): List<NamespaceEntry> {
        return availableNamespaces.filter { namespaceEntry ->
            namespaceEntry.matchesAuthenticationTarget(
                accountId = notificationAccountId,
                mobileDeviceId = mobileDeviceId,
                requestingDeviceId = requestingDeviceId,
                namespaceId = namespaceId,
                namespaceUrl = namespaceUrl,
                registrationState = registrationState,
            )
        }.distinctBy { namespaceEntry ->
            namespaceEntry.storageIdentity()
        }
    }

    private fun com.zerointrusion.data.network.AuthenticationResponseDto?.extractAttestationId(): String? {
        val nestedAttestationId = this?.attestation?.attestationId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (nestedAttestationId != null) {
            return nestedAttestationId
        }

        return this?.attestationId.extractAttestationId()
    }

    private fun JsonElement?.extractAttestationId(): String? {
        val element = this ?: return null
        return when {
            element.isJsonNull -> null
            element.isJsonPrimitive -> element.asString.trim().takeIf { it.isNotEmpty() }
            element.isJsonObject -> element.asJsonObject.get("attestationId")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private data class NamespaceResponseOutcome(
        val namespaceEntry: NamespaceEntry,
        val authenticationResult: NamespaceAuthenticationResult,
        val attestationId: String? = null,
    )

    private companion object {
        const val MAX_ATTESTATION_LIFETIME_SECONDS = 7200L
    }

}