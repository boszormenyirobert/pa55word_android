package com.zerointrusion.presentation.registration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun desktopLogoRendersWhiteLetteringAndGreenEmblem() {
        composeRule.setContent {
            RegistrationOptionsScreen(onNewAccount = {}, onAddMobile = {}, onRecovery = {})
        }

        val pixels = composeRule.onNodeWithContentDescription("pa55word logo")
            .assertIsDisplayed().captureToImage().toPixelMap()
        var hasWhite = false
        var hasGreen = false
        for (row in 0 until pixels.height step 4) {
            for (column in 0 until pixels.width step 4) {
                val color = pixels[column, row]
                hasWhite = hasWhite || (color.red > 0.8f && color.green > 0.8f && color.blue > 0.8f)
                hasGreen = hasGreen || (color.green > 0.5f && color.red < 0.7f && color.blue < 0.2f)
            }
        }
        assertTrue("Logo lettering should be visible", hasWhite)
        assertTrue("Logo emblem should be visible", hasGreen)
        var digitLeft = pixels.width
        var digitRight = -1
        var digitTop = pixels.height
        var digitBottom = -1
        var prefixRight = -1
        for (row in 0 until pixels.height) {
            for (column in 0 until pixels.width) {
                val color = pixels[column, row]
                val logoX = column * 1000f / pixels.width
                val logoY = row * 240f / pixels.height
                val distanceX = logoX - 405f
                val distanceY = logoY - 120f
                if (color.green > 0.5f && color.red < 0.7f && color.blue < 0.2f &&
                    distanceX * distanceX + distanceY * distanceY < 100f * 100f
                ) {
                    digitLeft = minOf(digitLeft, column)
                    digitRight = maxOf(digitRight, column)
                    digitTop = minOf(digitTop, row)
                    digitBottom = maxOf(digitBottom, row)
                }
                if (logoX < 291f && color.red > 0.8f && color.green > 0.8f && color.blue > 0.8f) {
                    prefixRight = maxOf(prefixRight, column)
                }
            }
        }
        assertTrue("The 55 must be visible inside the circle", digitRight >= digitLeft)
        assertEquals("The 55 must be horizontally centered", 405f, (digitLeft + digitRight) * 500f / pixels.width, 4f)
        assertEquals("The 55 must be vertically centered", 120f, (digitTop + digitBottom) * 120f / pixels.height, 4f)
        val prefixGap = 291f - prefixRight * 1000f / pixels.width
        assertTrue("The pa-to-circle gap must remain compact: $prefixGap", prefixGap in 6f..24f)
        val background = composeRule.onNodeWithTag("registration-options").captureToImage().toPixelMap()
        listOf(
            background[0, 0],
            background[background.width - 1, 0],
            background[0, background.height - 1],
            background[background.width - 1, background.height - 1],
        ).forEach { color ->
            assertEquals(0f, color.red, 0.001f)
            assertEquals(0f, color.green, 0.001f)
            assertEquals(0f, color.blue, 0.001f)
        }
    }

    @Test
    fun newAccountOptionInvokesOnlyNewAccount() {
        assertOptionSelection("New account registration")
    }

    @Test
    fun addMobileOptionInvokesOnlyAddMobile() {
        assertOptionSelection("Add new mobile to existing account")
    }

    @Test
    fun recoveryOptionInvokesOnlyRecovery() {
        assertOptionSelection("Recovery")
    }

    @Test
    fun newAccountFormPreservesAllFieldsAndActions() {
        assertRegistrationForm(isExistingAccount = false)
    }

    @Test
    fun addMobileFormPreservesAllFieldsAndActions() {
        assertRegistrationForm(isExistingAccount = true)
    }

    @Test
    fun awaitingMobileApprovalShowsQrDisablesConfirmAndAllowsBack() {
        var backCount = 0
        composeRule.setContent {
            RegistrationScreen(
                state = RegistrationUiState(
                    isAwaitingMobileRegistration = true,
                    mobileRegistrationQrPayload = "test-mobile-registration",
                ),
                onEmailChanged = {},
                onPhoneChanged = {},
                onNamespaceNameChanged = {},
                onApiUrlChanged = {},
                onAdditionalApiUrlChanged = {},
                onThirdApiUrlChanged = {},
                isExistingAccount = true,
                onConfirm = {},
                onBack = { backCount++ },
            )
        }

        composeRule.onNodeWithText("Confirm").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("New mobile registration QR code")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for approval...").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Back").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    private fun assertOptionSelection(option: String) {
        val selected = mutableListOf<String>()
        composeRule.setContent {
            RegistrationOptionsScreen(
                onNewAccount = { selected.add("New account registration") },
                onAddMobile = { selected.add("Add new mobile to existing account") },
                onRecovery = { selected.add("Recovery") },
            )
        }

        composeRule.onNodeWithText("New account registration").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("pa55word logo").assertIsDisplayed()
        composeRule.onNodeWithText("Add new mobile to existing account").assertIsDisplayed()
        composeRule.onNodeWithText("Recovery").assertIsDisplayed()
        listOf("New account registration", "Add new mobile to existing account", "Recovery").forEach { label ->
            composeRule.onNodeWithText(label).assertHeightIsAtLeast(80.dp)
        }
        composeRule.onNodeWithText("Email").assertDoesNotExist()
        composeRule.onNodeWithText("Confirm").assertDoesNotExist()
        composeRule.onNodeWithText(option).performClick()
        composeRule.runOnIdle { assertEquals(listOf(option), selected) }
    }

    private fun assertRegistrationForm(isExistingAccount: Boolean) {
        var confirmedState: RegistrationUiState? = null
        var backCount = 0
        composeRule.setContent {
            var state by remember { mutableStateOf(RegistrationUiState()) }
            RegistrationScreen(
                state = state,
                onEmailChanged = { state = state.copy(email = it) },
                onPhoneChanged = { state = state.copy(phone = it) },
                onNamespaceNameChanged = { state = state.copy(namespaceName = it) },
                onApiUrlChanged = { state = state.copy(apiUrl = it) },
                onAdditionalApiUrlChanged = { state = state.copy(additionalApiUrl = it) },
                onThirdApiUrlChanged = { state = state.copy(thirdApiUrl = it) },
                isExistingAccount = isExistingAccount,
                onConfirm = { confirmedState = state },
                onBack = { backCount++ },
            )
        }

        val title = if (isExistingAccount) "Add new mobile to existing account" else "New account registration"
        composeRule.onNodeWithText(title).assertIsDisplayed()
        val values = linkedMapOf(
            "Email" to "test@example.com",
            "Phone" to "+36123456789",
            "Namespace Name" to "Test namespace",
            "API URL" to "https://primary.example.com",
            "Additional API URL (optional)" to "https://second.example.com",
            "Third API URL (optional)" to "https://third.example.com",
        )
        values.forEach { (label, value) ->
            composeRule.onNodeWithText(label).performScrollTo().performTextReplacement(value)
            composeRule.onNodeWithText(label).assertTextContains(value)
        }
        composeRule.onNodeWithText("Confirm").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(
                RegistrationUiState(
                    email = values.getValue("Email"),
                    phone = values.getValue("Phone"),
                    namespaceName = values.getValue("Namespace Name"),
                    apiUrl = values.getValue("API URL"),
                    additionalApiUrl = values.getValue("Additional API URL (optional)"),
                    thirdApiUrl = values.getValue("Third API URL (optional)"),
                ),
                confirmedState,
            )
        }
        composeRule.onNodeWithText("Back").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }
}