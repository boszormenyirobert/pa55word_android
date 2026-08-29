package com.zerointrusion.domain.notification

data class BackendPushNotification(
    val title: String,
    val body: String,
    val type: String,
    val screen: String,
    val accountId: String,
    val mobileDeviceId: String,
    val desktopDeviceId: String,
    val data: Map<String, String>,
    val receivedAtEpochMillis: Long,
)

enum class NotificationDecision {
    Accept,
    Decline,
}