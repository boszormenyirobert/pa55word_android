package com.zerointrusion.domain.connecteddevices

data class MobileNamespaceRegistration(
    val accountId: String,
    val mobileDeviceId: String,
    val namespaceId: String,
    val namespaceName: String,
    val namespaceUrl: String,
    val encDek: String,
    val root: Boolean = false,
)
