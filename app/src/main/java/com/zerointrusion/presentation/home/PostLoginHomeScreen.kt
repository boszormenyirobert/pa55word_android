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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.notification.BackendPushNotification
import com.zerointrusion.presentation.app.QrNamespaceRegistrationUiState
import com.zerointrusion.ui.theme.Blue
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import com.zerointrusion.ui.theme.Yellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PostLoginHomeScreen(
    deviceId: String,
    namespaceName: String,
    email: String,
    namespaces: List<NamespaceEntry>,
    liveDesktopSessions: List<ActiveDesktopSession>,
    liveDesktopSessionRevokingAttestationId: String?,
    notification: BackendPushNotification?,
    notificationError: String?,
    qrNamespaceRegistrationState: QrNamespaceRegistrationUiState,
    onOpenSettings: () -> Unit,
    onOpenQrRead: () -> Unit,
    onClearQrNamespaceRegistrationState: () -> Unit,
    onRefreshLiveDesktopSessions: () -> Unit,
    onRevokeDesktopSession: (ActiveDesktopSession) -> Unit,
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
            onClearQrNamespaceRegistrationState()
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

    LaunchedEffect(Unit) {
        onRefreshLiveDesktopSessions()
        while (isActive) {
            delay(15_000)
            onRefreshLiveDesktopSessions()
        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.headlineMedium,
                    color = White,
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Open settings",
                        tint = White,
                    )
                }
            }

            LiveDesktopSessionsCard(
                sessions = liveDesktopSessions,
                namespaces = namespaces,
                fallbackServerName = namespaceName,
                revokingAttestationId = liveDesktopSessionRevokingAttestationId,
                onRevokeDesktopSession = onRevokeDesktopSession,
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
                        onClick = {
                            onClearQrNamespaceRegistrationState()
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

                }
            }

            cameraPermissionError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorTextLight,
                )
            }

            when (qrNamespaceRegistrationState) {
                QrNamespaceRegistrationUiState.Idle -> Unit
                QrNamespaceRegistrationUiState.InProgress -> Text(
                    text = "Connecting scanned namespace...",
                    color = White,
                )
                is QrNamespaceRegistrationUiState.Success -> Text(
                    text = "Namespace connected: ${qrNamespaceRegistrationState.namespaceName} (desktop: ${qrNamespaceRegistrationState.desktopDeviceId})",
                    color = com.zerointrusion.ui.theme.Green,
                )
                is QrNamespaceRegistrationUiState.Error -> Text(
                    text = qrNamespaceRegistrationState.message,
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
                border = if (hasNotification) BorderStroke(2.dp, Yellow) else null,
            ) {
                Text("Allow")
            }
            Button(
                onClick = onDeclineNotification,
                modifier = Modifier.weight(1f),
                enabled = hasNotification,
                border = if (hasNotification) BorderStroke(2.dp, Yellow) else null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ActionDeclineRed,
                    contentColor = White,
                    disabledContainerColor = ActionDeclineRed.copy(alpha = 0.45f),
                    disabledContentColor = White.copy(alpha = 0.8f),
                ),
            ) {
                Text("Decline")
            }
        }
    }
}

@Composable
private fun LiveDesktopSessionsCard(
    sessions: List<ActiveDesktopSession>,
    namespaces: List<NamespaceEntry>,
    fallbackServerName: String,
    revokingAttestationId: String?,
    onRevokeDesktopSession: (ActiveDesktopSession) -> Unit,
) {
    val deviceNamesById = remember(namespaces) {
        namespaces
            .asReversed()
            .associate { namespace ->
                namespace.deviceId to namespace.deviceName.orEmpty()
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
            Text(
                text = "Desktop sessions",
                style = MaterialTheme.typography.titleLarge,
                color = White,
            )

            if (sessions.isEmpty()) {
                Text(
                    text = "No live desktop sessions.",
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                sessions.forEach { session ->
                    val resolvedServerName = deviceNamesById[session.deviceId]
                        .takeIf { !it.isNullOrBlank() }
                        ?: fallbackServerName.ifBlank { session.deviceId }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Dark.copy(alpha = 0.36f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = resolvedServerName,
                                color = White,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Yellow.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, Yellow),
                                ) {
                                    Text(
                                        text = "live session",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = Yellow,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (revokingAttestationId == session.attestationId) {
                                        ActionDeclineRed.copy(alpha = 0.45f)
                                    } else {
                                        ActionDeclineRed
                                    },
                                    modifier = Modifier.clickable(
                                        enabled = revokingAttestationId != session.attestationId,
                                        onClick = { onRevokeDesktopSession(session) },
                                    ),
                                ) {
                                    Text(
                                        if (revokingAttestationId == session.attestationId) "Revoking..." else "Revoke session",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = White.copy(alpha = if (revokingAttestationId == session.attestationId) 0.8f else 1f),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
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
                val payloadType = notification.data["type"].orEmpty()
                Text("Incoming notification", color = White, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = payloadType.ifBlank { notification.type.ifBlank { notification.title.ifBlank { "(empty)" } } },
                    color = White,
                    style = MaterialTheme.typography.titleMedium,
                )

                val deviceName = notification.data["deviceName"].orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NotificationDetailLine(label = "Device", value = deviceName)
                    NotificationDetailLine(label = "Device id", value = notification.deviceId)
                    NotificationDetailLine(label = "Account id", value = notification.accountId)
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

@Composable
private fun NotificationDetailLine(
    label: String,
    value: String,
) {
    if (value.isBlank()) {
        return
    }

    Text(
        text = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append("$label: ")
            }
            append(value)
        },
        color = White,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
    )
}
