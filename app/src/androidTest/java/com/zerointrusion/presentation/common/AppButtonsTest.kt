package com.zerointrusion.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerointrusion.presentation.app.AddDatabaseUiState
import com.zerointrusion.presentation.deviceinformation.DeviceInformationUiModel
import com.zerointrusion.presentation.deviceinformation.DevicesScreen
import com.zerointrusion.presentation.settings.AccountsScreen
import com.zerointrusion.presentation.settings.AddDatabaseScreen
import com.zerointrusion.presentation.settings.RecoveryConnectionScreen
import com.zerointrusion.presentation.settings.SettingsConnectedDevicesScreen
import com.zerointrusion.presentation.settings.SettingsScreen
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.ZeroIntrusionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppButtonsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun actionButtonHasCompactHeightAndInvokesItsAction() {
        var clicks = 0
        composeRule.setContent {
            ZeroIntrusionTheme {
                AppButton(onClick = { clicks++ }, icon = Icons.Default.Check) { Text("Confirm") }
            }
        }

        val buttonBounds = composeRule.onNodeWithText("Confirm").getUnclippedBoundsInRoot()
        val textBounds =
            composeRule.onNodeWithText("Confirm", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(
            "The icon must have space before the label",
            textBounds.left - buttonBounds.left >= 46.dp,
        )
        composeRule.onNodeWithText("Confirm").assertHeightIsEqualTo(48.dp).performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun compactActionIconsFitSideBySide() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            ZeroIntrusionTheme {
                Row(
                    modifier = Modifier.width(268.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppButton(
                        onClick = { actions.add("Allow") },
                        icon = Icons.Default.Check,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Allow")
                    }
                    AppButton(
                        onClick = { actions.add("Decline") },
                        icon = Icons.Default.Close,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Decline")
                    }
                }
            }
        }

        listOf("Allow", "Decline").forEach { label ->
            composeRule.onNodeWithText(label).assertHeightIsEqualTo(48.dp).performClick()
        }
        composeRule.runOnIdle { assertEquals(listOf("Allow", "Decline"), actions) }
    }

    @Test
    fun disabledActionCannotBeInvoked() {
        var clicks = 0
        composeRule.setContent {
            AppButton(icon = Icons.Default.Check, onClick = { clicks++ }, enabled = false) {
                Text("Confirm")
            }
        }

        composeRule.onNodeWithText("Confirm").assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun iconButtonHasAccessibleTouchTargetAndInvokesItsAction() {
        var clicks = 0
        composeRule.setContent {
            AppIconButton(onClick = { clicks++ }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }

        composeRule
            .onNodeWithContentDescription("Delete")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun disabledIconActionCannotBeInvoked() {
        var clicks = 0
        composeRule.setContent {
            AppIconButton(onClick = { clicks++ }, enabled = false) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }

        composeRule.onNodeWithContentDescription("Delete").assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun destructiveActionKeepsRedOutlineAndTransparentInterior() {
        composeRule.setContent {
            Surface(color = Color.Black) {
                AppButton(icon = Icons.Default.Delete, onClick = {}, color = ActionDeclineRed) {
                    Text("Delete")
                }
            }
        }

        val pixels = composeRule.onNodeWithText("Delete").captureToImage().toPixelMap()
        val interior = pixels[pixels.width / 2, pixels.height / 4]
        assertEquals(0f, interior.red, 0.01f)
        assertEquals(0f, interior.green, 0.01f)
        assertEquals(0f, interior.blue, 0.01f)
        var hasRedOutline = false
        for (row in 0 until pixels.height) {
            for (column in 0 until minOf(6, pixels.width)) {
                val color = pixels[column, row]
                hasRedOutline =
                    hasRedOutline || (color.red > color.green * 1.5f && color.red > 0.2f)
            }
        }
        assertTrue("Destructive action should retain its red border", hasRedOutline)
    }

    @Test
    fun settingsActionsKeepTheirCallbacksAndCompactHeight() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            ZeroIntrusionTheme {
                SettingsScreen(
                    onOpenAccounts = { actions.add("Accounts - Servers") },
                    onOpenRecoveryConnection = { actions.add("Recovery connection") },
                    onOpenDevices = { actions.add("Devices") },
                    onBack = { actions.add("Back") },
                )
            }
        }

        val labels = listOf("Accounts - Servers", "Devices", "Recovery connection", "Back")
        labels.forEach { label ->
            val button = composeRule.onNodeWithText(label)
            if (label != "Back") button.performScrollTo()
            button.assertIsDisplayed().assertHeightIsEqualTo(48.dp).performClick()
        }
        composeRule.runOnIdle { assertEquals(labels, actions) }
    }

    @Test
    fun iconButtonHasNoVisibleBorder() {
        composeRule.setContent {
            Surface(color = Color.Black) {
                AppIconButton(onClick = {}, color = ActionDeclineRed) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }

        val pixels =
            composeRule.onNodeWithContentDescription("Delete").captureToImage().toPixelMap()
        for (row in 0 until pixels.height) {
            for (column in 0 until 3) {
                assertEquals(0f, pixels[column, row].red, 0.01f)
            }
        }
    }

    @Test
    fun settingsButtonLabelsAreLeftAlignedWithThirtyDpPadding() {
        composeRule.setContent {
            ZeroIntrusionTheme {
                SettingsScreen(
                    onOpenAccounts = {},
                    onOpenRecoveryConnection = {},
                    onOpenDevices = {},
                    onBack = {},
                )
            }
        }

        listOf("Accounts - Servers", "Devices", "Recovery connection").forEach { label ->
            val button =
                composeRule.onNodeWithText(label).performScrollTo().getUnclippedBoundsInRoot()
            val text =
                composeRule.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(60f, (text.left - button.left).value, 1f)
        }
    }

    @Test
    fun devicesMenuActionsAreLeftAlignedAndKeepTheirCallbacks() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(320.dp, 480.dp)) {
                    DevicesScreen(
                        onOpenDeviceInformation = { actions.add("Device information") },
                        onOpenRemoveDevices = { actions.add("Remove devices") },
                        onBack = { actions.add("Back") },
                    )
                }
            }
        }

        listOf("Device information", "Remove devices").forEach { label ->
            val button = composeRule.onNodeWithText(label).performScrollTo()
            val buttonBounds = button.getUnclippedBoundsInRoot()
            val textBounds =
                composeRule.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(60f, (textBounds.left - buttonBounds.left).value, 1f)
            button.assertHeightIsEqualTo(48.dp).performClick()
        }
        composeRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("Device information", "Remove devices", "Back"), actions)
        }
    }

    @Test
    fun settingsAndDatabaseActionsMatchHomeActionPositionsOnNarrowScreen() {
        var screen by mutableStateOf(0)
        val actions = mutableListOf<String>()
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(320.dp, 480.dp)) {
                    when (screen) {
                        0 ->
                            Column(
                                modifier =
                                    Modifier.fillMaxSize()
                                        .padding(WindowInsets.safeDrawing.asPaddingValues())
                                        .padding(horizontal = 20.dp, vertical = 20.dp)
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                AppBottomActions {
                                    AppButton(
                                        icon = Icons.Default.Check,
                                        onClick = {},
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Allow")
                                    }
                                    AppButton(
                                        icon = Icons.Default.Close,
                                        onClick = {},
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Decline")
                                    }
                                }
                            }
                        1 ->
                            SettingsScreen(
                                onOpenAccounts = {},
                                onOpenRecoveryConnection = {},
                                onOpenDevices = {},
                                onBack = {},
                            )
                        2 ->
                            AccountsScreen(
                                namespaces = emptyList(),
                                deleting = false,
                                errorMessage = "Long account error\n".repeat(30),
                                onDeleteAccount = { _, _ -> },
                                onAddDatabase = {},
                                onBack = {},
                            )
                        3 ->
                            AddDatabaseScreen(
                                state = AddDatabaseUiState(),
                                onNameChanged = {},
                                onUrlChanged = {},
                                onSubmit = {},
                                onBack = {},
                            )
                        4 ->
                            SettingsConnectedDevicesScreen(
                                deviceInformation =
                                    DeviceInformationUiModel(
                                        accountId = "account",
                                        deviceId = "device",
                                        deviceName = "Phone",
                                        namespaceName = "Server",
                                        namespaceId = "namespace",
                                        namespaceUrl = "https://example.com",
                                        email = "user@example.com",
                                    ),
                                namespaces = emptyList(),
                                connectedDeviceGroups = emptyList(),
                                namespaceManagementErrorMessage = "Long device error\n".repeat(30),
                                onDeleteNamespace = {},
                                onBack = { actions.add("Devices back") },
                            )
                        5 ->
                            RecoveryConnectionScreen(
                                email = "user@example.com\n".repeat(30),
                                phone = "+1234567890",
                                onStartRecovery = { actions.add("Recover") },
                                onBack = { actions.add("Recovery back") },
                            )
                        6 ->
                            DevicesScreen(
                                onOpenDeviceInformation = {},
                                onOpenRemoveDevices = {},
                                onBack = { actions.add("Devices menu back") },
                            )
                    }
                }
            }
        }

        val allowBounds = composeRule.onNodeWithText("Allow").getUnclippedBoundsInRoot()
        val declineBounds = composeRule.onNodeWithText("Decline").getUnclippedBoundsInRoot()
        for (destination in 1..6) {
            composeRule.runOnIdle { screen = destination }
            assertEquals(
                allowBounds,
                composeRule.onNodeWithText("Back").assertIsDisplayed().getUnclippedBoundsInRoot(),
            )
            if (destination == 2 || destination == 3 || destination == 5) {
                val label =
                    when (destination) {
                        2 -> "Add"
                        3 -> "Connect"
                        else -> "Recovery"
                    }
                assertEquals(
                    declineBounds,
                    composeRule.onNodeWithText(label).assertIsDisplayed().getUnclippedBoundsInRoot(),
                )
                if (destination == 5) composeRule.onNodeWithText(label).performClick()
            }
            if (destination >= 4) composeRule.onNodeWithText("Back").performClick()
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf("Devices back", "Recover", "Recovery back", "Devices menu back"),
                actions,
            )
        }
    }
}
