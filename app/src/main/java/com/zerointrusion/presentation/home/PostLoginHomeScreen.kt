package com.zerointrusion.presentation.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zerointrusion.domain.BackendPushNotification
import com.zerointrusion.presentation.NewDeviceRegistrationUiState
import com.zerointrusion.ui.theme.Blue
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ErrorColor
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.Green
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

@Composable
fun PostLoginHomeScreen(
    deviceId: String,
    namespaceName: String,
    email: String,
    notification: BackendPushNotification?,
    notificationError: String?,
    newDeviceRegistrationState: NewDeviceRegistrationUiState,
    onOpenDeviceInformation: () -> Unit,
    onOpenQrRead: () -> Unit,
    onOpenConnectedDevices: () -> Unit,
    onClearNewDeviceRegistrationState: () -> Unit,
    onAllowNotification: () -> Unit,
    onDeclineNotification: () -> Unit,
    onDismissNotificationError: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationManager = remember(context) { NotificationManagerCompat.from(context) }

    var pendingQrNavigation by rememberSaveable { mutableStateOf(false) }
    var cameraPermissionGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraPermissionError by rememberSaveable { mutableStateOf<String?>(null) }
    var hasRequestedCameraPermission by rememberSaveable { mutableStateOf(false) }

    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(notificationManager.areNotificationsEnabled()) }
    var notificationsPermissionGranted by rememberSaveable {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
        cameraPermissionError = if (granted) {
            null
        } else {
            "Camera permission was denied, so QR scanning is unavailable."
        }
        if (granted && pendingQrNavigation) {
            pendingQrNavigation = false
            onClearNewDeviceRegistrationState()
            onOpenQrRead()
        } else if (!granted) {
            pendingQrNavigation = false
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsPermissionGranted = granted
        notificationsEnabled = notificationManager.areNotificationsEnabled()
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted && !hasRequestedCameraPermission) {
            hasRequestedCameraPermission = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationsPermissionGranted &&
            !hasRequestedNotificationPermission
        ) {
            hasRequestedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        notificationsEnabled = notificationManager.areNotificationsEnabled()
    }

    LaunchedEffect(notificationsPermissionGranted) {
        notificationsEnabled = notificationManager.areNotificationsEnabled()
    }

    val hasNotification = notification != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Dark, LightDark, Dark),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .padding(bottom = 98.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
            )

            if (!notificationsEnabled || !notificationsPermissionGranted) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Notifications disabled",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onError,
                        )
                        Text(
                            text = "Background push alerts may not appear. Enable app notifications.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onError,
                        )
                        Button(
                            onClick = {
                                notificationsEnabled = notificationManager.areNotificationsEnabled()
                                if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !notificationsPermissionGranted
                                ) {
                                    hasRequestedNotificationPermission = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                        ) {
                            Text("Enable notifications")
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = LightDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onOpenDeviceInformation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Device information")
                    }

                    Button(
                        onClick = {
                            onClearNewDeviceRegistrationState()
                            if (cameraPermissionGranted) {
                                cameraPermissionError = null
                                onOpenQrRead()
                            } else {
                                pendingQrNavigation = true
                                hasRequestedCameraPermission = true
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Qr-Read")
                    }

                    Button(
                        onClick = onOpenConnectedDevices,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Connected devices")
                    }
                }
            }

            cameraPermissionError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorTextLight,
                )
            }

            when (newDeviceRegistrationState) {
                NewDeviceRegistrationUiState.Idle -> Unit
                NewDeviceRegistrationUiState.InProgress -> Text(
                    text = "Registering scanned device...",
                    color = White,
                )
                is NewDeviceRegistrationUiState.Success -> Text(
                    text = "New device registered: ${newDeviceRegistrationState.deviceId}",
                    color = com.zerointrusion.ui.theme.Green,
                )
                is NewDeviceRegistrationUiState.Error -> Text(
                    text = newDeviceRegistrationState.message,
                    color = ErrorTextLight,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            NotificationStateBox(
                notification = notification,
                notificationError = notificationError,
                onDismissNotificationError = onDismissNotificationError,
                deviceId = deviceId,
                namespaceName = namespaceName,
                email = email,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onAllowNotification,
                modifier = Modifier.weight(1f),
                enabled = hasNotification,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green,
                    contentColor = White,
                    disabledContainerColor = Green.copy(alpha = 0.45f),
                    disabledContentColor = White.copy(alpha = 0.8f),
                ),
            ) {
                Text("Allow")
            }
            Button(
                onClick = onDeclineNotification,
                modifier = Modifier.weight(1f),
                enabled = hasNotification,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorColor,
                    contentColor = White,
                    disabledContainerColor = ErrorColor.copy(alpha = 0.45f),
                    disabledContentColor = White.copy(alpha = 0.8f),
                ),
            ) {
                Text("Decline")
            }
        }
    }
}

@Composable
private fun NotificationStateBox(
    notification: BackendPushNotification?,
    notificationError: String?,
    onDismissNotificationError: () -> Unit,
    deviceId: String,
    namespaceName: String,
    email: String,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Dark),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .border(BorderStroke(1.dp, Blue), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (notification == null) {
                Text("No active notification", color = White, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Device: ${deviceId.ifBlank { "(empty)" }}",
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Namespace: ${namespaceName.ifBlank { "(empty)" }}",
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Email: ${email.ifBlank { "(empty)" }}",
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text("Incoming notification", color = White, style = MaterialTheme.typography.titleMedium)
                Text("Title: ${notification.title.ifBlank { "(empty)" }}", color = White)
                Text("Body: ${notification.body.ifBlank { "(empty)" }}", color = White)
                Text("Type: ${notification.type.ifBlank { "(empty)" }}", color = White)
                Text("Screen: ${notification.screen.ifBlank { "(empty)" }}", color = White)
                Text("Account: ${notification.accountId.ifBlank { "(empty)" }}", color = White)
                Text("Device: ${notification.deviceId.ifBlank { "(empty)" }}", color = White)

                if (notification.data.isNotEmpty()) {
                    notification.data.forEach { (k, v) ->
                        Text(text = "$k: $v", color = White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (!notificationError.isNullOrBlank()) {
                Text(
                    text = notificationError,
                    color = ErrorTextLight,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onDismissNotificationError,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LightDark,
                        contentColor = White,
                    ),
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}
