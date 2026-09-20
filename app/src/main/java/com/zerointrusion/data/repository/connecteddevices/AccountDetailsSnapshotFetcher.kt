package com.zerointrusion.data.repository.connecteddevices

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.ACCOUNT_LOG_TAG
import com.zerointrusion.data.repository.common.buildConnectedDevicesUrl
import com.zerointrusion.data.repository.common.buildSignedAccountMobileDeviceNonceJson
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.repository.common.resolveNamespaceEndpointTargets
import com.zerointrusion.data.repository.connecteddevices.AccountDetailsStateHolder
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.data.storage.toNamespaceEntries
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.AccountDetailsSnapshot
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.registration.RegistrationState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

@Singleton
class AccountDetailsSnapshotFetcher @Inject constructor(
    private val registrationApi: RegistrationApi,
    private val stateStore: RegistrationStateStore,
    private val accountDetailsStateHolder: AccountDetailsStateHolder,
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val dekService: DekService,
    private val signatureService: SignatureService,
    private val gson: Gson,
) {
    private val connectedDevicesResponseParser = ConnectedDevicesResponseParser(gson)

    internal suspend fun fetchSnapshot(): AccountDetailsSnapshot = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        if (registrationState.accountId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid accountId.")
        }
        if (registrationState.deviceId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid mobileDeviceId.")
        }
        registrationState.encDek?.trim()?.takeIf { it.isNotEmpty() }?.let { encryptedDek ->
            try {
                dekService.decryptDek(encryptedDek)
            } catch (_: RegistrationFailure.Security) {
                stateStore.clear()
                throw RegistrationFailure.Security(
                    "The locally stored registration DEK can no longer be decrypted with the current device private key. The Android Keystore keypair on this phone no longer matches the public key registered on the backend. Local registration was cleared. Re-register this phone.",
                )
            }
        }

        // The in-memory snapshot resets on process restart; merge in persisted local entries too,
        // otherwise previously-paired second servers are never re-queried after a cold start.
        val existingNamespaces = (
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
        val homeTarget = AccountDetailsFetchTarget(
            namespaceUrl = registrationState.namespaceUrl.trim().trimEnd('/'),
            accountId = registrationState.accountId.trim(),
        )
        val pendingTargets = ArrayDeque(buildInitialFetchTargets(homeTarget, existingNamespaces))
        val fetchedTargetKeys = mutableSetOf<String>()
        val snapshots = mutableListOf<AccountDetailsSnapshot>()

        while (pendingTargets.isNotEmpty()) {
            val target = pendingTargets.removeFirst()
            if (!fetchedTargetKeys.add(target.storageIdentity())) {
                continue
            }

            val snapshot = fetchSnapshotForTarget(registrationState, target)
            snapshots += snapshot

            buildDiscoveredFetchTargets(target, snapshot.namespaces)
                .filterNot { discoveredTarget -> fetchedTargetKeys.contains(discoveredTarget.storageIdentity()) }
                .forEach { discoveredTarget -> pendingTargets.addLast(discoveredTarget) }
        }

        val mergedGroups = snapshots
            .flatMap { snapshot -> snapshot.groups }
            .distinctBy { group -> group.accountId.trim().lowercase() + "|" + group.deviceId.trim().lowercase() }
            .sortedWith(compareBy({ it.accountId.lowercase() }, { it.deviceId.lowercase() }))

        val mergedNamespaces = snapshots
            .flatMap { snapshot -> snapshot.namespaces }
            .map { namespace -> namespace.withCarriedForwardAttestationId(existingNamespaces) }
            .distinctBy { namespace ->
                listOf(
                    namespace.accountId.trim().lowercase(),
                    namespace.desktopDeviceId.trim().lowercase(),
                    namespace.namespaceId.trim().lowercase(),
                    namespace.namespaceUrl.trim().trimEnd('/').lowercase(),
                ).joinToString(separator = "|")
            }
            .sortedWith(compareBy({ it.namespaceName.lowercase() }, { it.desktopDeviceId.lowercase() }))

        AccountDetailsSnapshot(
            groups = mergedGroups,
            namespaces = mergedNamespaces,
        )
    }

    // The backend never returns attestationId per namespace; carry forward the value this phone last
    // recorded locally when approving that exact device's login, so it survives snapshot refreshes and
    // can later disambiguate active sessions that share a namespace.
    private fun NamespaceEntry.withCarriedForwardAttestationId(existingNamespaces: List<NamespaceEntry>): NamespaceEntry {
        if (!attestationId.isNullOrBlank()) {
            return this
        }
        val previousAttestationId = existingNamespaces.firstOrNull { existing ->
            existing.accountId.trim().equals(accountId.trim(), ignoreCase = true) &&
                existing.desktopDeviceId.trim().equals(desktopDeviceId.trim(), ignoreCase = true) &&
                existing.namespaceId.trim().equals(namespaceId.trim(), ignoreCase = true) &&
                existing.namespaceUrl.trim().trimEnd('/').equals(namespaceUrl.trim().trimEnd('/'), ignoreCase = true)
        }?.attestationId?.trim()?.takeIf { it.isNotEmpty() } ?: return this
        return copy(attestationId = previousAttestationId)
    }

    private suspend fun fetchSnapshotForTarget(
        registrationState: RegistrationState,
        target: AccountDetailsFetchTarget,
    ): AccountDetailsSnapshot {
        val nonce = newRequestNonce()

        val requestUrl = buildConnectedDevicesUrl(
            baseUrl = target.namespaceUrl,
            accountId = target.accountId,
            mobileDeviceId = registrationState.deviceId,
            nonce = nonce,
        )
        val signaturePayloadJson = buildSignedAccountMobileDeviceNonceJson(
            gson = gson,
            accountId = target.accountId,
            mobileDeviceId = registrationState.deviceId,
            nonce = nonce,
        )
        val signature = signatureService.signJsonString(signaturePayloadJson)

        Log.d(
            ACCOUNT_LOG_TAG,
            "ACCOUNT details fetch start. namespaceUrl=${target.namespaceUrl} accountId=${target.accountId} url=$requestUrl",
        )
        Log.d(ACCOUNT_LOG_TAG, "HTTP GET $requestUrl payload=$signaturePayloadJson")
        val response = try {
            registrationApi.getAccountDetails(
                url = requestUrl,
                signature = signature,
            )
        } catch (exception: Exception) {
            Log.e(
                ACCOUNT_LOG_TAG,
                "ACCOUNT details fetch failed before HTTP response. namespaceUrl=${target.namespaceUrl} accountId=${target.accountId} url=$requestUrl",
                exception,
            )
            throw mapNetworkException(requestUrl, exception)
        }

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            Log.e(
                ACCOUNT_LOG_TAG,
                "ACCOUNT details fetch failed. http=${response.code()} namespaceUrl=${target.namespaceUrl} accountId=${target.accountId} url=$requestUrl",
            )
            throw RegistrationFailure.Server(
                errorBody ?: "Request failed with HTTP ${response.code()} at $requestUrl.",
            )
        }

        val body = response.body()?.string()?.trim().orEmpty()
        if (body.isEmpty()) {
            throw RegistrationFailure.Server("The backend returned an empty account response from $requestUrl.")
        }

        Log.d(
            ACCOUNT_LOG_TAG,
            "ACCOUNT details raw response body. namespaceUrl=${target.namespaceUrl} accountId=${target.accountId} url=$requestUrl body=$body",
        )
        val parsedResponse = connectedDevicesResponseParser.parse(body)
        Log.d(
            ACCOUNT_LOG_TAG,
            "ACCOUNT details raw response. namespaceUrl=${target.namespaceUrl} accountId=${target.accountId} url=$requestUrl namespaces=${gson.toJson(parsedResponse.namespaces)} body=$body",
        )
        return AccountDetailsSnapshot(
            groups = parsedResponse.groups,
            namespaces = parsedResponse.namespaces,
        )
    }

    private fun buildInitialFetchTargets(
        homeTarget: AccountDetailsFetchTarget,
        existingNamespaces: List<NamespaceEntry>,
    ): List<AccountDetailsFetchTarget> = (
        buildDiscoveredFetchTargets(homeTarget, existingNamespaces) + homeTarget
        ).distinctBy { target -> target.storageIdentity() }

    private fun buildDiscoveredFetchTargets(
        currentTarget: AccountDetailsFetchTarget,
        namespaces: List<NamespaceEntry>,
    ): List<AccountDetailsFetchTarget> = resolveNamespaceEndpointTargets(
        namespaces = namespaces,
        referenceNamespaceUrl = currentTarget.namespaceUrl,
        referenceAccountId = currentTarget.accountId,
    ).map { target ->
        AccountDetailsFetchTarget(
            namespaceUrl = target.namespaceUrl,
            accountId = target.accountId,
        )
    }
            .distinctBy { target -> target.storageIdentity() }

    private data class AccountDetailsFetchTarget(
        val namespaceUrl: String,
        val accountId: String,
    ) {
        fun storageIdentity(): String = namespaceUrl.lowercase() + "|" + accountId.lowercase()
    }

    fun summarizeGroups(groups: List<com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup>): String =
        connectedDevicesResponseParser.summarize(groups)
}