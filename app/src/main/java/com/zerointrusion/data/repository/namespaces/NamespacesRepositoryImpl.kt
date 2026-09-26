package com.zerointrusion.data.repository.namespaces

import com.google.gson.Gson
import com.zerointrusion.data.network.AccountLifecycleRequestDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.buildAccountLifecycleUrl
import com.zerointrusion.data.repository.common.buildSignedAccountMobileDeviceNonceJson
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.LocalConnectedDeviceEntry
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.data.storage.toNamespaceEntries
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.namespaces.NamespacesRepository
import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NamespacesRepositoryImpl(
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val stateStore: RegistrationStateStore,
    private val registrationApi: RegistrationApi,
    private val signatureService: SignatureService,
    private val gson: Gson,
) : NamespacesRepository {
    override fun observeNamespaces(): Flow<List<NamespaceEntry>> =
        connectedDevicesLocalStore.observeEntries().map { entries -> entries.toNamespaceEntries() }

    override suspend fun setSelectedNamespace(namespace: NamespaceEntry): RegistrationState = withContext(Dispatchers.IO) {
        val currentState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        if (
            namespace.accountId.isBlank() ||
            namespace.namespaceId.isBlank() ||
            namespace.namespaceName.isBlank() ||
            namespace.namespaceUrl.isBlank()
        ) {
            throw RegistrationFailure.Validation("The selected namespace does not contain valid account or namespace data.")
        }

        val updatedState = currentState.copy(
            accountId = namespace.accountId,
            namespaceId = namespace.namespaceId,
            namespaceName = namespace.namespaceName,
            namespaceUrl = namespace.namespaceUrl,
        )
        stateStore.save(updatedState)
        return@withContext updatedState
    }

    override suspend fun deleteNamespace(namespace: NamespaceEntry) = withContext(Dispatchers.IO) {
        val currentState = stateStore.observeState().first()
        val isGateway = currentState != null &&
            currentState.accountId == namespace.accountId &&
            currentState.namespaceId == namespace.namespaceId &&
            currentState.namespaceName == namespace.namespaceName &&
            currentState.namespaceUrl == namespace.namespaceUrl

        if (isGateway) {
            throw RegistrationFailure.Validation(
                "This namespace is the current gateway. First set another gateway in Device settings before deleting it.",
            )
        }

        TODO("Implement namespace deletion API call.")
    }

    override suspend fun validateStoredAccounts() = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: return@withContext
        val mobileDeviceId = registrationState.deviceId.trim()
        if (mobileDeviceId.isEmpty()) {
            return@withContext
        }

        val entries = connectedDevicesLocalStore.currentEntries()
        val unavailableAccounts = entries.asAccountTargets().filter { target ->
            checkAccountStatus(target, mobileDeviceId)
        }.toSet()
        if (unavailableAccounts.isNotEmpty()) {
            pruneAccounts(entries, unavailableAccounts)
        }
    }

    override suspend fun deleteAccount(namespaceUrl: String, accountId: String): Boolean = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")
        val mobileDeviceId = registrationState.deviceId.trim()
        if (mobileDeviceId.isEmpty()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid mobileDeviceId.")
        }

        val target = AccountTarget(namespaceUrl.normalizedUrl(), accountId.trim())
        if (!target.isValid()) {
            throw RegistrationFailure.Validation("The selected account does not contain a valid URL or account ID.")
        }

        val nonce = newRequestNonce()
        val request = AccountLifecycleRequestDto(
            accountId = target.accountId,
            mobileDeviceId = mobileDeviceId,
            nonce = nonce,
        )
        val signature = signatureService.signJsonString(
            buildSignedAccountMobileDeviceNonceJson(gson, target.accountId, mobileDeviceId, nonce),
        )
        val url = buildAccountLifecycleUrl(target.namespaceUrl)
        val response = try {
            registrationApi.deleteAccount(url, signature, request)
        } catch (exception: Exception) {
            throw mapNetworkException(url, exception)
        }
        if (!response.isSuccessful) {
            throw RegistrationFailure.Server(
                response.errorBody()?.string() ?: "Request failed with HTTP ${response.code()} at $url.",
            )
        }

        val remainingEntries = pruneAccounts(connectedDevicesLocalStore.currentEntries(), setOf(target))
        return@withContext remainingEntries.asAccountTargets().isEmpty()
    }

    private suspend fun checkAccountStatus(target: AccountTarget, mobileDeviceId: String): Boolean {
        val nonce = newRequestNonce()
        val request = AccountLifecycleRequestDto(
            accountId = target.accountId,
            mobileDeviceId = mobileDeviceId,
            nonce = nonce,
        )
        val signature = signatureService.signJsonString(
            buildSignedAccountMobileDeviceNonceJson(gson, target.accountId, mobileDeviceId, nonce),
        )
        val url = buildAccountLifecycleUrl(target.namespaceUrl, status = true)
        val response = try {
            registrationApi.getAccountStatus(url, signature, request)
        } catch (_: Exception) {
            return false
        }
        return response.code() == 404 || response.code() == 410
    }

    private suspend fun pruneAccounts(
        entries: List<LocalConnectedDeviceEntry>,
        accounts: Set<AccountTarget>,
    ): List<LocalConnectedDeviceEntry> {
        val remainingEntries = entries.mapNotNull { entry ->
            val remainingNamespaces = entry.namespaces.filterNot { namespace ->
                AccountTarget(namespace.url.normalizedUrl(), namespace.accountId.trim()) in accounts
            }
            entry.takeIf { remainingNamespaces.isNotEmpty() }?.copy(namespaces = remainingNamespaces)
        }
        connectedDevicesLocalStore.replaceEntries(remainingEntries)
        replaceGatewayIfRemoved(accounts, remainingEntries.toNamespaceEntries())
        return remainingEntries
    }

    private suspend fun replaceGatewayIfRemoved(
        removedAccounts: Set<AccountTarget>,
        remainingNamespaces: List<NamespaceEntry>,
    ) {
        val state = stateStore.observeState().first() ?: return
        val gateway = AccountTarget(state.namespaceUrl.normalizedUrl(), state.accountId.trim())
        if (gateway !in removedAccounts) {
            return
        }
        val replacement = remainingNamespaces.firstOrNull() ?: return
        stateStore.save(
            state.copy(
                accountId = replacement.accountId,
                namespaceId = replacement.namespaceId,
                namespaceName = replacement.namespaceName,
                namespaceUrl = replacement.namespaceUrl,
            ),
        )
    }

    private data class AccountTarget(val namespaceUrl: String, val accountId: String) {
        fun isValid(): Boolean = namespaceUrl.isNotEmpty() && accountId.isNotEmpty()
    }

    private fun List<LocalConnectedDeviceEntry>.asAccountTargets(): List<AccountTarget> = flatMap { entry ->
        entry.namespaces.map { namespace ->
            AccountTarget(namespace.url.normalizedUrl(), namespace.accountId.trim())
        }
    }.filter { it.isValid() }.distinct()

    private fun String.normalizedUrl(): String = trim().trimEnd('/')
}
