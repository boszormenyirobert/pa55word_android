package com.zerointrusion.domain

data class BackendPushNotification(
    val title: String,
    val body: String,
    val type: String,
    val screen: String,
    val accountId: String,
    val deviceId: String,
    val data: Map<String, String>,
    val receivedAtEpochMillis: Long,
)

enum class NotificationDecision {
    Accept,
    Decline,
}