package com.zerointrusion.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zerointrusion.domain.notification.NotificationDecision
import com.zerointrusion.presentation.app.QrNamespaceRegistrationUiState
import com.zerointrusion.presentation.app.AppViewModel
import com.zerointrusion.presentation.authentication.DesktopSessionsScreen
import com.zerointrusion.presentation.auth.SecurityScreen
import com.zerointrusion.presentation.common.LoadingScreen
import com.zerointrusion.presentation.connecteddevices.ConnectedDevicesScreen
import com.zerointrusion.presentation.connecteddevices.DeviceRemovalScreen
import com.zerointrusion.presentation.deviceinformation.DevicesScreen
import com.zerointrusion.presentation.deviceinformation.DeviceInformationScreen
import com.zerointrusion.presentation.deviceinformation.DeviceInformationUiModel
import com.zerointrusion.presentation.home.PostLoginHomeScreen
import com.zerointrusion.presentation.namespaces.NamespacesScreen
import com.zerointrusion.presentation.qrread.QrNamespaceDeviceSelectionScreen
import com.zerointrusion.presentation.qrread.QrReadScreen
import com.zerointrusion.presentation.settings.RecoveryConnectionScreen
import com.zerointrusion.presentation.settings.AccountsScreen
import com.zerointrusion.presentation.settings.SettingsScreen
import com.zerointrusion.presentation.settings.SettingsConnectedDevicesScreen
import com.zerointrusion.presentation.registration.RegistrationScreen
import com.zerointrusion.presentation.registration.RegistrationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SHARED_ACCOUNT_DETAILS_POLL_INTERVAL_MILLIS = 30_000L

private val EMPTY_CONNECTED_DEVICE_GROUPS = emptyList<com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup>()

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

    LaunchedEffect(appState.homeNavigationRequestKey) {
        if (appState.notificationOverlay.notification != null && navController.currentDestination?.route != AppRoute.Home.route) {
            navController.navigate(AppRoute.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(appState.qrNamespaceRegistrationState) {
        if (
            appState.qrNamespaceRegistrationState is QrNamespaceRegistrationUiState.AwaitingDeviceSelection &&
            navController.currentDestination?.route != AppRoute.QrNamespaceDeviceSelection.route
        ) {
            navController.navigate(AppRoute.QrNamespaceDeviceSelection.route)
        }
    }

    LaunchedEffect(appState.isAuthenticated, appState.isRegistered) {
        if (!appState.isAuthenticated || !appState.isRegistered) {
            return@LaunchedEffect
        }

        appViewModel.refreshAccountDetailsSnapshot()
        appViewModel.refreshLiveDesktopSessions()
        while (isActive) {
            delay(SHARED_ACCOUNT_DETAILS_POLL_INTERVAL_MILLIS)
            appViewModel.refreshAccountDetailsSnapshot()
            appViewModel.refreshLiveDesktopSessions()
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
                        onAdditionalApiUrlChanged = registrationViewModel::onAdditionalApiUrlChanged,
                        onThirdApiUrlChanged = registrationViewModel::onThirdApiUrlChanged,
                        onRegister = registrationViewModel::register,
                        onRegisterNewMobile = registrationViewModel::registerNewMobileToExistingAccount,
                    )
                }
            }
            composable(AppRoute.Home.route) {
                PostLoginHomeScreen(
                    deviceId = appState.registrationState?.deviceId.orEmpty(),
                    namespaceName = appState.registrationState?.namespaceName.orEmpty(),
                    email = appState.registrationState?.email.orEmpty(),
                    namespaces = appState.namespaces,
                    liveDesktopSessions = appState.liveDesktopSessions,
                    liveDesktopSessionRevokingAttestationId = appState.liveDesktopSessionRevokingAttestationId,
                    liveDesktopSessionRevokeErrorMessage = appState.liveDesktopSessionRevokeErrorMessage,
                    notification = appState.notificationOverlay.notification,
                    notificationError = appState.notificationOverlay.mismatchError,
                    qrNamespaceRegistrationState = appState.qrNamespaceRegistrationState,
                    onOpenSettings = {
                        navController.navigate(AppRoute.Settings.route)
                    },
                    onOpenQrRead = {
                        navController.navigate(AppRoute.QrRead.route)
                    },
                    onClearQrNamespaceRegistrationState = appViewModel::clearQrNamespaceRegistrationState,
                    onRevokeDesktopSession = appViewModel::revokeDesktopSession,
                    onAllowNotification = {
                        appViewModel.onNotificationDecision(NotificationDecision.Accept)
                    },
                    onDeclineNotification = {
                        appViewModel.onNotificationDecision(NotificationDecision.Decline)
                    },
                    onDismissNotificationError = appViewModel::dismissNotificationMismatch,
                )
            }
            composable(AppRoute.Settings.route) {
                SettingsScreen(
                    onOpenAccounts = {
                        navController.navigate(AppRoute.Accounts.route)
                    },
                    onOpenRecoveryConnection = {
                        navController.navigate(AppRoute.RecoveryConnection.route)
                    },
                    onOpenDevices = {
                        navController.navigate(AppRoute.Devices.route)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.Accounts.route) {
                AccountsScreen(
                    namespaces = appState.namespaces,
                    deleting = appState.accountDeletionInProgress,
                    errorMessage = appState.accountDeletionErrorMessage,
                    onDeleteAccount = appViewModel::deleteAccount,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.RecoveryConnection.route) {
                RecoveryConnectionScreen(
                    email = appState.registrationState?.email.orEmpty(),
                    phone = appState.registrationState?.phone.orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.SettingsConnectedDevices.route) {
                SettingsConnectedDevicesScreen(
                    deviceInformation = DeviceInformationUiModel(
                        accountId = appState.registrationState?.accountId.orEmpty(),
                        deviceId = appState.registrationState?.deviceId.orEmpty(),
                        deviceName = appState.registrationState?.deviceName.orEmpty(),
                        namespaceName = appState.registrationState?.namespaceName.orEmpty(),
                        namespaceId = appState.registrationState?.namespaceId.orEmpty(),
                        namespaceUrl = appState.registrationState?.namespaceUrl.orEmpty(),
                        email = appState.registrationState?.email.orEmpty(),
                    ),
                    namespaces = appState.namespaces,
                    connectedDeviceGroups = (appState.connectedDevicesState as? com.zerointrusion.presentation.app.ConnectedDevicesUiState.Success)
                        ?.groups
                        ?: EMPTY_CONNECTED_DEVICE_GROUPS,
                    namespaceManagementErrorMessage = appState.namespaceManagementErrorMessage,
                    onDeleteNamespace = appViewModel::deleteNamespace,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.DeviceInformation.route) {
                DeviceInformationScreen(
                    model = DeviceInformationUiModel(
                        accountId = appState.registrationState?.accountId.orEmpty(),
                        deviceId = appState.registrationState?.deviceId.orEmpty(),
                        deviceName = appState.registrationState?.deviceName.orEmpty(),
                        namespaceName = appState.registrationState?.namespaceName.orEmpty(),
                        namespaceId = appState.registrationState?.namespaceId.orEmpty(),
                        namespaceUrl = appState.registrationState?.namespaceUrl.orEmpty(),
                        email = appState.registrationState?.email.orEmpty(),
                    ),
                    namespaces = appState.namespaces,
                    errorMessage = appState.namespaceSelectionErrorMessage,
                    onNamespaceSelected = appViewModel::setSelectedNamespace,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.Devices.route) {
                DevicesScreen(
                    onOpenDeviceInformation = {
                        navController.navigate(AppRoute.DeviceInformation.route)
                    },
                    onOpenRemoveDevices = {
                        navController.navigate(AppRoute.DeviceRemoval.route)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.DeviceRemoval.route) {
                DeviceRemovalScreen(
                    currentMobileDeviceId = appState.registrationState?.deviceId.orEmpty(),
                    state = appState.deviceRemovalState,
                    onLoad = appViewModel::loadRemovableDevices,
                    onDelete = appViewModel::deleteDevice,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.QrRead.route) {
                QrReadScreen(
                    qrNamespaceRegistrationState = appState.qrNamespaceRegistrationState,
                    onClearQrNamespaceRegistrationState = appViewModel::clearQrNamespaceRegistrationState,
                    onBack = {
                        appViewModel.clearQrNamespaceRegistrationState()
                        navController.popBackStack()
                    },
                    onPayloadScanned = { payload ->
                        appViewModel.registerNamespaceFromQr(payload)
                    },
                )
            }
            composable(AppRoute.QrNamespaceDeviceSelection.route) {
                QrNamespaceDeviceSelectionScreen(
                    qrNamespaceRegistrationState = appState.qrNamespaceRegistrationState,
                    onConnectedDeviceSelected = appViewModel::confirmQrNamespaceDeviceSelection,
                    onBack = {
                        appViewModel.clearQrNamespaceRegistrationState()
                        navController.popBackStack()
                    },
                )
            }
            composable(AppRoute.ConnectedDevices.route) {
                ConnectedDevicesScreen(
                    accountId = appState.registrationState?.accountId.orEmpty(),
                    state = appState.connectedDevicesState,
                    onRetry = appViewModel::loadConnectedDevices,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.DesktopSessions.route) {
                LaunchedEffect(Unit) {
                    appViewModel.loadDesktopSessions()
                }
                DesktopSessionsScreen(
                    accountId = appState.registrationState?.accountId.orEmpty(),
                    mobileDeviceId = appState.registrationState?.deviceId.orEmpty(),
                    state = appState.desktopSessionsState,
                    onRetry = appViewModel::loadDesktopSessions,
                    onRevokeSession = appViewModel::revokeDesktopSession,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.Namespaces.route) {
                LaunchedEffect(Unit) {
                    appViewModel.refreshAccountDetailsSnapshot()
                }
                NamespacesScreen(
                    namespaces = appState.namespaces,
                    gatewayAccountId = appState.registrationState?.accountId.orEmpty(),
                    gatewayNamespaceId = appState.registrationState?.namespaceId.orEmpty(),
                    gatewayNamespaceName = appState.registrationState?.namespaceName.orEmpty(),
                    gatewayNamespaceUrl = appState.registrationState?.namespaceUrl.orEmpty(),
                    errorMessage = appState.namespaceManagementErrorMessage,
                    onDeleteNamespace = appViewModel::deleteNamespace,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}