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
import com.zerointrusion.presentation.app.AppViewModel
import com.zerointrusion.presentation.auth.SecurityScreen
import com.zerointrusion.presentation.common.LoadingScreen
import com.zerointrusion.presentation.connecteddevices.ConnectedDevicesScreen
import com.zerointrusion.presentation.deviceinformation.DeviceInformationScreen
import com.zerointrusion.presentation.deviceinformation.DeviceInformationUiModel
import com.zerointrusion.presentation.home.PostLoginHomeScreen
import com.zerointrusion.presentation.namespaces.NamespacesScreen
import com.zerointrusion.presentation.qrread.QrReadScreen
import com.zerointrusion.presentation.registration.RegistrationScreen
import com.zerointrusion.presentation.registration.RegistrationViewModel

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
                PostLoginHomeScreen(
                    deviceId = appState.registrationState?.deviceId.orEmpty(),
                    namespaceName = appState.registrationState?.namespaceName.orEmpty(),
                    email = appState.registrationState?.email.orEmpty(),
                    notification = appState.notificationOverlay.notification,
                    notificationError = appState.notificationOverlay.mismatchError,
                    qrNamespaceRegistrationState = appState.qrNamespaceRegistrationState,
                    onOpenDeviceInformation = {
                        navController.navigate(AppRoute.DeviceInformation.route)
                    },
                    onOpenQrRead = {
                        navController.navigate(AppRoute.QrRead.route)
                    },
                    onOpenConnectedDevices = {
                        navController.navigate(AppRoute.ConnectedDevices.route)
                    },
                    onOpenNamespaces = {
                        navController.navigate(AppRoute.Namespaces.route)
                    },
                    onClearQrNamespaceRegistrationState = appViewModel::clearQrNamespaceRegistrationState,
                    onAllowNotification = {
                        appViewModel.onNotificationDecision(NotificationDecision.Accept)
                    },
                    onDeclineNotification = {
                        appViewModel.onNotificationDecision(NotificationDecision.Decline)
                    },
                    onDismissNotificationError = appViewModel::dismissNotificationMismatch,
                )
            }
            composable(AppRoute.DeviceInformation.route) {
                DeviceInformationScreen(
                    model = DeviceInformationUiModel(
                        accountId = appState.registrationState?.accountId.orEmpty(),
                        deviceId = appState.registrationState?.deviceId.orEmpty(),
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
            composable(AppRoute.QrRead.route) {
                QrReadScreen(
                    onBack = { navController.popBackStack() },
                    onPayloadScanned = { payload ->
                        appViewModel.registerNamespaceFromQr(payload)
                        navController.popBackStack()
                    },
                )
            }
            composable(AppRoute.ConnectedDevices.route) {
                LaunchedEffect(Unit) {
                    appViewModel.loadConnectedDevices()
                }
                ConnectedDevicesScreen(
                    accountId = appState.registrationState?.accountId.orEmpty(),
                    state = appState.connectedDevicesState,
                    onRetry = appViewModel::loadConnectedDevices,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.Namespaces.route) {
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