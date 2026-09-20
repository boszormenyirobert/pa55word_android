package com.zerointrusion.data.repository.common

import com.zerointrusion.domain.namespaces.NamespaceEntry

internal data class NamespaceEndpointTarget(
    val namespaceId: String,
    val namespaceName: String,
    val namespaceUrl: String,
    val accountId: String,
    val entries: List<NamespaceEntry>,
) {
    fun storageIdentity(): String = listOf(
        namespaceId.trim().lowercase(),
        namespaceUrl.trim().trimEnd('/').lowercase(),
        accountId.trim().lowercase(),
    ).joinToString(separator = "|")
}

internal fun resolveNamespaceEndpointTargets(
    namespaces: List<NamespaceEntry>,
    referenceNamespaceUrl: String,
    referenceAccountId: String,
): List<NamespaceEndpointTarget> = namespaces
    .groupBy { namespaceEntry ->
        namespaceEntry.namespaceId.trim().lowercase() + "|" +
            namespaceEntry.namespaceUrl.trim().trimEnd('/').lowercase()
    }
    .values
    .mapNotNull { entries ->
        val representative = entries.firstOrNull() ?: return@mapNotNull null
        val normalizedNamespaceUrl = representative.namespaceUrl.trim().trimEnd('/')
        val resolvedAccountId = resolveNamespaceTargetAccountId(
            namespaceEntries = entries,
            referenceNamespaceUrl = referenceNamespaceUrl,
            referenceAccountId = referenceAccountId,
        )

        if (normalizedNamespaceUrl.isEmpty() || resolvedAccountId.isEmpty()) {
            null
        } else {
            NamespaceEndpointTarget(
                namespaceId = representative.namespaceId,
                namespaceName = representative.namespaceName,
                namespaceUrl = normalizedNamespaceUrl,
                accountId = resolvedAccountId,
                entries = entries,
            )
        }
    }

internal fun resolveNamespaceTargetAccountId(
    namespaceEntries: List<NamespaceEntry>,
    referenceNamespaceUrl: String,
    referenceAccountId: String,
): String {
    val normalizedReferenceUrl = referenceNamespaceUrl.trim().trimEnd('/')
    val normalizedReferenceAccountId = referenceAccountId.trim()
    val normalizedNamespaceUrl = namespaceEntries.firstOrNull()?.namespaceUrl?.trim()?.trimEnd('/').orEmpty()

    if (normalizedNamespaceUrl.equals(normalizedReferenceUrl, ignoreCase = true)) {
        return normalizedReferenceAccountId
    }

    val localCandidates = namespaceEntries.mapNotNull { namespaceEntry ->
        namespaceEntry.accountId.trim().takeIf { it.isNotEmpty() }
    }.distinctBy { it.lowercase() }

    if (localCandidates.size == 1) {
        return localCandidates.first()
    }

    return localCandidates.firstOrNull() ?: normalizedReferenceAccountId
}