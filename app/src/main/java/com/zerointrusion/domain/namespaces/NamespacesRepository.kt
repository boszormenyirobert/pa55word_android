package com.zerointrusion.domain.namespaces

import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.flow.Flow

interface NamespacesRepository {
    fun observeNamespaces(): Flow<List<NamespaceEntry>>

    suspend fun setSelectedNamespace(namespace: NamespaceEntry): RegistrationState

    suspend fun deleteNamespace(namespace: NamespaceEntry)

    suspend fun validateStoredAccounts()

    suspend fun deleteAccount(namespaceUrl: String, accountId: String): Boolean
}