package com.zerointrusion.domain.qr

sealed interface ScannedQrPayload {
    val deviceId: String
    val publicKey: String
}

data class DeviceRegistrationQrPayload(
    override val deviceId: String,
    override val publicKey: String,
    val deviceName: String,
) : ScannedQrPayload

data class NamespaceQrPayload(
    override val deviceId: String,
    override val publicKey: String,
    val deviceName: String,
    val namespaceId: String,
    val namespaceName: String,
    val namespaceUrl: String,
) : ScannedQrPayload