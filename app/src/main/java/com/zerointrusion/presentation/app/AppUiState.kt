package com.zerointrusion.presentation.app

import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.notification.BackendPushNotification
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.presentation.navigation.AppRoute

sealed class FcmSyncUiState {
    data object Idle : FcmSyncUiState()
    data object InProgress : FcmSyncUiState()
    data class Success(val tokenPreview: String) : FcmSyncUiState()
    data class Error(val message: String) : FcmSyncUiState()
}

sealed class QrNamespaceRegistrationUiState {
    data object Idle : QrNamespaceRegistrationUiState()
    data object InProgress : QrNamespaceRegistrationUiState()
    data class Success(
        val namespaceName: String,
        val desktopDeviceId: String,
    ) : QrNamespaceRegistrationUiState()
    data class Error(val message: String) : QrNamespaceRegistrationUiState()
}

sealed class ConnectedDevicesUiState {
    data object Idle : ConnectedDevicesUiState()
    data object InProgress : ConnectedDevicesUiState()
    data class Success(val groups: List<ConnectedDeviceGroup>) : ConnectedDevicesUiState()
    data class Error(val message: String) : ConnectedDevicesUiState()
}

data class NotificationOverlayUiState(
    val notification: BackendPushNotification? = null,
    val mismatchError: String? = null,
    val canRespond: Boolean = false,
) {
    val isVisible: Boolean = notification != null
}

data class AppUiState(
    val isInitialized: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isRegistered: Boolean = false,
    val registrationState: RegistrationState? = null,
    val authenticationError: String? = null,
    val fcmSyncState: FcmSyncUiState = FcmSyncUiState.Idle,
    val qrNamespaceRegistrationState: QrNamespaceRegistrationUiState = QrNamespaceRegistrationUiState.Idle,
    val connectedDevicesState: ConnectedDevicesUiState = ConnectedDevicesUiState.Idle,
    val namespaces: List<NamespaceEntry> = emptyList(),
    val notificationOverlay: NotificationOverlayUiState = NotificationOverlayUiState(),
) {
    val currentRoute: String
        get() = when {
            !isInitialized -> AppRoute.Loading.route
            !isAuthenticated -> AppRoute.Security.route
            isRegistered -> AppRoute.Home.route
            else -> AppRoute.Registration.route
        }
}