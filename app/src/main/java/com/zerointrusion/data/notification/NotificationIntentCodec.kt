package com.zerointrusion.data.notification

import android.content.Intent
import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zerointrusion.domain.notification.BackendPushNotification
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationIntentCodec @Inject constructor(
    private val gson: Gson,
) {

    fun applyToIntent(intent: Intent, notification: BackendPushNotification) {
        intent.action = ACTION_OPEN_PUSH
        intent.putExtra(EXTRA_TITLE, notification.title)
        intent.putExtra(EXTRA_BODY, notification.body)
        intent.putExtra(EXTRA_TYPE, notification.type)
        intent.putExtra(EXTRA_SCREEN, notification.screen)
        intent.putExtra(EXTRA_ACCOUNT_ID, notification.accountId)
        intent.putExtra(EXTRA_DEVICE_ID, notification.deviceId)
        intent.putExtra(EXTRA_RECEIVED_AT, notification.receivedAtEpochMillis)
        intent.putExtra(EXTRA_DATA_JSON, gson.toJson(notification.data))
    }

    fun fromIntent(intent: Intent?): BackendPushNotification? {
        if (intent == null) {
            return null
        }

        return if (intent.action == ACTION_OPEN_PUSH) {
            fromInternalIntent(intent)
        } else {
            fromFirebaseLaunchIntent(intent)
        }
    }

    private fun fromInternalIntent(intent: Intent): BackendPushNotification? {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val screen = intent.getStringExtra(EXTRA_SCREEN).orEmpty()
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID).orEmpty()
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        val receivedAt = intent.getLongExtra(EXTRA_RECEIVED_AT, System.currentTimeMillis())
        val rawData = intent.getStringExtra(EXTRA_DATA_JSON).orEmpty()
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        val data = runCatching { gson.fromJson<Map<String, String>>(rawData, mapType) }.getOrDefault(emptyMap())

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
            data = data,
            receivedAtEpochMillis = receivedAt,
        )
    }

    private fun fromFirebaseLaunchIntent(intent: Intent): BackendPushNotification? {
        val extras = intent.extras ?: return null
        val deliveredData = extras.toStringMap()

        val title = deliveredData.firstNonBlank(
            EXTRA_TITLE,
            KEY_TITLE,
            KEY_GCM_NOTIFICATION_TITLE,
            KEY_GCM_NOTIFICATION_TITLE_ALT,
        ).ifBlank { "Push notification" }
        val body = deliveredData.firstNonBlank(
            EXTRA_BODY,
            KEY_BODY,
            KEY_GCM_NOTIFICATION_BODY,
            KEY_GCM_NOTIFICATION_BODY_ALT,
        )
        val type = deliveredData.firstNonBlank(EXTRA_TYPE, KEY_TYPE)
        val screen = deliveredData.firstNonBlank(EXTRA_SCREEN, KEY_SCREEN)
        val accountId = deliveredData.firstNonBlank(
            EXTRA_ACCOUNT_ID,
            KEY_ACCOUNT_ID,
            KEY_ACCOUNT_ID_SNAKE,
            KEY_ACCOUNT_ID_CAPITALIZED,
        )
        val deviceId = deliveredData.firstNonBlank(
            EXTRA_REQUESTING_DEVICE_ID,
            KEY_REQUESTING_DEVICE_ID,
            KEY_REQUESTING_DEVICE_ID_SNAKE,
            KEY_REQUESTING_DEVICE_ID_CAPITALIZED,
            EXTRA_DEVICE_ID,
            KEY_DEVICE_ID,
            KEY_DEVICE_ID_SNAKE,
            KEY_DEVICE_ID_CAPITALIZED,
        )

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
        const val ACTION_OPEN_PUSH = "com.zerointrusion.action.OPEN_PUSH"
        private const val EXTRA_TITLE = "push_title"
        private const val EXTRA_BODY = "push_body"
        private const val EXTRA_TYPE = "push_type"
        private const val EXTRA_SCREEN = "push_screen"
        private const val EXTRA_ACCOUNT_ID = "push_account_id"
        private const val EXTRA_REQUESTING_DEVICE_ID = "push_requesting_device_id"
        private const val EXTRA_DEVICE_ID = "push_device_id"
        private const val EXTRA_RECEIVED_AT = "push_received_at"
        private const val EXTRA_DATA_JSON = "push_data_json"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_TYPE = "type"
        private const val KEY_SCREEN = "screen"
        private const val KEY_ACCOUNT_ID = "accountId"
        private const val KEY_ACCOUNT_ID_SNAKE = "account_id"
        private const val KEY_ACCOUNT_ID_CAPITALIZED = "accountID"
        private const val KEY_REQUESTING_DEVICE_ID = "requestingDeviceId"
        private const val KEY_REQUESTING_DEVICE_ID_SNAKE = "requesting_device_id"
        private const val KEY_REQUESTING_DEVICE_ID_CAPITALIZED = "requestingDeviceID"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_DEVICE_ID_SNAKE = "device_id"
        private const val KEY_DEVICE_ID_CAPITALIZED = "deviceID"
        private const val KEY_GCM_NOTIFICATION_TITLE = "gcm.notification.title"
        private const val KEY_GCM_NOTIFICATION_BODY = "gcm.notification.body"
        private const val KEY_GCM_NOTIFICATION_TITLE_ALT = "google.notification.title"
        private const val KEY_GCM_NOTIFICATION_BODY_ALT = "google.notification.body"
    }
}

private fun Bundle.toStringMap(): Map<String, String> {
    return keySet().associateWith { key -> get(key)?.toString().orEmpty() }
}

private fun Map<String, String>.firstNonBlank(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isNotBlank() } }.orEmpty()
}