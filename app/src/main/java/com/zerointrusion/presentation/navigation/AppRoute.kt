package com.zerointrusion.presentation.navigation

import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.ServicePlan
import java.net.URI

sealed class AppRoute(val route: String) {
    data object Loading : AppRoute("loading")

    data object Security : AppRoute("security")

    data object Registration : AppRoute("registration")

    data object NewAccountRegistration : AppRoute("new-account-registration")

    data object AddMobile : AppRoute("add-mobile")

    data object Recovery : AppRoute("recovery")

    data object Home : AppRoute("home")

    data object Settings : AppRoute("settings")

    data object ServicePlan : AppRoute("service-plan")

    data object BasicServicePlan : AppRoute("service-plan/basic")

    data object EnterpriseServicePlan : AppRoute("service-plan/enterprise")

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

internal fun devicesEntryRoute(
    mobileRegistrations: List<MobileNamespaceRegistration>,
    registrationState: RegistrationState?,
): AppRoute {
    if (registrationState?.servicePlan != ServicePlan.ENTERPRISE) {
        return AppRoute.DeviceRemoval
    }
    val mobileUrls =
        mobileRegistrations
            .filter { registration ->
                registration.mobileDeviceId.trim() == registrationState.mobileDeviceId.trim()
            }
            .map { it.namespaceUrl } + registrationState.namespaceUrl
    val productionHost = URI(AppConstants.PRODUCTION_API_BASE_URL).host
    val hasOtherMobileServer =
        mobileUrls.any { url ->
            val host = runCatching { URI(url.trim()).host }.getOrNull()
            !host.isNullOrBlank() && !host.equals(productionHost, ignoreCase = true)
        }
    return if (hasOtherMobileServer) AppRoute.Devices else AppRoute.DeviceRemoval
}
