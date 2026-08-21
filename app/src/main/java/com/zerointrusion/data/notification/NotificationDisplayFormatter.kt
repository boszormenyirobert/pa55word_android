package com.zerointrusion.data.notification

internal fun Map<String, String>.notificationDisplayTitle(
    fallbackTitle: String?,
    type: String,
): String {
    return type.ifBlank { fallbackTitle?.takeIf { it.isNotBlank() }.orEmpty() }
}

internal fun Map<String, String>.notificationDisplayBody(
    fallbackBody: String?,
    type: String,
    deviceName: String,
    deviceId: String,
    accountId: String,
): String {
    val parts = buildList {
        if (deviceName.isNotBlank()) add("Device: $deviceName")
        if (deviceId.isNotBlank()) add("Device id: $deviceId")
        if (accountId.isNotBlank()) add("Account id: $accountId")
    }

    if (parts.isNotEmpty()) {
        return parts.joinToString("\n")
    }

    return fallbackBody.orEmpty()
}