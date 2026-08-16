package com.zerointrusion.data.repository.namespaces

import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.namespaces.NamespacesRepository
import kotlinx.coroutines.flow.Flow

class NamespacesRepositoryImpl(
    private val namespaceStore: NamespaceStore,
) : NamespacesRepository {
    override fun observeNamespaces(): Flow<List<NamespaceEntry>> = namespaceStore.observeNamespaces()
}