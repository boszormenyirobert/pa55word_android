package com.zerointrusion.domain.namespaces

import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.flow.Flow

interface NamespacesRepository {
    fun observeNamespaces(): Flow<List<NamespaceEntry>>

    fun observeAccounts(): Flow<List<NamespaceEntry>>

    suspend fun addDatabase(name: String, url: String)

    suspend fun setSelectedNamespace(namespace: NamespaceEntry): RegistrationState

    suspend fun deleteNamespace(namespace: NamespaceEntry)

    suspend fun validateStoredAccounts()

    suspend fun deleteAccount(namespaceUrl: String, accountId: String): Boolean
}