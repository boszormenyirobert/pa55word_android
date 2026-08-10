package com.zerointrusion.data.notification

import com.zerointrusion.domain.BackendPushNotification
import com.zerointrusion.domain.NotificationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NotificationRepositoryImpl @Inject constructor() : NotificationRepository {

    private val notificationFlow = MutableStateFlow<BackendPushNotification?>(null)

    override fun observeNotification(): Flow<BackendPushNotification?> = notificationFlow.asStateFlow()

    override fun publishNotification(notification: BackendPushNotification) {
        notificationFlow.value = notification
    }

    override fun clearNotification() {
        notificationFlow.value = null
    }
}