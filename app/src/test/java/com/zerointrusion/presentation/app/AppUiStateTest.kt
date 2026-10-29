package com.zerointrusion.presentation.app

import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.presentation.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUiStateTest {
    @Test
    fun automaticRefreshDoesNotShowManualProgress() {
        val refresh = HomeRefreshState.IDLE.requested(manually = false)
        assertEquals(HomeRefreshState.BACKGROUND, refresh)
        assertFalse(AppUiState(homeRefreshState = refresh).isHomeRefreshing)
    }

    @Test
    fun manualRefreshShowsProgressIncludingWhenJoiningBackgroundWork() {
        listOf(HomeRefreshState.IDLE, HomeRefreshState.BACKGROUND).forEach { state ->
            val refresh = state.requested(manually = true)
            assertEquals(HomeRefreshState.MANUAL, refresh)
            assertTrue(AppUiState(homeRefreshState = refresh).isHomeRefreshing)
        }
    }

    @Test
    fun automaticRefreshCannotHideActiveManualProgress() {
        assertEquals(HomeRefreshState.MANUAL, HomeRefreshState.MANUAL.requested(manually = false))
        assertFalse(AppUiState().isHomeRefreshing)
    }

    @Test
    fun routesBootstrappedMobileToQrReadUntilAdditionalNamespacesComplete() {
        val registrationState =
            RegistrationState(
                accountId = "account-id",
                mobileDeviceId = "NewPhone01",
                deviceName = "New phone",
                namespaceName = "start",
                namespaceUrl = "https://server-one.example",
                namespaceId = "64917a01df",
                email = "user@example.com",
                encDek = "encrypted",
                pendingAdditionalNamespacesScan = true,
            )

        val pendingState =
            AppUiState(
                isInitialized = true,
                isAuthenticated = true,
                isRegistered = true,
                registrationState = registrationState,
            )

        assertEquals(AppRoute.QrRead.route, pendingState.currentRoute)
        assertEquals(
            AppRoute.Home.route,
            pendingState
                .copy(
                    registrationState =
                        registrationState.copy(pendingAdditionalNamespacesScan = false)
                )
                .currentRoute,
        )
    }
}
