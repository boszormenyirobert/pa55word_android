package com.zerointrusion.presentation.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerointrusion.R
import com.zerointrusion.ui.theme.ZeroIntrusionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun logoStaysCenteredInUpperRegionWhenAuthenticationErrorAppears() {
        var errorMessage by mutableStateOf<String?>(null)
        var viewHeight by mutableStateOf(480.dp)
        var verticalInsets = 0f
        composeRule.setContent {
            ZeroIntrusionTheme {
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                verticalInsets =
                    (insets.calculateTopPadding() + insets.calculateBottomPadding()).value
                Box(modifier = Modifier.requiredSize(320.dp, viewHeight)) {
                    SecurityScreenContent(errorMessage = errorMessage, onRetry = {})
                }
            }
        }
        listOf(480.dp, 640.dp).forEach { height ->
            composeRule.runOnIdle { viewHeight = height }
            listOf<String?>(null, "Authentication failed. Please retry.").forEach { message ->
                composeRule.runOnIdle { errorMessage = message }
                val background =
                    composeRule.onNodeWithTag("security-background").getUnclippedBoundsInRoot()
                val logo =
                    composeRule
                        .onNodeWithContentDescription("pa55word logo")
                        .assertIsDisplayed()
                        .assertWidthIsEqualTo(280.dp)
                        .getUnclippedBoundsInRoot()
                val region =
                    composeRule.onNodeWithTag("security-logo-region").getUnclippedBoundsInRoot()
                assertEquals(
                    (background.bottom.value - background.top.value - verticalInsets - 40f) * 0.65f,
                    (region.bottom - region.top).value,
                    0.5f,
                )
                assertEquals(
                    (region.top.value + region.bottom.value) / 2f,
                    (logo.top.value + logo.bottom.value) / 2f,
                    0.5f,
                )
                assertTrue(
                    "Logo must sit above the screen center",
                    logo.top + logo.bottom < background.top + background.bottom,
                )
                val retry =
                    composeRule
                        .onNodeWithText(if (message == null) "Retry" else "Retry Authentication")
                        .assertIsDisplayed()
                        .getUnclippedBoundsInRoot()
                assertTrue("Retry must remain below the logo", retry.top >= logo.bottom)
                if (message != null) {
                    val banner =
                        composeRule
                            .onNodeWithText("Authentication blocked")
                            .assertIsDisplayed()
                            .getUnclippedBoundsInRoot()
                    assertTrue("Error must remain below the logo", banner.top >= logo.bottom)
                }
            }
        }
    }

    @Test
    fun applicationAndBiometricPromptUsePa55wordBranding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appName = context.getString(R.string.app_name)
        assertEquals("Pa55word", appName)
        assertEquals(
            "Pa55word",
            context.applicationInfo.loadLabel(context.packageManager).toString(),
        )
        assertEquals("Unlock Pa55word", context.getString(R.string.biometric_unlock_title, appName))
    }

    @Test
    fun waitingScreenShowsLogoOnBlackWithoutOldHomeContentAndAllowsRetry() {
        var retries = 0
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(320.dp, 480.dp)) {
                    SecurityScreenContent(errorMessage = null, onRetry = { retries++ })
                }
            }
        }

        val background =
            composeRule.onNodeWithTag("security-background").captureToImage().toPixelMap()
        listOf(
                background[0, 0],
                background[background.width - 1, 0],
                background[0, background.height - 1],
                background[background.width - 1, background.height - 1],
            )
            .forEach { color ->
                assertEquals(0f, color.red, 0.001f)
                assertEquals(0f, color.green, 0.001f)
                assertEquals(0f, color.blue, 0.001f)
            }
        val logo =
            composeRule
                .onNodeWithContentDescription("pa55word logo")
                .assertIsDisplayed()
                .assertWidthIsEqualTo(280.dp)
                .captureToImage()
                .toPixelMap()
        var hasWhite = false
        var hasGreen = false
        for (row in 0 until logo.height step 2) {
            for (column in 0 until logo.width step 2) {
                val color = logo[column, row]
                hasWhite = hasWhite || (color.red > 0.8f && color.green > 0.8f && color.blue > 0.8f)
                hasGreen = hasGreen || (color.green > 0.5f && color.red < 0.7f && color.blue < 0.2f)
            }
        }
        assertTrue("Logo lettering must be visible", hasWhite)
        assertTrue("Logo emblem must be visible", hasGreen)
        listOf(
                "Home",
                "Device information",
                "Qr-Read",
                "No active notification",
                "Allow",
                "Decline",
            )
            .forEach { label -> composeRule.onNodeWithText(label).assertDoesNotExist() }
        composeRule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun logoGradientCirculatesOnlyAroundRingAndStopsAfterWaiting() {
        composeRule.mainClock.autoAdvance = false
        var awaitingConfirmation by mutableStateOf(true)
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(320.dp, 480.dp)) {
                    SecurityScreenContent(
                        errorMessage = null,
                        onRetry = {},
                        awaitingConfirmation = awaitingConfirmation,
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        val logo = composeRule.onNodeWithContentDescription("pa55word logo")
        val initial = logo.captureToImage().toPixelMap()
        composeRule.mainClock.advanceTimeBy(300)
        val animated = logo.captureToImage().toPixelMap()
        var hasRedRing = false
        var changedPixels = 0
        for (row in 0 until initial.height) {
            for (column in 0 until initial.width) {
                if (initial[column, row].toArgb() != animated[column, row].toArgb()) {
                    changedPixels++
                    val distanceX = column * 1000f / initial.width - 405f
                    val distanceY = row * 240f / initial.height - 120f
                    val radiusSquared = distanceX * distanceX + distanceY * distanceY
                    assertTrue(
                        "Animation must not change the lettering or 55",
                        radiusSquared in 10000f..14400f,
                    )
                    val color = animated[column, row]
                    hasRedRing = hasRedRing || color.red > color.green
                }
            }
        }
        assertTrue("The ring must visibly animate", changedPixels > 0)
        assertTrue("The circulating gradient must include red", hasRedRing)

        val initialEast = initial[(initial.width * 515f / 1000f).toInt(), initial.height / 2]
        val initialWest = initial[(initial.width * 295f / 1000f).toInt(), initial.height / 2]
        assertTrue(
            "The ring must show different colors at once",
            initialWest.red - initialEast.red > 0.2f,
        )
        val animatedSouth =
            animated[
                (animated.width * 405f / 1000f).toInt(), (animated.height * 230f / 240f).toInt()]
        assertEquals(
            "Green must move a quarter-turn clockwise in 300 ms",
            initialEast.red,
            animatedSouth.red,
            0.12f,
        )
        assertEquals(initialEast.green, animatedSouth.green, 0.12f)
        assertEquals(initialEast.blue, animatedSouth.blue, 0.12f)

        composeRule.runOnIdle { awaitingConfirmation = false }
        composeRule.mainClock.advanceTimeByFrame()
        val stopped = logo.captureToImage().toPixelMap()
        composeRule.mainClock.advanceTimeBy(2400)
        val later = logo.captureToImage().toPixelMap()
        for (row in 0 until stopped.height) {
            for (column in 0 until stopped.width) {
                assertEquals(
                    "Logo must stay still after waiting ends",
                    stopped[column, row].toArgb(),
                    later[column, row].toArgb(),
                )
            }
        }
    }

    @Test
    fun authenticationErrorKeepsLogoAndAllowsRetry() {
        var retries = 0
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(320.dp, 480.dp)) {
                    SecurityScreenContent(
                        errorMessage = "Authentication failed. Please retry.",
                        onRetry = { retries++ },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("pa55word logo").assertIsDisplayed()
        composeRule.onNodeWithText("Authentication blocked").assertIsDisplayed()
        composeRule.onNodeWithText("Authentication failed. Please retry.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry Authentication").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }
}
