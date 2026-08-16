package com.zerointrusion.domain.connecteddevices

data class ConnectedDeviceNamespace(
    val namespaceName: String,
    val namespaceUrl: String,
)

data class ConnectedDeviceGroup(
    val accountId: String,
    val deviceId: String,
    val namespaces: List<ConnectedDeviceNamespace>,
)