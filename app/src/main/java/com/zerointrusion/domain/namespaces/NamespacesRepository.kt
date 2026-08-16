package com.zerointrusion.domain.namespaces

import kotlinx.coroutines.flow.Flow

interface NamespacesRepository {
    fun observeNamespaces(): Flow<List<NamespaceEntry>>
}