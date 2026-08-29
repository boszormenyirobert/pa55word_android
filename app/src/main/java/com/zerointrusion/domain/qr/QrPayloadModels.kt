package com.zerointrusion.domain.qr

sealed interface ScannedQrPayload {
    val desktopDeviceId: String
    val publicKey: String
}

data class DeviceRegistrationQrPayload(
    override val desktopDeviceId: String,
    override val publicKey: String,
    val deviceName: String,
) : ScannedQrPayload

data class NamespaceQrPayload(
    override val desktopDeviceId: String,
    override val publicKey: String,
    val deviceName: String,
    val namespaceId: String,
    val namespaceName: String,
    val namespaceUrl: String,
) : ScannedQrPayload