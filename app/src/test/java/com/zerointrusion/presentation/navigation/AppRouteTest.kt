package com.zerointrusion.presentation.navigation

import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.ServicePlan
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteTest {
    @Test
    fun basicPlanAlwaysOpensDeviceRemovalEvenWithOtherMobileServers() {
        listOf(AppConstants.PRODUCTION_API_BASE_URL, "https://other.example.com").forEach { url ->
            assertEquals(
                AppRoute.DeviceRemoval,
                devicesEntryRoute(
                    listOf(mobileRegistration(), mobileRegistration().copy(namespaceUrl = url)),
                    registration().copy(servicePlan = ServicePlan.BASIC),
                ),
            )
        }
    }

    @Test
    fun enterpriseWithOnlyProductionRegistrationOpensDeviceRemoval() {
        assertEquals(AppRoute.DeviceRemoval, devicesEntryRoute(emptyList(), registration()))
    }

    @Test
    fun enterpriseWithMultipleProductionEntriesStillOpensDeviceRemoval() {
        val registrations =
            listOf(
                    AppConstants.PRODUCTION_API_BASE_URL,
                    " https://PROD-API.PA55WORD.COM/// ",
                    "https://prod-api.pa55word.com:443/path",
                    "https://prod-api.pa55word.com/another-path?test=value",
                )
                .mapIndexed { index, url ->
                    mobileRegistration().copy(accountId = "account-$index", namespaceUrl = url)
                }
        assertEquals(AppRoute.DeviceRemoval, devicesEntryRoute(registrations, registration()))
    }

    @Test
    fun enterpriseWithAnotherMobileServerShowsDevicesMenu() {
        assertEquals(
            AppRoute.Devices,
            devicesEntryRoute(
                listOf(
                    mobileRegistration(),
                    mobileRegistration().copy(namespaceUrl = "https://second.example.com"),
                ),
                registration(),
            ),
        )
    }

    @Test
    fun enterpriseWithOnlyOneNonProductionServerStillShowsDevicesMenu() {
        assertEquals(
            AppRoute.Devices,
            devicesEntryRoute(
                emptyList(),
                registration().copy(namespaceUrl = "https://second.example.com"),
            ),
        )
    }

    @Test
    fun otherPhonesDoNotAddMobileServersForTheCurrentPhone() {
        assertEquals(
            AppRoute.DeviceRemoval,
            devicesEntryRoute(
                listOf(
                    mobileRegistration()
                        .copy(
                            mobileDeviceId = "another-phone",
                            namespaceUrl = "https://second.example.com",
                        )
                ),
                registration(),
            ),
        )
    }

    @Test
    fun missingRegistrationDoesNotShowEnterpriseMenu() {
        assertEquals(AppRoute.DeviceRemoval, devicesEntryRoute(emptyList(), null))
    }

    @Test
    fun blankAndInvalidUrlsDoNotCountAsOtherServers() {
        listOf(" ", "not a URL").forEach { url ->
            assertEquals(
                AppRoute.DeviceRemoval,
                devicesEntryRoute(
                    listOf(mobileRegistration().copy(namespaceUrl = url)),
                    registration(),
                ),
            )
        }
    }

    @Test
    fun productionNameInsideAnotherHostDoesNotHideEnterpriseMenu() {
        assertEquals(
            AppRoute.Devices,
            devicesEntryRoute(
                listOf(
                    mobileRegistration()
                        .copy(namespaceUrl = "https://prod-api.pa55word.com.other.example.com")
                ),
                registration(),
            ),
        )
    }

    private fun mobileRegistration() =
        MobileNamespaceRegistration(
            namespaceName = "Pa55word basic",
            namespaceId = "namespace-1",
            namespaceUrl = AppConstants.PRODUCTION_API_BASE_URL,
            accountId = "account-1",
            mobileDeviceId = "mobile-1",
            encDek = "encrypted-dek",
        )

    private fun registration() =
        RegistrationState(
            accountId = "account-1",
            mobileDeviceId = "mobile-1",
            deviceName = "Test phone",
            namespaceName = "Pa55word basic",
            namespaceUrl = AppConstants.PRODUCTION_API_BASE_URL,
            namespaceId = "namespace-1",
            email = "test@example.com",
            encDek = "encrypted-dek",
            servicePlan = ServicePlan.ENTERPRISE,
        )
}
