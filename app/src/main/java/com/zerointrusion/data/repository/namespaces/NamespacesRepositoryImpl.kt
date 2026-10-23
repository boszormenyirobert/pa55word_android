package com.zerointrusion.data.repository.namespaces

import com.google.gson.Gson
import com.zerointrusion.data.network.AccountLifecycleRequestDto
import com.zerointrusion.data.network.AccountApi
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.buildAccountLifecycleUrl
import com.zerointrusion.data.repository.common.buildSignedAccountMobileDeviceNonceJson
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.repository.registration.MobileNamespaceRegistrationHandler
import com.zerointrusion.data.repository.registration.RegistrationServerHealthChecker
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.NamespaceService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.LocalConnectedDeviceEntry
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.data.storage.toNamespaceEntries
import com.zerointrusion.data.storage.removeAccountConnections
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.namespaces.NamespacesRepository
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NamespacesRepositoryImpl(
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val stateStore: RegistrationStateStore,
    private val accountApi: AccountApi,
    private val registrationApi: RegistrationApi,
    private val signatureService: SignatureService,
    private val gson: Gson,
    keyStoreService: KeyStoreService,
    private val namespaceService: NamespaceService,
    private val validator: RegistrationValidator,
) : NamespacesRepository {
    private val additionMutex = Mutex()
    private val healthChecker = RegistrationServerHealthChecker(registrationApi)
    private val mobileRegistrationHandler = MobileNamespaceRegistrationHandler(
        registrationApi, keyStoreService, connectedDevicesLocalStore, gson, validator,
    )

    override fun observeNamespaces(): Flow<List<NamespaceEntry>> =
        connectedDevicesLocalStore.observeEntries().map { entries -> entries.toNamespaceEntries() }

    override fun observeAccounts(): Flow<List<NamespaceEntry>> = combine(
        connectedDevicesLocalStore.observeEntries(),
        connectedDevicesLocalStore.observeMobileRegistrations(),
        stateStore.observeState(),
    ) { entries, registrations, state ->
        val mobileNamespaces = registrations
            .filter { it.mobileDeviceId == state?.mobileDeviceId }
            .map { it.toNamespaceEntry() }
        val gateway = state?.let {
            NamespaceEntry(it.namespaceName, it.namespaceId, it.namespaceUrl, it.accountId, "", it.root)
        }
        (entries.toNamespaceEntries() + mobileNamespaces + listOfNotNull(gateway))
            .distinctBy { it.namespaceUrl.normalizedUrl().lowercase() + "|" + it.accountId }
    }

    override suspend fun addDatabase(name: String, url: String) = withContext(Dispatchers.IO) {
        additionMutex.withLock {
            val validation = validator.validateNamespaceForm(name, url)
            if (!validation.isValid) {
                throw RegistrationFailure.Validation(
                    validation.namespaceNameError ?: validation.namespaceUrlError ?: "Invalid namespace.",
                )
            }
            val state = stateStore.observeState().first()
                ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")
            if (state.accountId.isBlank() || state.mobileDeviceId.isBlank()) {
                throw RegistrationFailure.Validation("The stored registration does not contain valid account and mobile device IDs.")
            }
            val encDek = state.encDek?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw RegistrationFailure.Validation("The stored registration does not contain an encrypted DEK.")
            val normalizedUrl = url.normalizedUrl()
            val alreadyRegistered = state.namespaceUrl.normalizedUrl().equals(normalizedUrl, ignoreCase = true) ||
                connectedDevicesLocalStore.currentMobileRegistrations().any {
                    it.mobileDeviceId == state.mobileDeviceId &&
                        it.namespaceUrl.normalizedUrl().equals(normalizedUrl, ignoreCase = true)
                } ||
                connectedDevicesLocalStore.currentEntries().any { entry ->
                    entry.url.normalizedUrl().equals(normalizedUrl, ignoreCase = true) ||
                        entry.namespaces.any { it.url.normalizedUrl().equals(normalizedUrl, ignoreCase = true) }
                }
            if (alreadyRegistered) {
                throw RegistrationFailure.Validation("This mobile device is already registered at this URL.")
            }
            healthChecker.requireReachableServer(normalizedUrl)
            val namespace = namespaceService.createNamespace(name, normalizedUrl)
            mobileRegistrationHandler.register(
                state = state,
                namespaceId = namespace.namespaceId,
                namespaceName = namespace.name,
                namespaceUrl = namespace.url,
                storedEncDek = encDek,
            )
            Unit
        }
    }

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
        val unavailableAccounts = accountTargets(entries).filter { target ->
            checkAccountStatus(target, mobileDeviceId)
        }.toSet()
        if (unavailableAccounts.isNotEmpty()) {
            pruneAccounts(unavailableAccounts, replaceGateway = false)
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
            accountApi.deleteAccount(url, signature, request)
        } catch (exception: Exception) {
            throw mapNetworkException(url, exception)
        }
        if (!response.isSuccessful) {
            throw RegistrationFailure.Server(
                response.errorBody()?.string() ?: "Request failed with HTTP ${response.code()} at $url.",
            )
        }

        val remainingEntries = pruneAccounts(setOf(target))
        return@withContext accountTargets(remainingEntries).isEmpty() && stateStore.observeState().first() == null
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
            accountApi.getAccountStatus(url, signature, request)
        } catch (_: Exception) {
            return false
        }
        return response.code() == 404 || response.code() == 410
    }

    private suspend fun pruneAccounts(
        accounts: Set<AccountTarget>,
        replaceGateway: Boolean = true,
    ): List<LocalConnectedDeviceEntry> {
        val remainingEntries = connectedDevicesLocalStore.removeAccountConnections { url, accountId ->
            AccountTarget(url.normalizedUrl(), accountId.trim()) in accounts
        }
        if (replaceGateway) {
            replaceGatewayIfRemoved(
                accounts,
                connectedDevicesLocalStore.currentMobileRegistrations().map { it.toNamespaceEntry() } +
                    remainingEntries.toNamespaceEntries(),
            )
        }
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
        val replacement = remainingNamespaces.firstOrNull()
        if (replacement == null) {
            stateStore.clear()
            return
        }
        val replacementRegistration = connectedDevicesLocalStore.currentMobileRegistrations().firstOrNull {
            it.mobileDeviceId == state.mobileDeviceId &&
                it.accountId == replacement.accountId &&
                it.namespaceUrl.normalizedUrl() == replacement.namespaceUrl.normalizedUrl()
        }
        stateStore.save(
            state.copy(
                accountId = replacement.accountId,
                namespaceId = replacement.namespaceId,
                namespaceName = replacement.namespaceName,
                namespaceUrl = replacement.namespaceUrl,
                encDek = replacementRegistration?.encDek ?: state.encDek,
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

    private fun accountTargets(entries: List<LocalConnectedDeviceEntry>): List<AccountTarget> =
        (entries.asAccountTargets() + connectedDevicesLocalStore.currentMobileRegistrations().map {
            AccountTarget(it.namespaceUrl.normalizedUrl(), it.accountId.trim())
        }).filter { it.isValid() }.distinct()

    private fun MobileNamespaceRegistration.toNamespaceEntry(): NamespaceEntry =
        NamespaceEntry(namespaceName, namespaceId, namespaceUrl, accountId, "", root)
}
