package com.zerointrusion.presentation.navigation

sealed class AppRoute(val route: String) {
    data object Loading : AppRoute("loading")
    data object Security : AppRoute("security")
    data object Registration : AppRoute("registration")
    data object Home : AppRoute("home")
    data object DeviceInformation : AppRoute("device-information")
    data object QrRead : AppRoute("qr-read")
    data object ConnectedDevices : AppRoute("connected-devices")
    data object Namespaces : AppRoute("namespaces")
}