package com.zerointrusion.presentation.connecteddevices

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerointrusion.domain.connecteddevices.RemovableAccountDevice
import com.zerointrusion.presentation.app.DeviceRemovalUiState
import com.zerointrusion.ui.theme.ZeroIntrusionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceRemovalScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun cardsShowDatabaseNameWithoutLastUsedAndKeepRemovalTarget() {
        val device =
            RemovableAccountDevice(
                accountId = "account-id",
                namespaceUrl = "https://stored.example.com",
                targetMobileDeviceId = "current-phone",
                deviceName = "Test phone",
                lastTimeUsed = "2026-10-29T10:00:00Z",
            )
        val removals = mutableListOf<RemovableAccountDevice>()
        composeRule.setContent {
            ZeroIntrusionTheme {
                DeviceRemovalScreen(
                    currentMobileDeviceId = "current-phone",
                    state = DeviceRemovalUiState.Success(listOf(device)),
                    onLoad = {},
                    onDelete = { removals.add(it) },
                    onBack = {},
                    databaseNameForDevice = { "Stored database name" },
                )
            }
        }

        composeRule.onNodeWithText("Database: Stored database name").assertIsDisplayed()
        composeRule.onNodeWithText("Server: ${device.namespaceUrl}").assertDoesNotExist()
        composeRule.onNodeWithText("Last used: ${device.lastTimeUsed}").assertDoesNotExist()
        composeRule.onNodeWithText("Mobile device").assertIsDisplayed()
        composeRule.onNodeWithText("Current phone").assertIsDisplayed()
        composeRule.onNodeWithText("Test phone").assertIsDisplayed()
        composeRule.onNodeWithText("ID: current-phone").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove Test phone").performClick()
        composeRule.onNodeWithText("Confirm removal").performClick()
        composeRule.runOnIdle { assertEquals(listOf(device), removals) }
    }

    @Test
    fun serverDropdownAppearsOnlyForMultipleDistinctServersAndKeepsSelectionWorking() {
        val firstServer = "https://first.example.com"
        val secondServer = "https://second.example.com"
        val firstDevice =
            RemovableAccountDevice(
                accountId = "first-account",
                namespaceUrl = firstServer,
                targetDesktopDeviceId = "first-desktop",
                deviceName = "First desktop",
            )
        val sameServerDevice =
            firstDevice.copy(
                namespaceUrl = "$firstServer/",
                targetDesktopDeviceId = "another-desktop",
                deviceName = "Another desktop",
            )
        val secondDevice =
            RemovableAccountDevice(
                accountId = "second-account",
                namespaceUrl = secondServer,
                targetDesktopDeviceId = "second-desktop",
                deviceName = "Second desktop",
            )
        var devices by mutableStateOf(listOf(firstDevice, sameServerDevice))
        composeRule.setContent {
            ZeroIntrusionTheme {
                DeviceRemovalScreen(
                    currentMobileDeviceId = "current-phone",
                    state = DeviceRemovalUiState.Success(devices),
                    onLoad = {},
                    onDelete = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Server").assertDoesNotExist()
        composeRule.onNodeWithText("First desktop").assertIsDisplayed()
        composeRule.onNodeWithText("Another desktop").assertIsDisplayed()

        composeRule.runOnIdle { devices = listOf(firstDevice, secondDevice) }
        composeRule.onNodeWithText("Server").assertIsDisplayed()
        composeRule.onNodeWithText("First desktop").assertIsDisplayed()
        composeRule.onNodeWithText("Second desktop").assertDoesNotExist()
        composeRule.onNodeWithText(firstServer).performClick()
        composeRule.onNodeWithText(secondServer).performClick()
        composeRule.onNodeWithText("Second desktop").assertIsDisplayed()
        composeRule.onNodeWithText("First desktop").assertDoesNotExist()

        composeRule.runOnIdle { devices = listOf(secondDevice) }
        composeRule.onNodeWithText("Server").assertDoesNotExist()
        composeRule.onNodeWithText("Second desktop").assertIsDisplayed()

        composeRule.runOnIdle { devices = emptyList() }
        composeRule.onNodeWithText("Server").assertDoesNotExist()
        composeRule.onNodeWithText("No devices are available to remove.").assertIsDisplayed()
    }
}
