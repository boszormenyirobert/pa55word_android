package com.zerointrusion.presentation.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zerointrusion.R
import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.domain.connecteddevices.DatabaseAvailability
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.notification.BackendPushNotification
import com.zerointrusion.presentation.app.QrNamespaceRegistrationUiState
import com.zerointrusion.presentation.common.AppBottomActions
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppButtonSize
import com.zerointrusion.presentation.common.AppIconButton
import com.zerointrusion.presentation.common.AppScreenHeader
import com.zerointrusion.ui.theme.ActionAcceptGreen
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.Blue
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.Green
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import com.zerointrusion.ui.theme.Yellow
import java.util.Date

@Composable
fun PostLoginHomeScreen(
    deviceId: String,
    namespaceName: String,
    email: String,
    namespaces: List<NamespaceEntry>,
    databaseAvailability: List<DatabaseAvailability>,
    isInternetConnected: Boolean,
    biometricRemainingSeconds: Int,
    isHomeRefreshing: Boolean,
    homeRefreshErrorCode: String?,
    liveDesktopSessions: List<ActiveDesktopSession>,
    liveDesktopSessionRevokingAttestationId: String?,
    liveDesktopSessionRevokeErrorMessage: String?,
    notification: BackendPushNotification?,
    notificationError: String?,
    qrNamespaceRegistrationState: QrNamespaceRegistrationUiState,
    onOpenSettings: () -> Unit,
    onOpenQrRead: () -> Unit,
    onClearQrNamespaceRegistrationState: () -> Unit,
    onRevokeDesktopSession: (ActiveDesktopSession) -> Unit,
    onRefreshHome: () -> Unit,
    onDismissLiveDesktopSessionRevokeError: () -> Unit,
    onAllowNotification: () -> Unit,
    onDeclineNotification: () -> Unit,
    onDismissNotificationError: () -> Unit,
    onRenewBiometrics: () -> Unit,
) {
    val context = LocalContext.current
    val notificationManager = remember(context) { NotificationManagerCompat.from(context) }
    var notificationsEnabled by remember {
        mutableStateOf(notificationManager.areNotificationsEnabled())
    }
    var cameraPermissionError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingQrNavigation by rememberSaveable { mutableStateOf(false) }
    var hasRequestedCameraPermission by rememberSaveable { mutableStateOf(false) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val cameraPermissionGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    val notificationsPermissionGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
            granted ->
            if (granted) {
                cameraPermissionError = null
                if (pendingQrNavigation) {
                    pendingQrNavigation = false
                    onOpenQrRead()
                }
            } else {
                pendingQrNavigation = false
                cameraPermissionError =
                    if (hasRequestedCameraPermission) {
                        "Camera permission is required to scan the QR code."
                    } else {
                        null
                    }
            }
        }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
            notificationsEnabled = notificationManager.areNotificationsEnabled()
            hasRequestedNotificationPermission = true
        }

    LaunchedEffect(notificationsPermissionGranted) {
        notificationsEnabled = notificationManager.areNotificationsEnabled()
    }

    val hasNotification = notification != null

    fun openQrRead() {
        onClearQrNamespaceRegistrationState()
        if (cameraPermissionGranted) {
            cameraPermissionError = null
            onOpenQrRead()
        } else {
            pendingQrNavigation = true
            hasRequestedCameraPermission = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(brush = Brush.verticalGradient(colors = listOf(Dark, LightDark, Dark)))
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppScreenHeader(
                logoWidth = 160.dp,
                actions = {
                    AppIconButton(onClick = ::openQrRead) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = "QR-scan",
                            tint = White,
                        )
                    }
                    HomeRefreshButton(isRefreshing = isHomeRefreshing, onRefresh = onRefreshHome)
                    AppIconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Open settings",
                            tint = White,
                        )
                    }
                },
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DatabaseAvailabilityCard(availability = databaseAvailability)

                homeRefreshErrorCode?.let { code ->
                    Text(
                        text = stringResource(R.string.home_refresh_failed, code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorTextLight,
                    )
                }

                LiveDesktopSessionsCard(
                    sessions = liveDesktopSessions,
                    fallbackServerName = namespaceName,
                    revokingAttestationId = liveDesktopSessionRevokingAttestationId,
                    onRevokeDesktopSession = onRevokeDesktopSession,
                )

                liveDesktopSessionRevokeErrorMessage?.let { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorTextLight,
                            modifier = Modifier.weight(1f),
                        )
                        AppIconButton(onClick = onDismissLiveDesktopSessionRevokeError) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss desktop session revoke error",
                                tint = ErrorTextLight,
                            )
                        }
                    }
                }

                if (!notificationsEnabled || !notificationsPermissionGranted) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
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
                                text =
                                    "Background push alerts may not appear. Enable app notifications.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onError,
                            )
                            AppButton(
                                icon = Icons.Default.NotificationsActive,
                                onClick = {
                                    notificationsEnabled =
                                        notificationManager.areNotificationsEnabled()
                                    if (!notificationsPermissionGranted) {
                                        hasRequestedNotificationPermission = true
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    } else {
                                        val intent =
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .apply {
                                                    putExtra(
                                                        Settings.EXTRA_APP_PACKAGE,
                                                        context.packageName,
                                                    )
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

                cameraPermissionError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorTextLight,
                    )
                }

                when (qrNamespaceRegistrationState) {
                    QrNamespaceRegistrationUiState.Idle -> Unit
                    is QrNamespaceRegistrationUiState.AwaitingDeviceSelection -> Unit
                    QrNamespaceRegistrationUiState.InProgress ->
                        Text(text = "Connecting scanned namespace...", color = White)
                    is QrNamespaceRegistrationUiState.Success ->
                        Text(
                            text = qrNamespaceRegistrationState.message,
                            color = com.zerointrusion.ui.theme.Green,
                        )
                    is QrNamespaceRegistrationUiState.Error ->
                        Text(text = qrNamespaceRegistrationState.message, color = ErrorTextLight)
                }
            }

            NotificationStateBox(
                notification = notification,
                deviceId = deviceId,
                isInternetConnected = isInternetConnected,
                biometricRemainingSeconds = biometricRemainingSeconds,
                onRenewBiometrics = onRenewBiometrics,
            )
            if (!notificationError.isNullOrBlank()) {
                Text(
                    text = notificationError,
                    color = ErrorTextLight,
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppButton(icon = Icons.Default.Close, onClick = onDismissNotificationError) {
                    Text("Dismiss")
                }
            }
            AppBottomActions {
                AppButton(
                    icon = Icons.Default.Check,
                    onClick = onAllowNotification,
                    modifier = Modifier.weight(1f),
                    enabled = hasNotification,
                    color = ActionAcceptGreen,
                    disabledContentAlpha = 0.72f,
                ) {
                    Text("Allow")
                }
                AppButton(
                    icon = Icons.Default.Close,
                    onClick = onDeclineNotification,
                    modifier = Modifier.weight(1f),
                    enabled = hasNotification,
                    color = ErrorTextLight,
                    disabledContentAlpha = 0.72f,
                ) {
                    Text("Decline")
                }
            }
        }
    }
}

@Composable
internal fun DatabaseAvailabilityCard(availability: List<DatabaseAvailability>) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Database availability",
                style = MaterialTheme.typography.titleMedium,
                color = White,
            )
            if (availability.isEmpty()) {
                Text(
                    text = "Checking database availability...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = White,
                )
            } else {
                availability.forEach { database ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(12.dp)
                                    .background(
                                        color =
                                            if (database.isAvailable) Green else ActionDeclineRed,
                                        shape = CircleShape,
                                    )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = database.namespaceName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LiveDesktopSessionsCard(
    sessions: List<ActiveDesktopSession>,
    fallbackServerName: String,
    revokingAttestationId: String?,
    onRevokeDesktopSession: (ActiveDesktopSession) -> Unit,
) {
    val visibleGroups = remember(sessions) { buildHomeSessionGroups(sessions, fallbackServerName) }

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

            if (visibleGroups.isEmpty()) {
                Text(
                    text = "No live desktop sessions.",
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                visibleGroups.forEach { device ->
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
                                text = device.deviceName,
                                color = White,
                                style = MaterialTheme.typography.titleMedium,
                            )

                            device.namespaces.forEachIndexed { index, namespace ->
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = namespace.serverName,
                                        color = White,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )

                                    namespace.sessions.forEachIndexed { sessionIndex, session ->
                                        if (sessionIndex != 0) {
                                            HorizontalDivider(
                                                color = White.copy(alpha = 0.12f),
                                                thickness = 1.dp,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Surface(
                                                modifier =
                                                    Modifier.weight(1f).height(32.dp).semantics {
                                                        contentDescription = "Live session status"
                                                    },
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Transparent,
                                                border =
                                                    BorderStroke(1.dp, Yellow.copy(alpha = 0.6f)),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "live session",
                                                        color = Yellow,
                                                        style = MaterialTheme.typography.labelLarge,
                                                    )
                                                }
                                            }

                                            AppButton(
                                                icon = Icons.Default.LinkOff,
                                                onClick = { onRevokeDesktopSession(session) },
                                                enabled =
                                                    revokingAttestationId != session.attestationId,
                                                color = ActionDeclineRed,
                                                modifier = Modifier.weight(1f),
                                                size = AppButtonSize.Small,
                                                horizontalContentPadding = 8.dp,
                                            ) {
                                                if (
                                                    revokingAttestationId == session.attestationId
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier =
                                                            Modifier.size(16.dp).semantics {
                                                                contentDescription =
                                                                    "Revoking session"
                                                            },
                                                        color = ActionDeclineRed,
                                                        strokeWidth = 2.dp,
                                                    )
                                                } else {
                                                    Text("Revoke")
                                                }
                                            }
                                        }
                                    }
                                }

                                if (index != device.namespaces.lastIndex) {
                                    HorizontalDivider(
                                        color = White.copy(alpha = 0.12f),
                                        thickness = 1.dp,
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
internal fun HomeRefreshButton(isRefreshing: Boolean, onRefresh: () -> Unit) {
    val progressDescription = stringResource(R.string.home_refreshing)
    AppIconButton(onClick = onRefresh, enabled = !isRefreshing) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier =
                    Modifier.size(24.dp).semantics { contentDescription = progressDescription },
                color = White,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.home_refresh),
                tint = White,
            )
        }
    }
}

@Composable
internal fun NotificationStateBox(
    notification: BackendPushNotification?,
    deviceId: String,
    isInternetConnected: Boolean,
    biometricRemainingSeconds: Int,
    onRenewBiometrics: () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Dark),
        modifier =
            Modifier.fillMaxWidth()
                .border(
                    BorderStroke(1.dp, if (notification != null) Green else Blue),
                    RoundedCornerShape(18.dp),
                )
                .clickable(
                    enabled = biometricRemainingSeconds > 0,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.home_biometric_renew),
                    onClick = onRenewBiometrics,
                ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.home_biometric_validity),
                    modifier = Modifier.weight(1f),
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val remaining = biometricRemainingSeconds.coerceAtLeast(0)
                Text(
                    text =
                        stringResource(
                            R.string.home_biometric_validity_value,
                            remaining / 60,
                            remaining % 60,
                        ),
                    modifier = Modifier.width(64.dp),
                    color = White,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!isInternetConnected) {
                val connectionDescription = stringResource(R.string.home_internet_unavailable)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier.size(12.dp)
                                .background(color = ActionDeclineRed, shape = CircleShape)
                                .semantics { contentDescription = connectionDescription }
                    )
                    Text(
                        connectionDescription,
                        color = ErrorTextLight,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (notification != null) {
                Text(
                    text = stringResource(R.string.home_notification_incoming),
                    color = White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val displayedDevice =
                notification?.data?.get("deviceName")?.takeIf { it.isNotBlank() }
                    ?: notification?.desktopDeviceId?.takeIf { it.isNotBlank() }
                    ?: deviceId
            NotificationDetailLine(
                label = stringResource(R.string.home_notification_device),
                value =
                    displayedDevice.ifBlank {
                        stringResource(R.string.home_notification_empty_device)
                    },
            )
            if (notification != null) {
                val context = LocalContext.current
                val receivedAt = Date(notification.receivedAtEpochMillis)
                NotificationDetailLine(
                    label = stringResource(R.string.home_notification_date_time),
                    value =
                        stringResource(
                            R.string.home_notification_date_time_value,
                            DateFormat.getMediumDateFormat(context).format(receivedAt),
                            DateFormat.getTimeFormat(context).format(receivedAt),
                        ),
                )
            }
        }
    }
}

@Composable
private fun NotificationDetailLine(label: String, value: String) {
    if (value.isBlank()) {
        return
    }

    Text(
        text =
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append("$label: ")
                }
                append(value)
            },
        color = White,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
    )
}

private fun Map<String, String>.firstNonBlankValue(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isNotBlank() } }.orEmpty()
}
