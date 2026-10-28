package com.zerointrusion.presentation.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
