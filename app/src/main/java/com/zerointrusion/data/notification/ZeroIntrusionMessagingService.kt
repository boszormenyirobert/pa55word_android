package com.zerointrusion.data.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.util.Log

@AndroidEntryPoint
class ZeroIntrusionMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushNotificationGateway: PushNotificationGateway

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(
            "FCM",
            "Incoming notification: title=${remoteMessage.notification?.title}, body=${remoteMessage.notification?.body}, data=${remoteMessage.data}"
        )
        pushNotificationGateway.handleRemoteMessage(remoteMessage)
    }
}