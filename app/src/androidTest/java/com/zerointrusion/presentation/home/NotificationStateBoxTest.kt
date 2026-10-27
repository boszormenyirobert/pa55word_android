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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.Green
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
    fun connectedInternetShowsGreenCircle() {
        assertInternetIndicator(
            isConnected = true,
            description = "Internet available",
            expectedColor = Green,
        )
    }

    @Test
    fun disconnectedInternetShowsRedCircle() {
        assertInternetIndicator(
            isConnected = false,
            description = "Internet unavailable",
            expectedColor = ActionDeclineRed,
        )
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

    private fun assertInternetIndicator(
        isConnected: Boolean,
        description: String,
        expectedColor: Color,
    ) {
        composeRule.setContent {
            NotificationStateBox(
                notification = null,
                deviceId = "test-device",
                isInternetConnected = isConnected,
            )
        }

        composeRule.onNodeWithText("Internet connection").assertIsDisplayed()
        val circle = composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        val pixels = circle.captureToImage().toPixelMap()
        assertEquals(expectedColor.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
    }
}
