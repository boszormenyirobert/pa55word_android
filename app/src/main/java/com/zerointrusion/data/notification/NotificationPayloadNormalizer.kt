package com.zerointrusion.data.notification

import com.google.firebase.messaging.RemoteMessage
import com.zerointrusion.domain.notification.BackendPushNotification
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPayloadNormalizer @Inject constructor() {

    fun fromRemoteMessage(remoteMessage: RemoteMessage): BackendPushNotification? {
        val deliveredData = remoteMessage.data.toMap()
        val type = deliveredData[KEY_TYPE].orEmpty()
        val accountId = deliveredData.firstNonBlank(
            KEY_ACCOUNT_ID,
            KEY_ACCOUNT_ID_SNAKE,
            KEY_ACCOUNT_ID_CAPITALIZED,
        )
        val deviceId = deliveredData.firstNonBlank(
            KEY_REQUESTING_DEVICE_ID,
            KEY_REQUESTING_DEVICE_ID_SNAKE,
            KEY_REQUESTING_DEVICE_ID_CAPITALIZED,
            KEY_DEVICE_ID,
            KEY_DEVICE_ID_SNAKE,
            KEY_DEVICE_ID_CAPITALIZED,
        )
        val deviceName = deliveredData.firstNonBlank(
            KEY_DEVICE_NAME,
            KEY_DEVICE_NAME_SNAKE,
            KEY_DEVICE_NAME_CAPITALIZED,
        )
        val title = deliveredData.notificationDisplayTitle(
            fallbackTitle = remoteMessage.notification?.title ?: deliveredData[KEY_TITLE],
            type = type,
        )
        val body = deliveredData.notificationDisplayBody(
            fallbackBody = remoteMessage.notification?.body ?: deliveredData[KEY_BODY],
            type = type,
            deviceName = deviceName,
            deviceId = deviceId,
            accountId = accountId,
        )
        val screen = deliveredData[KEY_SCREEN].orEmpty()

        if (title.isBlank() && body.isBlank() && accountId.isBlank() && deviceId.isBlank()) {
            return null
        }

        return BackendPushNotification(
            title = title,
            body = body,
            type = type,
            screen = screen,
            accountId = accountId,
            deviceId = deviceId,
            data = deliveredData,
            receivedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_TYPE = "type"
        private const val KEY_SCREEN = "screen"
        private const val KEY_ACCOUNT_ID = "accountId"
        private const val KEY_ACCOUNT_ID_SNAKE = "account_id"
        private const val KEY_ACCOUNT_ID_CAPITALIZED = "accountID"
        private const val KEY_DEVICE_NAME = "deviceName"
        private const val KEY_DEVICE_NAME_SNAKE = "device_name"
        private const val KEY_DEVICE_NAME_CAPITALIZED = "deviceName"
        private const val KEY_REQUESTING_DEVICE_ID = "requestingDeviceId"
        private const val KEY_REQUESTING_DEVICE_ID_SNAKE = "requesting_device_id"
        private const val KEY_REQUESTING_DEVICE_ID_CAPITALIZED = "requestingDeviceID"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_DEVICE_ID_SNAKE = "device_id"
        private const val KEY_DEVICE_ID_CAPITALIZED = "deviceID"
    }
}

private fun Map<String, String>.firstNonBlank(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isNotBlank() } }.orEmpty()
}