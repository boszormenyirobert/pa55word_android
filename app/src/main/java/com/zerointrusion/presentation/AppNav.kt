package com.zerointrusion.presentation

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zerointrusion.domain.NotificationDecision

sealed class AppRoute(val route: String) {
    data object Loading : AppRoute("loading")
    data object Security : AppRoute("security")
    data object Registration : AppRoute("registration")
    data object Home : AppRoute("home")
}

@Composable
fun ZeroIntrusionAppRoot(
    appViewModel: AppViewModel = hiltViewModel(),
    registrationViewModel: RegistrationViewModel = hiltViewModel(),
) {
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()
    val registrationState by registrationViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    LaunchedEffect(appState.currentRoute) {
        if (navController.currentDestination?.route != appState.currentRoute) {
            navController.navigate(appState.currentRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = AppRoute.Loading.route,
        ) {
            composable(AppRoute.Loading.route) {
                LoadingScreen(
                    message = if (registrationState.isSubmitting) {
                        "Registering device…"
                    } else {
                        "Preparing secure registration…"
                    },
                )
            }
            composable(AppRoute.Security.route) {
                SecurityScreen(
                    errorMessage = appState.authenticationError,
                    onAuthenticationSucceeded = appViewModel::onAuthenticationSucceeded,
                    onAuthenticationFailed = appViewModel::onAuthenticationFailed,
                )
            }
            composable(AppRoute.Registration.route) {
                if (registrationState.isSubmitting) {
                    LoadingScreen(message = "Registering device…")
                } else {
                    RegistrationScreen(
                        state = registrationState,
                        onEmailChanged = registrationViewModel::onEmailChanged,
                        onPhoneChanged = registrationViewModel::onPhoneChanged,
                        onNamespaceNameChanged = registrationViewModel::onNamespaceNameChanged,
                        onApiUrlChanged = registrationViewModel::onApiUrlChanged,
                        onRegister = registrationViewModel::register,
                    )
                }
            }
            composable(AppRoute.Home.route) {
                HomeScreen(
                    deviceId = appState.registrationState?.deviceId.orEmpty(),
                    namespaceName = appState.registrationState?.namespaceName.orEmpty(),
                    email = appState.registrationState?.email.orEmpty(),
                    fcmSyncState = appState.fcmSyncState,
                    newDeviceRegistrationState = appState.newDeviceRegistrationState,
                    onRetryFcmSync = appViewModel::retryFcmSync,
                    onReadQrCode = appViewModel::registerNewDeviceFromQr,
                    onClearNewDeviceRegistrationState = appViewModel::clearNewDeviceRegistrationState,
                )
            }
        }

        val mismatchError = appState.notificationOverlay.mismatchError
        if (!mismatchError.isNullOrBlank()) {
            NotificationMismatchBanner(
                message = mismatchError,
                onDismiss = appViewModel::dismissNotificationMismatch,
            )
        }

        appState.notificationOverlay.notification?.let { notification ->
            IncomingNotificationSheet(
                notification = notification,
                showDecisionButtons = appState.notificationOverlay.canRespond,
                onAccept = { appViewModel.onNotificationDecision(NotificationDecision.Accept) },
                onDecline = { appViewModel.onNotificationDecision(NotificationDecision.Decline) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomingNotificationSheet(
    notification: com.zerointrusion.domain.BackendPushNotification,
    showDecisionButtons: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDecline,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroPanel(
                eyebrow = "Incoming backend push",
                title = notification.title.ifBlank { "Push notification" },
                body = notification.body.ifBlank { "A backend notification was delivered to this device." },
            )
            StatusBanner(
                title = if (showDecisionButtons) "Matched registered device" else "Decision buttons hidden",
                message = if (showDecisionButtons) {
                    "The incoming accountId and deviceId exactly match the persisted registration state, so this dedicated notification view is allowed."
                } else {
                    "The incoming accountId and deviceId do not match the persisted registration state, so the action buttons are hidden."
                },
                tone = if (showDecisionButtons) BannerTone.Success else BannerTone.Info,
            )
            DetailRow(label = "Title", value = notification.title.ifBlank { "(empty)" })
            DetailRow(label = "Body", value = notification.body.ifBlank { "(empty)" })
            DetailRow(label = "Type", value = notification.type.ifBlank { "(empty)" })
            DetailRow(label = "Screen", value = notification.screen.ifBlank { "(empty)" })
            DetailRow(label = "Account ID", value = notification.accountId)
            DetailRow(label = "Device ID", value = notification.deviceId)
            NotificationDataCard(data = notification.data)
            if (showDecisionButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text("Decline")
                    }
                    PrimaryActionButton(
                        text = "Accept",
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationMismatchBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.32f), RoundedCornerShape(24.dp))
                .padding(16.dp)
                .clickable(onClick = onDismiss),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusBanner(
                title = "Push notification blocked",
                message = message,
                tone = BannerTone.Error,
            )
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun NotificationDataCard(data: Map<String, String>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Payload data",
                style = MaterialTheme.typography.titleLarge,
            )
            if (data.isEmpty()) {
                Text(
                    text = "No additional key-value data arrived with this push.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                data.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = key,
                            modifier = Modifier.weight(0.35f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = value,
                            modifier = Modifier.weight(0.65f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityScreen(
    errorMessage: String?,
    onAuthenticationSucceeded: () -> Unit,
    onAuthenticationFailed: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var promptNonce by rememberSaveable { mutableIntStateOf(0) }

    val biometricPrompt: BiometricPrompt? = remember(activity) {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(it),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            onAuthenticationFailed(errString.toString())
                        }
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onAuthenticationSucceeded()
                    }

                    override fun onAuthenticationFailed() {
                        onAuthenticationFailed("Authentication failed. Please retry.")
                    }
                },
            )
        }
    }

    LaunchedEffect(activity, promptNonce) {
        val prompt = biometricPrompt
        if (activity == null || prompt == null) {
            onAuthenticationFailed("Authentication is unavailable on this device.")
            return@LaunchedEffect
        }

        when (val status = canAuthenticate(context)) {
            AuthAvailability.Available -> prompt.authenticate(buildPromptInfo())
            is AuthAvailability.Unavailable -> onAuthenticationFailed(status.message)
        }
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(WindowInsets.safeDrawing.asPaddingValues()),
            verticalArrangement = Arrangement.Center,
        ) {
            HeroPanel(
                eyebrow = "Security Gate",
                title = "Confirm this is your trusted device.",
                body = "The app unlocks registration and stored account state only after a strong biometric or your device credential succeeds.",
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PillLabel(text = "Required before routing")
                    Text(
                        text = "Authenticate with a strong biometric or the configured device credential.",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "If authentication or enrollment is unavailable, the app stays blocked and offers manual retry only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        StatusBanner(
                            title = "Authentication blocked",
                            message = errorMessage,
                            tone = BannerTone.Error,
                        )
                    }
                    Button(
                        onClick = { promptNonce++ },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Text("Retry Authentication")
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationScreen(
    state: RegistrationUiState,
    onEmailChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onNamespaceNameChanged: (String) -> Unit,
    onApiUrlChanged: (String) -> Unit,
    onRegister: () -> Unit,
) {
    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HeroPanel(
                eyebrow = "Milestone 7",
                title = "Secure device registration, with the dangerous parts kept out of sight.",
                body = "This flow generates the keystore keypair, builds the backend payload and stores the returned account state without exposing sensitive material in the UI.",
            )
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillLabel(text = "Plain POST /api/v1/account")
                        PillLabel(text = "Local signature only")
                    }
                    Text(
                        text = "Registration details",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Provide the namespace endpoint and identity fields. The app derives device identifiers and encrypted key material locally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RegistrationTextField(
                        value = state.email,
                        onValueChange = onEmailChanged,
                        label = "Email",
                        supporting = state.emailError ?: "Used in the registration payload only.",
                        isError = state.emailError != null,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    )
                    RegistrationTextField(
                        value = state.phone,
                        onValueChange = onPhoneChanged,
                        label = "Phone",
                        supporting = state.phoneError ?: "International format is recommended.",
                        isError = state.phoneError != null,
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    )
                    RegistrationTextField(
                        value = state.namespaceName,
                        onValueChange = onNamespaceNameChanged,
                        label = "Namespace Name",
                        supporting = state.namespaceNameError ?: "Shown in the stored registration state.",
                        isError = state.namespaceNameError != null,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    )
                    RegistrationTextField(
                        value = state.apiUrl,
                        onValueChange = onApiUrlChanged,
                        label = "API URL",
                        supporting = state.apiUrlError ?: "Base URL with port only, for example http://192.168.178.20:3000. The app appends /api/v1/account.",
                        isError = state.apiUrlError != null,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    )
                    if (!state.errorMessage.isNullOrBlank()) {
                        StatusBanner(
                            title = "Registration failed",
                            message = state.errorMessage,
                            tone = BannerTone.Error,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PrimaryActionButton(
                            text = "Register Device",
                            onClick = onRegister,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "What happens next",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    ChecklistLine("A 4096-bit RSA keypair is kept inside Android Keystore.")
                    ChecklistLine("A Base64 X.509 public key and RSA-OAEP encrypted DEK are generated locally.")
                    ChecklistLine("The returned account ID is stored securely after success.")
                    ChecklistLine("Sensitive crypto values are never rendered or logged in the UI.")
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    PillLabel(text = "Please wait")
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "The app is working locally with keystore material or waiting for the backend response.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    deviceId: String,
    namespaceName: String,
    email: String,
    fcmSyncState: FcmSyncUiState,
    newDeviceRegistrationState: NewDeviceRegistrationUiState,
    onRetryFcmSync: () -> Unit,
    onReadQrCode: (String) -> Unit,
    onClearNewDeviceRegistrationState: () -> Unit,
) {
    val context = LocalContext.current
    val notificationManager = remember(context) { NotificationManagerCompat.from(context) }
    val isFcmReady = fcmSyncState is FcmSyncUiState.Success
    var isScannerOpen by rememberSaveable { mutableStateOf(false) }
    var openScannerAfterPermissionGrant by rememberSaveable { mutableStateOf(false) }
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
        if (granted && openScannerAfterPermissionGrant) {
            isScannerOpen = true
        }
        if (!granted) {
            openScannerAfterPermissionGrant = false
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

    LaunchedEffect(cameraPermissionGranted) {
        if (!cameraPermissionGranted) {
            isScannerOpen = false
        }
    }

    LaunchedEffect(notificationsPermissionGranted) {
        notificationsEnabled = notificationManager.areNotificationsEnabled()
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(20.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            HeroPanel(
                eyebrow = if (isFcmReady) "Registered + FCM Ready" else "Registered",
                title = if (isFcmReady) "Channel Active" else "Device Registered",
                body = if (isFcmReady) {
                    "The device is registered"
                } else {
                    "The primary device registration state is present and routed past the security gate."
                },
            )
            Spacer(modifier = Modifier.height(18.dp))
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatusBanner(
                        title = if (isFcmReady) "Registration and FCM active" else "Registration active",
                        message = if (isFcmReady) {
                            "Stored account state is available and the device is ready to receive push-targeted backend events."
                        } else {
                            "Stored account state is available and the device can continue from the registered flow."
                        },
                        tone = BannerTone.Success,
                    )
                    if (isFcmReady) {
                        StatusBanner(
                            title = "New view unlocked",
                            message = "This screen is shown only after successful registration and a valid FCM token sync.",
                            tone = BannerTone.Info,
                        )
                    }
                    if (!notificationsEnabled || !notificationsPermissionGranted) {
                        StatusBanner(
                            title = "Notifications disabled",
                            message = "Push alerts may not appear while the app is in background. Enable app notifications in Android settings and allow notification permission.",
                            tone = BannerTone.Error,
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
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("Enable notifications")
                        }
                    }
                    FcmStatusCard(
                        fcmSyncState = fcmSyncState,
                        onRetryFcmSync = onRetryFcmSync,
                    )
                    StatusBanner(
                        title = "Read registration QR",
                        message = "Scan the target device QR code to create and submit a NewDeviceRegistrationDto to the backend root-device endpoint.",
                        tone = BannerTone.Info,
                    )
                    Button(
                        onClick = {
                            onClearNewDeviceRegistrationState()
                            if (cameraPermissionGranted) {
                                cameraPermissionError = null
                                openScannerAfterPermissionGrant = false
                                isScannerOpen = true
                            } else {
                                openScannerAfterPermissionGrant = true
                                hasRequestedCameraPermission = true
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Read QR Code")
                    }
                    cameraPermissionError?.let { message ->
                        StatusBanner(
                            title = "Camera access required",
                            message = message,
                            tone = BannerTone.Error,
                        )
                    }
                    when (newDeviceRegistrationState) {
                        NewDeviceRegistrationUiState.Idle -> Unit
                        NewDeviceRegistrationUiState.InProgress -> StatusBanner(
                            title = "Registering scanned device",
                            message = "The app is fetching the root device DEK, decrypting it locally, re-encrypting it for the scanned public key, and sending the DTO to the backend.",
                            tone = BannerTone.Info,
                        )
                        is NewDeviceRegistrationUiState.Success -> StatusBanner(
                            title = "New device registered",
                            message = "The QR flow completed successfully for device ${newDeviceRegistrationState.deviceId}.",
                            tone = BannerTone.Success,
                        )
                        is NewDeviceRegistrationUiState.Error -> StatusBanner(
                            title = "QR registration failed",
                            message = newDeviceRegistrationState.message,
                            tone = BannerTone.Error,
                        )
                    }
                    DetailRow(label = "Device ID", value = deviceId)
                    DetailRow(label = "Namespace", value = namespaceName)
                    DetailRow(label = "Email", value = email)
                }
            }
        }

        if (isScannerOpen) {
            QrScannerSheet(
                onDismiss = { isScannerOpen = false },
                onPayloadScanned = { payload ->
                    isScannerOpen = false
                    onReadQrCode(payload)
                },
                onScannerError = { message ->
                    isScannerOpen = false
                    cameraPermissionError = message
                },
            )
        }
    }
}

@Composable
private fun AppBackground(content: @Composable ColumnScope.() -> Unit) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.background,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        content = content,
    )
}

@Composable
internal fun HeroPanel(
    eyebrow: String,
    title: String,
    body: String,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(32.dp),
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillLabel(text = eyebrow)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun PillLabel(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RegistrationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String,
    isError: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = isError,
        supportingText = { Text(supporting) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.32f),
            errorContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
        ),
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(vertical = 15.dp),
    ) {
        Text(text)
    }
}

private enum class BannerTone {
    Error,
    Warning,
    Info,
    Success,
}

@Composable
private fun StatusBanner(
    title: String,
    message: String,
    tone: BannerTone,
) {
    val container = when (tone) {
        BannerTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        BannerTone.Warning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
        BannerTone.Info -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        BannerTone.Success -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
    }
    val border = when (tone) {
        BannerTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
        BannerTone.Warning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
        BannerTone.Info -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        BannerTone.Success -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChecklistLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .size(8.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FcmStatusCard(
    fcmSyncState: FcmSyncUiState,
    onRetryFcmSync: () -> Unit,
) {
    val (title, message, tone) = when (fcmSyncState) {
        FcmSyncUiState.Idle -> Triple(
            "FCM sync pending",
            "No valid FCM token is stored yet. The app will request and upload one for this registered device.",
            BannerTone.Warning,
        )
        FcmSyncUiState.InProgress -> Triple(
            "FCM sync running",
            "The app is currently requesting or uploading the FCM token.",
            BannerTone.Info,
        )
        is FcmSyncUiState.Success -> Triple(
            "FCM token active",
            "Current token preview: ${fcmSyncState.tokenPreview}",
            BannerTone.Success,
        )
        is FcmSyncUiState.Error -> Triple(
            "FCM sync failed",
            fcmSyncState.message,
            BannerTone.Error,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusBanner(
            title = title,
            message = message,
            tone = tone,
        )
        if (fcmSyncState !is FcmSyncUiState.InProgress) {
            Button(
                onClick = onRetryFcmSync,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("Retry FCM Sync")
            }
        }
    }
}

private sealed class AuthAvailability {
    data object Available : AuthAvailability()
    data class Unavailable(val message: String) : AuthAvailability()
}

private fun canAuthenticate(context: android.content.Context): AuthAvailability {
    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        val biometricResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (biometricResult == BiometricManager.BIOMETRIC_SUCCESS) {
            return AuthAvailability.Available
        }

        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isDeviceSecure == true) {
            return AuthAvailability.Available
        }
    }

    return when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.Available
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> AuthAvailability.Unavailable("This device has no biometric hardware.")
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> AuthAvailability.Unavailable("Biometric hardware is currently unavailable.")
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthAvailability.Unavailable("No biometric or device credential is enrolled.")
        else -> AuthAvailability.Unavailable("Authentication is unavailable on this device.")
    }
}

private fun buildPromptInfo(): BiometricPrompt.PromptInfo {
    val builder = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock ZeroIntrusion")
        .setSubtitle("Confirm your identity to continue")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
    } else {
        builder.setDeviceCredentialAllowed(true)
    }

    return builder.build()
}