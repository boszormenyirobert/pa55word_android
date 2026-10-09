package com.zerointrusion.data.repository.connecteddevices

import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration

internal fun mergeRefreshedMobileRegistrations(
    beforeRefresh: List<MobileNamespaceRegistration>,
    current: List<MobileNamespaceRegistration>,
    refreshed: List<MobileNamespaceRegistration>,
): List<MobileNamespaceRegistration> {
    fun MobileNamespaceRegistration.identity() = listOf(
        namespaceUrl.trim().trimEnd('/').lowercase(), accountId, mobileDeviceId, namespaceId,
    )
    val previousIdentities = beforeRefresh.map { it.identity() }.toSet()
    val addedDuringRefresh = current.filter { it.identity() !in previousIdentities }
    return (refreshed + addedDuringRefresh).distinctBy { it.identity() }
}
