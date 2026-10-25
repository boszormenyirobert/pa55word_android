package com.zerointrusion.presentation.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.Green
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationStateBoxTest {
    @get:Rule
    val composeRule = createComposeRule()

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