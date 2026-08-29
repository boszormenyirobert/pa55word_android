package com.zerointrusion.domain.connecteddevices

data class ConnectedDeviceNamespace(
    val namespaceId: String,
    val namespaceName: String,
    val namespaceUrl: String,
    val deviceId: String,
)

data class ConnectedDeviceGroup(
    val accountId: String,
    val deviceId: String,
    val deviceName: String? = null,
    val namespaces: List<ConnectedDeviceNamespace>,
)