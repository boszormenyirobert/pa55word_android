package com.zerointrusion.presentation.deviceinformation

data class DeviceInformationUiModel(
    val accountId: String,
    val deviceId: String,
    val deviceName: String,
    val namespaceName: String,
    val namespaceId: String,
    val namespaceUrl: String,
    val email: String,
)