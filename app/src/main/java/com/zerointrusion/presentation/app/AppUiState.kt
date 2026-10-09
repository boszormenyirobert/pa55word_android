package com.zerointrusion.presentation.app

import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.domain.authentication.NamespaceAuthenticationResult
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.RemovableAccountDevice
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
    data class AwaitingDeviceSelection(
        val namespaceName: String,
        val deviceOptions: List<QrNamespaceDeviceOption>,
    ) : QrNamespaceRegistrationUiState()
    data object InProgress : QrNamespaceRegistrationUiState()
    data class Success(
        val message: String,
        val followUpQrPayload: String? = null,
    ) : QrNamespaceRegistrationUiState()
    data class Error(val message: String) : QrNamespaceRegistrationUiState()
}

data class QrNamespaceDeviceOption(
    val accountId: String,
    val deviceId: String,
    val deviceName: String,
)

data class AddDatabaseUiState(
    val name: String = "",
    val url: String = "",
    val nameError: String? = null,
    val urlError: String? = null,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
)

sealed class ConnectedDevicesUiState {
    data object Idle : ConnectedDevicesUiState()
    data object InProgress : ConnectedDevicesUiState()
    data class Success(val groups: List<ConnectedDeviceGroup>) : ConnectedDevicesUiState()
    data class Error(val message: String) : ConnectedDevicesUiState()
}

sealed class DeviceRemovalUiState {
    data object Idle : DeviceRemovalUiState()
    data object InProgress : DeviceRemovalUiState()
    data class Success(
        val devices: List<RemovableAccountDevice>,
        val deletingDeviceId: String? = null,
        val errorMessage: String? = null,
    ) : DeviceRemovalUiState()
    data class Error(val message: String) : DeviceRemovalUiState()
}

sealed class DesktopSessionsUiState {
    data object Idle : DesktopSessionsUiState()
    data object InProgress : DesktopSessionsUiState()
    data class Success(
        val sessions: List<ActiveDesktopSession>,
        val revokingAttestationId: String? = null,
        val message: String? = null,
    ) : DesktopSessionsUiState()
    data class Error(val message: String) : DesktopSessionsUiState()
}

data class NotificationOverlayUiState(
    val notification: BackendPushNotification? = null,
    val mismatchError: String? = null,
    val canRespond: Boolean = false,
    val namespaceResults: List<NamespaceAuthenticationResult> = emptyList(),
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
    val deviceRemovalState: DeviceRemovalUiState = DeviceRemovalUiState.Idle,
    val desktopSessionsState: DesktopSessionsUiState = DesktopSessionsUiState.Idle,
    val liveDesktopSessions: List<ActiveDesktopSession> = emptyList(),
    val liveDesktopSessionRevokingAttestationId: String? = null,
    val liveDesktopSessionRevokeErrorMessage: String? = null,
    val namespaces: List<NamespaceEntry> = emptyList(),
    val accounts: List<NamespaceEntry> = emptyList(),
    val addDatabaseState: AddDatabaseUiState = AddDatabaseUiState(),
    val namespaceSelectionErrorMessage: String? = null,
    val namespaceManagementErrorMessage: String? = null,
    val accountDeletionInProgress: Boolean = false,
    val accountDeletionErrorMessage: String? = null,
    val homeNavigationRequestKey: Long = 0L,
    val notificationOverlay: NotificationOverlayUiState = NotificationOverlayUiState(),
) {
    val currentRoute: String
        get() = when {
            !isInitialized -> AppRoute.Loading.route
            !isAuthenticated -> AppRoute.Security.route
            isRegistered && registrationState?.pendingAdditionalNamespacesScan == true -> AppRoute.QrRead.route
            isRegistered -> AppRoute.Home.route
            else -> AppRoute.Registration.route
        }
}