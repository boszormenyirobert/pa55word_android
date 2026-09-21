package com.zerointrusion.presentation.app

import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.presentation.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUiStateTest {
    @Test
    fun routesBootstrappedMobileToQrReadUntilAdditionalNamespacesComplete() {
        val registrationState = RegistrationState(
            accountId = "account-id",
            mobileDeviceId = "NewPhone01",
            deviceName = "New phone",
            namespaceName = "start",
            namespaceUrl = "https://server-one.example",
            namespaceId = "64917a01df",
            email = "user@example.com",
            phone = "+361234567",
            encDek = "encrypted",
            pendingAdditionalNamespacesScan = true,
        )

        val pendingState = AppUiState(
            isInitialized = true,
            isAuthenticated = true,
            isRegistered = true,
            registrationState = registrationState,
        )

        assertEquals(AppRoute.QrRead.route, pendingState.currentRoute)
        assertEquals(
            AppRoute.Home.route,
            pendingState.copy(
                registrationState = registrationState.copy(pendingAdditionalNamespacesScan = false),
            ).currentRoute,
        )
    }
}