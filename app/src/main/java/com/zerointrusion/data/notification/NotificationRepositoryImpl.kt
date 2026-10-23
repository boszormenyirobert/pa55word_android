package com.zerointrusion.data.notification

import com.zerointrusion.domain.notification.BackendPushNotification
import com.zerointrusion.domain.notification.NotificationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NotificationRepositoryImpl @Inject constructor() : NotificationRepository {

    private val notificationFlow = MutableStateFlow<BackendPushNotification?>(null)
    private val publishedNotificationIds = object : LinkedHashMap<String, Unit>(MAX_DEDUPLICATION_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            return size > MAX_DEDUPLICATION_ENTRIES
        }
    }

    override fun observeNotification(): Flow<BackendPushNotification?> = notificationFlow.asStateFlow()

    @Synchronized
    override fun publishNotification(notification: BackendPushNotification) {
        val notificationId = notification.data[NOTIFICATION_ID_KEY]?.trim().orEmpty()
        if (notificationId.isNotEmpty() && publishedNotificationIds.put(notificationId, Unit) != null) {
            return
        }
        notificationFlow.value = notification
    }

    override fun clearNotification() {
        notificationFlow.value = null
    }

    private companion object {
        const val NOTIFICATION_ID_KEY = "notificationId"
        const val MAX_DEDUPLICATION_ENTRIES = 256
    }
}