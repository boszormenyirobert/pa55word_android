package com.zerointrusion.presentation.registration

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationScreensTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun desktopLogoRendersWhiteLetteringAndGreenEmblem() {
        composeRule.setContent {
            RegistrationOptionsScreen(onNewAccount = {}, onAddMobile = {}, onRecovery = {})
        }

        val pixels =
            composeRule
                .onNodeWithContentDescription("pa55word logo")
                .assertIsDisplayed()
                .captureToImage()
                .toPixelMap()
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
                if (
                    color.green > 0.5f &&
                        color.red < 0.7f &&
                        color.blue < 0.2f &&
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
        assertEquals(
            "The 55 must be horizontally centered",
            405f,
            (digitLeft + digitRight) * 500f / pixels.width,
            4f,
        )
        assertEquals(
            "The 55 must be vertically centered",
            120f,
            (digitTop + digitBottom) * 120f / pixels.height,
            4f,
        )
        val prefixGap = 291f - prefixRight * 1000f / pixels.width
        assertTrue("The pa-to-circle gap must remain compact: $prefixGap", prefixGap in 6f..24f)
        val background =
            composeRule.onNodeWithTag("registration-options").captureToImage().toPixelMap()
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
                state =
                    RegistrationUiState(
                        isAwaitingMobileRegistration = true,
                        mobileRegistrationQrPayload = "test-mobile-registration",
                    ),
                onEmailChanged = {},
                isExistingAccount = true,
                onConfirm = {},
                onBack = { backCount++ },
            )
        }

        composeRule.onNodeWithText("Confirm").assertIsDisplayed().assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription("New mobile registration QR code")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for approval...").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun focusedRegistrationFieldsRemainVisibleAboveKeyboard() {
        var imeBottom by mutableStateOf(0)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeRule.setContent {
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = bottom }
            var state by remember { mutableStateOf(RegistrationUiState()) }
            RegistrationScreen(
                state = state,
                onEmailChanged = { state = state.copy(email = it) },
                isExistingAccount = false,
                onConfirm = {},
                onBack = {},
            )
        }

        try {
            composeRule.onNodeWithText("Email").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { imeBottom > 0 }
            composeRule.waitForIdle()
            val field = composeRule.onNodeWithText("Email").assertIsFocused().assertIsDisplayed()
            val fieldBounds = field.getUnclippedBoundsInRoot()
            val backBounds =
                composeRule.onNodeWithText("Back").assertIsDisplayed().getUnclippedBoundsInRoot()
            composeRule.onNodeWithText("Confirm").assertIsDisplayed()
            assertTrue("Email must not be above the visible area", fieldBounds.top >= 0.dp)
            assertTrue(
                "Email must remain above the action row",
                fieldBounds.bottom <= backBounds.top,
            )
            field.performImeAction()
        } finally {
            closeSoftKeyboard()
        }
    }

    @Test
    fun registrationActionsStayAtBottomOnNarrowScreen() {
        composeRule.setContent {
            Box(modifier = Modifier.requiredSize(320.dp, 480.dp)) {
                RegistrationScreen(
                    state =
                        RegistrationUiState(
                            isAwaitingMobileRegistration = true,
                            mobileRegistrationQrPayload = "test-mobile-registration",
                        ),
                    onEmailChanged = {},
                    isExistingAccount = true,
                    onConfirm = {},
                    onBack = {},
                )
            }
        }

        val back =
            composeRule.onNodeWithText("Back").assertIsDisplayed().assertHeightIsEqualTo(48.dp)
        val confirm =
            composeRule.onNodeWithText("Confirm").assertIsDisplayed().assertHeightIsEqualTo(48.dp)
        val backBounds = back.getUnclippedBoundsInRoot()
        val confirmBounds = confirm.getUnclippedBoundsInRoot()
        assertEquals(backBounds.top, confirmBounds.top)
        assertEquals(
            (backBounds.right - backBounds.left).value,
            (confirmBounds.right - confirmBounds.left).value,
            1f,
        )
        assertTrue("Back must be left of Confirm", backBounds.right < confirmBounds.left)

        composeRule.onNodeWithText("Waiting for approval...").performScrollTo().assertIsDisplayed()
        assertEquals(backBounds, back.assertIsDisplayed().getUnclippedBoundsInRoot())
        assertEquals(confirmBounds, confirm.assertIsDisplayed().getUnclippedBoundsInRoot())
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
        listOf("New account registration", "Add new mobile to existing account", "Recovery")
            .forEach { label -> composeRule.onNodeWithText(label).assertHeightIsAtLeast(80.dp) }
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
                isExistingAccount = isExistingAccount,
                onConfirm = { confirmedState = state },
                onBack = { backCount++ },
            )
        }

        val title =
            if (isExistingAccount) "Add new mobile to existing account"
            else "New account registration"
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText("Phone").assertDoesNotExist()
        composeRule.onNodeWithText("API URL").assertDoesNotExist()
        composeRule.onNodeWithText("Namespace Name").assertDoesNotExist()
        composeRule.onNodeWithText("Additional API URL (optional)").assertDoesNotExist()
        composeRule.onNodeWithText("Third API URL (optional)").assertDoesNotExist()
        val values = linkedMapOf("Email" to "test@example.com")
        values.forEach { (label, value) ->
            composeRule.onNodeWithText(label).performScrollTo().performTextReplacement(value)
            composeRule.onNodeWithText(label).assertTextContains(value)
        }
        composeRule.onNodeWithText("Confirm").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(RegistrationUiState(email = values.getValue("Email")), confirmedState)
        }
        composeRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }
}
