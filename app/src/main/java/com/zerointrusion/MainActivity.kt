package com.zerointrusion

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.zerointrusion.data.notification.PushNotificationGateway
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.zerointrusion.presentation.ZeroIntrusionAppRoot
import com.zerointrusion.ui.theme.ZeroIntrusionTheme

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var pushNotificationGateway: PushNotificationGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pushNotificationGateway.handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            ZeroIntrusionTheme {
                ZeroIntrusionAppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pushNotificationGateway.handleIntent(intent)
    }
}