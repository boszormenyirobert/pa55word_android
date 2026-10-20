package com.zerointrusion.presentation.navigation

sealed class AppRoute(val route: String) {
    data object Loading : AppRoute("loading")
    data object Security : AppRoute("security")
    data object Registration : AppRoute("registration")
    data object Recovery : AppRoute("recovery")
    data object Home : AppRoute("home")
    data object Settings : AppRoute("settings")
    data object Accounts : AppRoute("accounts")
    data object AddDatabase : AppRoute("add-database")
    data object SettingsConnectedDevices : AppRoute("settings-connected-devices")
    data object RecoveryConnection : AppRoute("recovery-connection")
    data object Devices : AppRoute("devices")
    data object DeviceInformation : AppRoute("device-information")
    data object DeviceRemoval : AppRoute("device-removal")
    data object QrRead : AppRoute("qr-read")
    data object QrNamespaceDeviceSelection : AppRoute("qr-namespace-device-selection")
    data object ConnectedDevices : AppRoute("connected-devices")
    data object DesktopSessions : AppRoute("desktop-sessions")
    data object Namespaces : AppRoute("namespaces")
}