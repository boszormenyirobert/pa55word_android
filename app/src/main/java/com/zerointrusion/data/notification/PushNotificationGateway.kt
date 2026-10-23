package com.zerointrusion.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.RemoteMessage
import com.zerointrusion.MainActivity
import com.zerointrusion.domain.notification.BackendPushNotification
import com.zerointrusion.domain.notification.NotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationGateway @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val notificationRepository: NotificationRepository,
    private val normalizer: NotificationPayloadNormalizer,
    private val intentCodec: NotificationIntentCodec,
) {

    fun handleRemoteMessage(remoteMessage: RemoteMessage) {
        val notification = normalizer.fromRemoteMessage(remoteMessage) ?: return
        Log.d(
            NOTIFICATION_LOG_TAG,
            "Normalized remote notification: title=${notification.title}, body=${notification.body}, type=${notification.type}, screen=${notification.screen}, accountId=${notification.accountId}, mobileDeviceId=${notification.mobileDeviceId}, desktopDeviceId=${notification.desktopDeviceId}, data=${notification.data}",
        )
        notificationRepository.publishNotification(notification)
        if (!isAppInForeground()) {
            showSystemNotification(notification)
        }
    }

    fun handleIntent(intent: Intent?) {
        val notification = intentCodec.fromIntent(intent) ?: return
        Log.d(
            NOTIFICATION_LOG_TAG,
            "Notification restored from intent: title=${notification.title}, body=${notification.body}, type=${notification.type}, screen=${notification.screen}, accountId=${notification.accountId}, mobileDeviceId=${notification.mobileDeviceId}, desktopDeviceId=${notification.desktopDeviceId}, data=${notification.data}",
        )
        notificationRepository.publishNotification(notification)
    }

    private fun showSystemNotification(notification: BackendPushNotification) {
        createChannelIfNeeded()

        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        intentCodec.applyToIntent(intent, notification)

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notification.systemNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val systemNotification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(appContext).notify(notification.systemNotificationId(), systemNotification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    private fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun BackendPushNotification.systemNotificationId(): Int {
        return data[NOTIFICATION_ID_KEY]?.trim()?.takeIf { it.isNotEmpty() }?.hashCode()
            ?: receivedAtEpochMillis.toInt()
    }

    companion object {
        private const val NOTIFICATION_LOG_TAG = "ZeroIntrusionNotification"
        private const val CHANNEL_ID = "zero_intrusion_backend_push"
        private const val CHANNEL_NAME = "Backend Push Notifications"
        private const val NOTIFICATION_ID_KEY = "notificationId"
    }
}