package com.zerointrusion.domain.notification

import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeNotification(): Flow<BackendPushNotification?>

    fun publishNotification(notification: BackendPushNotification)

    fun clearNotification()
}