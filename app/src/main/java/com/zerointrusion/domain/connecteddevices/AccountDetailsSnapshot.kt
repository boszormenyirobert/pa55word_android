package com.zerointrusion.domain.connecteddevices

import com.zerointrusion.domain.namespaces.NamespaceEntry

data class AccountDetailsSnapshot(
    val groups: List<ConnectedDeviceGroup>,
    val namespaces: List<NamespaceEntry>,
    val mobileRegistrations: List<MobileNamespaceRegistration> = emptyList(),
)