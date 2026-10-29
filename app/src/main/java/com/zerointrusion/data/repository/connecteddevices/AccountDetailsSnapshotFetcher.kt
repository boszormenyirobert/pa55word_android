package com.zerointrusion.data.repository.connecteddevices

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.AccountApi
import com.zerointrusion.data.repository.common.ACCOUNT_LOG_TAG
import com.zerointrusion.data.repository.common.buildConnectedDevicesUrl
import com.zerointrusion.data.repository.common.buildSignedAccountMobileDeviceNonceJson
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.data.storage.removeAccountConnections
import com.zerointrusion.data.storage.toNamespaceEntries
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.AccountDetailsSnapshot
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.DatabaseAvailability
import com.zerointrusion.domain.registration.RegistrationState
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AccountDetailsSnapshotFetcher
@Inject
constructor(
    private val accountApi: AccountApi,
    private val stateStore: RegistrationStateStore,
    private val accountDetailsStateHolder: AccountDetailsStateHolder,
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val dekService: DekService,
    private val signatureService: SignatureService,
    private val gson: Gson,
) {
    private val connectedDevicesResponseParser = ConnectedDevicesResponseParser(gson)
    private val snapshotMerger = AccountDetailsSnapshotMerger()

    internal suspend fun fetchSnapshot(): AccountDetailsSnapshot =
        withContext(Dispatchers.IO) {
            val registrationState =
                stateStore.observeState().first()
                    ?: throw RegistrationFailure.Validation(
                        "No registered account is stored on this device."
                    )

            if (registrationState.accountId.isBlank()) {
                throw RegistrationFailure.Validation(
                    "The stored registration state does not contain a valid accountId."
                )
            }
            if (registrationState.deviceId.isBlank()) {
                throw RegistrationFailure.Validation(
                    "The stored registration state does not contain a valid mobileDeviceId."
                )
            }
            registrationState.encDek
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { encryptedDek ->
                    try {
                        dekService.decryptDek(encryptedDek)
                    } catch (_: RegistrationFailure.Security) {
                        connectedDevicesLocalStore.replaceMobileRegistrations(emptyList())
                        stateStore.clear()
                        throw RegistrationFailure.Security(
                            "The locally stored registration DEK can no longer be decrypted with the current device private key. The Android Keystore keypair on this phone no longer matches the public key registered on the backend. Local registration was cleared. Re-register this phone."
                        )
                    }
                }

            // The in-memory snapshot resets on process restart; merge in persisted local entries
            // too,
            // otherwise previously-paired second servers are never re-queried after a cold start.
            val existingNamespaces =
                (accountDetailsStateHolder.currentSnapshot()?.namespaces.orEmpty() +
                        connectedDevicesLocalStore.currentEntries().toNamespaceEntries())
                    .distinctBy { namespaceEntry ->
                        listOf(
                                namespaceEntry.accountId.trim().lowercase(),
                                namespaceEntry.desktopDeviceId.trim().lowercase(),
                                namespaceEntry.namespaceId.trim().lowercase(),
                                namespaceEntry.namespaceUrl.trim().trimEnd('/').lowercase(),
                            )
                            .joinToString(separator = "|")
                    }
            val homeTarget =
                AccountDetailsFetchTarget(
                    namespaceUrl = registrationState.namespaceUrl.trim().trimEnd('/'),
                    accountId = registrationState.accountId.trim(),
                )
            val mobileRegistrations =
                connectedDevicesLocalStore.currentMobileRegistrations().filter {
                    it.mobileDeviceId == registrationState.deviceId
                }
            val mobileTargets =
                mobileRegistrations.map { AccountDetailsFetchTarget(it.namespaceUrl, it.accountId) }
            val pendingTargets =
                ArrayDeque(
                    (snapshotMerger.initialTargets(homeTarget, existingNamespaces) + mobileTargets)
                        .distinctBy { it.storageIdentity() }
                )
            val fetchedTargetKeys = mutableSetOf<String>()
            val snapshots = mutableListOf<AccountDetailsSnapshot>()
            val removedTargets = mutableSetOf<String>()
            val targetFailures = mutableListOf<Exception>()
            val availabilityByTarget = linkedMapOf<String, DatabaseAvailability>()

            fun publishAvailability() {
                accountDetailsStateHolder.replaceDatabaseAvailability(
                    availabilityByTarget.values.sortedBy { availability ->
                        availability.namespaceName.lowercase()
                    }
                )
            }

            fun recordAvailability(target: AccountDetailsFetchTarget, isAvailable: Boolean) {
                val namespace =
                    (snapshots.flatMap { it.namespaces } + existingNamespaces).firstOrNull {
                        existing ->
                        existing.namespaceName.isNotBlank() &&
                            AccountDetailsFetchTarget(existing.namespaceUrl, existing.accountId)
                                .storageIdentity() == target.storageIdentity()
                    }
                val mobileRegistration =
                    (snapshots.flatMap { it.mobileRegistrations } + mobileRegistrations)
                        .firstOrNull { registration ->
                            registration.namespaceName.isNotBlank() &&
                                AccountDetailsFetchTarget(
                                        registration.namespaceUrl,
                                        registration.accountId,
                                    )
                                    .storageIdentity() == target.storageIdentity()
                        }
                val namespaceName =
                    namespace?.namespaceName?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: mobileRegistration?.namespaceName?.trim()
                        ?: registrationState.namespaceName.takeIf {
                            target.storageIdentity() == homeTarget.storageIdentity()
                        }
                        ?: target.namespaceUrl
                availabilityByTarget[target.storageIdentity()] =
                    DatabaseAvailability(
                        accountId = target.accountId,
                        namespaceName = namespaceName,
                        namespaceUrl = target.namespaceUrl,
                        isAvailable = isAvailable,
                    )
                publishAvailability()
            }

            while (pendingTargets.isNotEmpty()) {
                val target = pendingTargets.removeFirst()
                if (!fetchedTargetKeys.add(target.storageIdentity())) {
                    continue
                }

                val snapshot =
                    try {
                        val outcome =
                            withTimeoutOrNull(2_000L) {
                                Result.success(
                                    fetchSnapshotForTarget(
                                        registrationState = registrationState,
                                        target = target,
                                    )
                                )
                            }
                                ?: throw RegistrationFailure.Network(
                                    "ACCOUNT_REFRESH_TIMEOUT: Account server did not respond within two seconds."
                                )
                        outcome.getOrThrow()
                    } catch (exception: kotlinx.coroutines.CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        targetFailures += exception
                        recordAvailability(target, isAvailable = false)
                        Log.w(
                            ACCOUNT_LOG_TAG,
                            "ACCOUNT details fetch skipped unavailable server. namespaceUrl=${target.namespaceUrl} accountId=${target.accountId}",
                            exception,
                        )
                        continue
                    }
                if (snapshot != null) {
                    snapshots += snapshot
                }
                recordAvailability(target, isAvailable = true)
                if (snapshot == null) {
                    removedTargets += target.storageIdentity()
                    availabilityByTarget.remove(target.storageIdentity())
                    publishAvailability()
                    pruneRemovedTarget(target)
                    continue
                }
                snapshotMerger
                    .discoveredTargets(target, snapshot.namespaces)
                    .filterNot { discoveredTarget ->
                        fetchedTargetKeys.contains(discoveredTarget.storageIdentity())
                    }
                    .forEach { discoveredTarget -> pendingTargets.addLast(discoveredTarget) }
            }

            if (snapshots.isEmpty() && targetFailures.isNotEmpty()) {
                throw targetFailures.first()
            }

            val merged =
                snapshotMerger
                    .mergeSnapshots(snapshots, existingNamespaces)
                    .withoutAccounts(removedTargets)
            if (homeTarget.storageIdentity() in removedTargets) {
                val replacement =
                    merged.mobileRegistrations.firstOrNull {
                        it.mobileDeviceId == registrationState.deviceId && it.encDek.isNotBlank()
                    }
                if (replacement == null) {
                    stateStore.clear()
                } else {
                    stateStore.save(
                        registrationState.copy(
                            accountId = replacement.accountId,
                            namespaceId = replacement.namespaceId,
                            namespaceName = replacement.namespaceName,
                            namespaceUrl = replacement.namespaceUrl,
                            encDek = replacement.encDek,
                        )
                    )
                }
            }
            merged
        }

    internal suspend fun refreshAvailability(database: DatabaseAvailability) =
        withContext(Dispatchers.IO) {
            val registrationState = stateStore.observeState().first() ?: return@withContext
            val target = AccountDetailsFetchTarget(database.namespaceUrl, database.accountId)
            val isAvailable =
                try {
                    fetchSnapshotForTarget(registrationState, target)
                    true
                } catch (exception: kotlinx.coroutines.CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.w(
                        ACCOUNT_LOG_TAG,
                        "ACCOUNT availability refresh failed. namespaceUrl=${target.namespaceUrl} accountId=${target.accountId}",
                        exception,
                    )
                    false
                }
            accountDetailsStateHolder.replaceDatabaseAvailability(
                accountDetailsStateHolder.observeDatabaseAvailability().first().map { availability
                    ->
                    if (
                        availability.namespaceUrl
                            .trim()
                            .trimEnd('/')
                            .equals(target.namespaceUrl, ignoreCase = true) &&
                            availability.accountId
                                .trim()
                                .equals(target.accountId, ignoreCase = true)
                    ) {
                        availability.copy(isAvailable = isAvailable)
                    } else {
                        availability
                    }
                }
            )
        }

    private suspend fun pruneRemovedTarget(target: AccountDetailsFetchTarget) {
        val removed = setOf(target.storageIdentity())
        connectedDevicesLocalStore.removeAccountConnections { url, accountId ->
            AccountDetailsFetchTarget(url, accountId).storageIdentity() in removed
        }
        accountDetailsStateHolder.currentSnapshot()?.let { snapshot ->
            accountDetailsStateHolder.replaceSnapshot(snapshot.withoutAccounts(removed))
        }
        Log.w(
            ACCOUNT_LOG_TAG,
            "Removed deregistered mobile connection. namespaceUrl=${target.namespaceUrl} accountId=${target.accountId}",
        )
    }

    private suspend fun fetchSnapshotForTarget(
        registrationState: RegistrationState,
        target: AccountDetailsFetchTarget,
    ): AccountDetailsSnapshot? {
        val nonce = newRequestNonce()

        val requestUrl =
            buildConnectedDevicesUrl(
                baseUrl = target.namespaceUrl,
                accountId = target.accountId,
                mobileDeviceId = registrationState.deviceId,
                nonce = nonce,
            )
        val signaturePayloadJson =
            buildSignedAccountMobileDeviceNonceJson(
                gson = gson,
                accountId = target.accountId,
                mobileDeviceId = registrationState.deviceId,
                nonce = nonce,
            )
        val signature = signatureService.signJsonString(signaturePayloadJson)

        Log.d(ACCOUNT_LOG_TAG, "ACCOUNT details query submitted.")
        val response =
            try {
                accountApi.getAccountDetails(url = requestUrl, signature = signature)
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) {
                    throw exception
                }
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
            if (isRemovedAccountRegistration(response.code(), errorBody, gson)) {
                return null
            }
            throw RegistrationFailure.Server(
                errorBody ?: "Request failed with HTTP ${response.code()} at $requestUrl."
            )
        }

        val body = response.body()?.string()?.trim().orEmpty()
        if (body.isEmpty()) {
            throw RegistrationFailure.Server(
                "The backend returned an empty account response from $requestUrl."
            )
        }

        Log.d(ACCOUNT_LOG_TAG, "ACCOUNT details response received.")
        val parsedResponse = connectedDevicesResponseParser.parse(body, registrationState.deviceId)
        Log.d(
            ACCOUNT_LOG_TAG,
            "ACCOUNT details parsed. namespaceCount=${parsedResponse.namespaces.size}",
        )
        return AccountDetailsSnapshot(
            groups = parsedResponse.groups,
            namespaces = parsedResponse.namespaces,
            mobileRegistrations = parsedResponse.mobileRegistrations,
        )
    }

    fun summarizeGroups(
        groups: List<com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup>
    ): String = connectedDevicesResponseParser.summarize(groups)
}
