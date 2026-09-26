package com.zerointrusion.data.repository.connecteddevices

import com.zerointrusion.data.repository.common.resolveNamespaceEndpointTargets
import com.zerointrusion.domain.connecteddevices.AccountDetailsSnapshot
import com.zerointrusion.domain.namespaces.NamespaceEntry

internal class AccountDetailsSnapshotMerger {
    fun initialTargets(
        homeTarget: AccountDetailsFetchTarget,
        existingNamespaces: List<NamespaceEntry>,
    ): List<AccountDetailsFetchTarget> =
        (discoveredTargets(homeTarget, existingNamespaces) + homeTarget)
            .distinctBy { target -> target.storageIdentity() }

    fun discoveredTargets(
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
    }.distinctBy { target -> target.storageIdentity() }

    fun mergeSnapshots(
        snapshots: List<AccountDetailsSnapshot>,
        existingNamespaces: List<NamespaceEntry>,
    ): AccountDetailsSnapshot = AccountDetailsSnapshot(
        groups = snapshots
            .flatMap { snapshot -> snapshot.groups }
            .distinctBy { group -> group.accountId.trim().lowercase() + "|" + group.deviceId.trim().lowercase() }
            .sortedWith(compareBy({ it.accountId.lowercase() }, { it.deviceId.lowercase() })),
        namespaces = snapshots
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
            .sortedWith(compareBy({ it.namespaceName.lowercase() }, { it.desktopDeviceId.lowercase() })),
    )

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
}

internal data class AccountDetailsFetchTarget(
    val namespaceUrl: String,
    val accountId: String,
) {
    fun storageIdentity(): String = namespaceUrl.lowercase() + "|" + accountId.lowercase()
}