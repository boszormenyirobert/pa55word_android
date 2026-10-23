package com.zerointrusion.data.notification

import com.zerointrusion.domain.notification.BackendPushNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRepositoryImplTest {

    @Test
    fun ignoresDuplicateNotificationId() = runBlocking {
        val repository = NotificationRepositoryImpl()
        val original = notification(notificationId = "notification-1", body = "Original", receivedAt = 1L)
        val duplicate = notification(notificationId = "notification-1", body = "Duplicate", receivedAt = 2L)

        repository.publishNotification(original)
        repository.publishNotification(duplicate)

        assertEquals(original, repository.observeNotification().first())
    }

    @Test
    fun publishesDifferentNotificationIds() = runBlocking {
        val repository = NotificationRepositoryImpl()
        repository.publishNotification(notification(notificationId = "notification-1", body = "First", receivedAt = 1L))
        val next = notification(notificationId = "notification-2", body = "Second", receivedAt = 2L)

        repository.publishNotification(next)

        assertEquals(next, repository.observeNotification().first())
    }

    private fun notification(notificationId: String, body: String, receivedAt: Long) = BackendPushNotification(
        title = "Authentication request",
        body = body,
        type = "auth_request",
        screen = "home",
        accountId = "account-1",
        mobileDeviceId = "mobile-1",
        desktopDeviceId = "desktop-1",
        data = mapOf("notificationId" to notificationId),
        receivedAtEpochMillis = receivedAt,
    )
}