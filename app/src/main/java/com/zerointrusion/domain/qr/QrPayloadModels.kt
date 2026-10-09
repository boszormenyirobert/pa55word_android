package com.zerointrusion.domain.qr

sealed interface ScannedQrPayload {
}

data class DeviceRegistrationQrPayload(
    val desktopDeviceId: String,
    val publicKey: String,
    val deviceName: String,
    val targetUrl: String,
) : ScannedQrPayload

data class NamespaceQrPayload(
    val desktopDeviceId: String,
    val publicKey: String,
    val deviceName: String,
    val namespaceId: String,
    val namespaceName: String,
    val namespaceUrl: String,
) : ScannedQrPayload

data class NamespaceSyncQrPayload(
    val desktopDeviceId: String,
    val publicKey: String,
    val deviceName: String,
    val namespaceId: String,
    val namespaceUrl: String,
) : ScannedQrPayload

data class AdditionMobileDeviceQrPayload(
    val publicKey: String,
    val deviceName: String,
    val mobileDeviceId: String,
    val namespaceUrls: List<String>,
) : ScannedQrPayload

data class AdditionalMobileNamespace(
    val accountId: String,
    val desktopDeviceId: String,
    val namespaceId: String,
    val namespace: String,
    val url: String,
    val root: Boolean,
)

data class AdditionalMobileNamespacesQrPayload(
    val namespaces: List<AdditionalMobileNamespace>,
) : ScannedQrPayload