package com.zerointrusion.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.domain.connecteddevices.DatabaseAvailability
import com.zerointrusion.presentation.app.AppUiState
import com.zerointrusion.presentation.app.HomeRefreshState
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.ZeroIntrusionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationStateBoxTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun serverListShowsNamespaceNamesWithoutDomains() {
        val servers =
            listOf(
                DatabaseAvailability(
                    accountId = "primary-account",
                    namespaceName = "Pa55word basic",
                    namespaceUrl = "https://prod-api.pa55word.com",
                    isAvailable = true,
                ),
                DatabaseAvailability(
                    accountId = "secondary-account",
                    namespaceName = "Second server",
                    namespaceUrl = "https://secondary.example.com",
                    isAvailable = false,
                ),
            )
        composeRule.setContent {
            ZeroIntrusionTheme { DatabaseAvailabilityCard(availability = servers) }
        }

        composeRule.onNodeWithText("Database availability").assertIsDisplayed()
        servers.forEach { server ->
            composeRule.onNodeWithText(server.namespaceName).assertIsDisplayed()
            composeRule.onNodeWithText(server.namespaceUrl).assertDoesNotExist()
        }
    }

    @Test
    fun wholeCardRequestsAuthenticationWithoutExtendingValidityAndDisablesAfterExpiry() {
        var remaining by mutableStateOf(12)
        var requests = 0
        composeRule.setContent {
            ZeroIntrusionTheme {
                Column(modifier = Modifier.width(280.dp)) {
                    NotificationStateBox(
                        notification = null,
                        deviceId = "test-device",
                        isInternetConnected = true,
                        biometricRemainingSeconds = remaining,
                        onRenewBiometrics = { requests++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("00:12").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Biometric validity", useUnmergedTree = true).performTouchInput {
            click()
        }
        composeRule.onNodeWithText("test-device", useUnmergedTree = true).performTouchInput {
            click()
        }
        composeRule.onNodeWithText("00:12").performTouchInput {
            click(Offset(12.dp.toPx(), height - 12.dp.toPx()))
        }
        composeRule.runOnIdle { assertEquals(4, requests) }
        composeRule.onNodeWithText("00:12").assertIsDisplayed()
        composeRule.runOnIdle { remaining = 0 }
        composeRule.onNodeWithText("00:00").assertIsNotEnabled().performTouchInput { click() }
        composeRule.onNodeWithText("test-device", useUnmergedTree = true).performTouchInput {
            click()
        }
        composeRule.runOnIdle { assertEquals(4, requests) }
    }

    @Test
    fun connectedInternetShowsLiveBiometricCountdownWithoutNetworkNotice() {
        var remaining by mutableStateOf(300)
        composeRule.setContent {
            ZeroIntrusionTheme {
                Column(modifier = Modifier.width(280.dp)) {
                    NotificationStateBox(
                        notification = null,
                        deviceId = "test-device",
                        isInternetConnected = true,
                        biometricRemainingSeconds = remaining,
                    )
                }
            }
        }
        composeRule.onNodeWithText("Biometric validity").assertIsDisplayed()
        val initialBounds =
            composeRule.onNodeWithText("05:00").assertIsDisplayed().getUnclippedBoundsInRoot()
        composeRule.onNodeWithText("Internet connection").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Internet available").assertDoesNotExist()
        composeRule.onNodeWithText("No internet connection").assertDoesNotExist()
        composeRule.runOnIdle { remaining = 299 }
        val updatedBounds =
            composeRule.onNodeWithText("04:59").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(initialBounds, updatedBounds)
        composeRule.runOnIdle { remaining = -1 }
        composeRule.onNodeWithText("00:00").assertIsDisplayed()
    }

    @Test
    fun offlineWarningShowsAlongsideCountdownAndDisappearsWhenConnected() {
        var connected by mutableStateOf(false)
        composeRule.setContent {
            ZeroIntrusionTheme {
                NotificationStateBox(
                    notification = null,
                    deviceId = "test-device",
                    isInternetConnected = connected,
                    biometricRemainingSeconds = 61,
                )
            }
        }
        composeRule.onNodeWithText("01:01").assertIsDisplayed()
        composeRule.onNodeWithText("No internet connection").assertIsDisplayed()
        val pixels =
            composeRule
                .onNodeWithContentDescription("No internet connection", useUnmergedTree = true)
                .assertIsDisplayed()
                .captureToImage()
                .toPixelMap()
        assertEquals(
            ActionDeclineRed.toArgb(),
            pixels[pixels.width / 2, pixels.height / 2].toArgb(),
        )
        composeRule.runOnIdle { connected = true }
        composeRule.onNodeWithText("No internet connection").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("No internet connection").assertDoesNotExist()
        composeRule.onNodeWithText("01:01").assertIsDisplayed()
    }

    @Test
    fun refreshButtonRemainsIdleDuringBackgroundWorkAndShowsManualProgress() {
        var refreshState by mutableStateOf(HomeRefreshState.BACKGROUND)
        var requests = 0
        composeRule.setContent {
            ZeroIntrusionTheme {
                HomeRefreshButton(
                    isRefreshing = AppUiState(homeRefreshState = refreshState).isHomeRefreshing,
                    onRefresh = {
                        requests++
                        refreshState = refreshState.requested(manually = true)
                    },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Refreshing").assertDoesNotExist()
        val refresh = composeRule.onNodeWithContentDescription("Refresh servers and live sessions")
        val bounds = refresh.assertIsDisplayed().getUnclippedBoundsInRoot()
        refresh.performClick()
        composeRule.onNodeWithContentDescription("Refreshing").assertIsDisplayed()
        composeRule
            .onNode(hasClickAction() and hasAnyDescendant(hasContentDescription("Refreshing")))
            .assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, requests)
            refreshState = HomeRefreshState.IDLE
        }
        val idle = composeRule.onNodeWithContentDescription("Refresh servers and live sessions")
        assertEquals(bounds, idle.assertIsDisplayed().getUnclippedBoundsInRoot())
        idle.performClick()
        composeRule.runOnIdle { assertEquals(2, requests) }
        composeRule.onNodeWithContentDescription("Refreshing").assertIsDisplayed()
    }

    @Test
    fun liveSessionStatusAndRevokeStaySmallAndKeepTheirPositionsWhileRevoking() {
        val session =
            ActiveDesktopSession(
                accountId = "account",
                attestationId = "session",
                deviceId = "desktop",
                deviceName = "Desktop",
                namespaceName = "Server",
                namespaceUrl = "https://example.com",
                issuedAt = "2026-10-27T00:00:00Z",
                expiresAt = "2026-10-28T00:00:00Z",
            )
        var revoking by mutableStateOf<String?>(null)
        val revokedSessions = mutableListOf<ActiveDesktopSession>()
        composeRule.setContent {
            ZeroIntrusionTheme {
                Column(modifier = Modifier.width(280.dp)) {
                    LiveDesktopSessionsCard(
                        sessions = listOf(session),
                        fallbackServerName = "Server",
                        revokingAttestationId = revoking,
                        onRevokeDesktopSession = {
                            revokedSessions.add(it)
                            revoking = it.attestationId
                        },
                    )
                    AppButton(icon = Icons.Default.Check, onClick = {}) { Text("Allow") }
                }
            }
        }

        val status = composeRule.onNodeWithContentDescription("Live session status")
        val revoke = composeRule.onNodeWithText("Revoke")
        composeRule.onNodeWithText("Allow").assertHeightIsEqualTo(48.dp)
        status.assertIsDisplayed().assertHeightIsEqualTo(32.dp)
        revoke.assertIsDisplayed().assertHeightIsEqualTo(32.dp)
        val statusBounds = status.getUnclippedBoundsInRoot()
        val revokeBounds = revoke.getUnclippedBoundsInRoot()
        assertEquals(statusBounds.top, revokeBounds.top)
        assertTrue("Status must be to the left of Revoke", statusBounds.right < revokeBounds.left)

        revoke.performTouchInput { click() }
        val pending =
            composeRule.onNode(
                hasClickAction() and hasAnyDescendant(hasContentDescription("Revoking session"))
            )
        pending.assertIsNotEnabled().assertHeightIsEqualTo(32.dp)
        assertEquals(revokeBounds, pending.getUnclippedBoundsInRoot())
        assertEquals(statusBounds, status.getUnclippedBoundsInRoot())
        pending.performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf(session), revokedSessions) }
    }
}
