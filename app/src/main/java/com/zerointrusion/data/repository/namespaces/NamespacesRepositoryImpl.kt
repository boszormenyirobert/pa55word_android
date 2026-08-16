package com.zerointrusion.data.repository.namespaces

import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.namespaces.NamespacesRepository
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class NamespacesRepositoryImpl(
    private val namespaceStore: NamespaceStore,
    private val stateStore: RegistrationStateStore,
) : NamespacesRepository {
    override fun observeNamespaces(): Flow<List<NamespaceEntry>> = namespaceStore.observeNamespaces()

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
        updatedState
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

        val updatedNamespaces = namespaceStore.observeNamespaces().first().filterNot { storedNamespace ->
            storedNamespace.accountId == namespace.accountId &&
                storedNamespace.namespaceId == namespace.namespaceId &&
                storedNamespace.namespaceName == namespace.namespaceName &&
                storedNamespace.namespaceUrl == namespace.namespaceUrl &&
                storedNamespace.deviceId == namespace.deviceId
        }
        namespaceStore.saveNamespaces(updatedNamespaces)
    }
}