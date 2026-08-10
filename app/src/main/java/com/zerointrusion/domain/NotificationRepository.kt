package com.zerointrusion.domain

import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeNotification(): Flow<BackendPushNotification?>

    fun publishNotification(notification: BackendPushNotification)

    fun clearNotification()
}