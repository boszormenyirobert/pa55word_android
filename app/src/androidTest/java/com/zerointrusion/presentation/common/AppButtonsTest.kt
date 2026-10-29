package com.zerointrusion.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.recovery.RecoveryQrPayload
import com.zerointrusion.domain.recovery.RecoveryStage
import com.zerointrusion.domain.registration.ServicePlan
import com.zerointrusion.presentation.app.AddDatabaseUiState
import com.zerointrusion.presentation.deviceinformation.DeviceInformationUiModel
import com.zerointrusion.presentation.deviceinformation.DevicesScreen
import com.zerointrusion.presentation.recovery.RecoveryActions
import com.zerointrusion.presentation.recovery.RecoveryScreenContent
import com.zerointrusion.presentation.recovery.RecoveryUiState
import com.zerointrusion.presentation.registration.RegistrationOptionsScreen
import com.zerointrusion.presentation.settings.AccountsScreen
import com.zerointrusion.presentation.settings.AddDatabaseScreen
import com.zerointrusion.presentation.settings.RecoveryConnectionScreen
import com.zerointrusion.presentation.settings.ServicePlanDetailsScreen
import com.zerointrusion.presentation.settings.ServicePlanScreen
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
    fun databaseEmailInputIsRequiredEditableAndDisabledDuringSubmission() {
        var state by mutableStateOf(AddDatabaseUiState(emailError = "Email is required."))
        var submittedEmail: String? = null
        composeRule.setContent {
            ZeroIntrusionTheme {
                AddDatabaseScreen(
                    state = state,
                    onNameChanged = {},
                    onUrlChanged = {},
                    onEmailChanged = { state = state.copy(email = it, emailError = null) },
                    onSubmit = {
                        submittedEmail = state.email
                        state = state.copy(isSubmitting = true)
                    },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Email is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Email").performTextInput("database@example.com")
        composeRule.runOnIdle {
            assertEquals("database@example.com", state.email)
            assertEquals(null, state.emailError)
        }
        composeRule.onNodeWithText("Connect").performClick()
        composeRule.onNodeWithText("Email").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals("database@example.com", submittedEmail) }
    }

    @Test
    fun enlargedHomeLogoFitsBesideActionsAcrossScreenWidths() {
        var headerWidth by mutableStateOf(320.dp)
        val labels = listOf("QR-scan", "Refresh", "Open settings")
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(headerWidth, 120.dp)) {
                    AppScreenHeader(
                        logoWidth = 160.dp,
                        actions = {
                            labels.forEach { label ->
                                AppIconButton(onClick = {}) {
                                    Icon(Icons.Default.Check, contentDescription = label)
                                }
                            }
                        },
                    )
                }
            }
        }
        listOf(320.dp, 280.dp).forEach { width ->
            composeRule.runOnIdle { headerWidth = width }
            val logo =
                composeRule
                    .onNodeWithContentDescription("pa55word logo")
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
            val logoWidth = (logo.right - logo.left).value
            val logoHeight = (logo.bottom - logo.top).value
            assertEquals(if (width == 320.dp) 160f else 120f, logoWidth, 0.5f)
            assertEquals(logoWidth * 0.24f, logoHeight, 0.5f)
            var precedingRight = logo.right
            labels.forEach { label ->
                val bounds =
                    composeRule
                        .onNodeWithContentDescription(label)
                        .assertIsDisplayed()
                        .getUnclippedBoundsInRoot()
                assertTrue("Actions must not overlap", bounds.left >= precedingRight)
                assertTrue("Actions must fit in the header", bounds.right <= logo.left + width)
                precedingRight = bounds.right
            }
        }
    }

    @Test
    fun headerActionsReplaceTitleAndFitBesideLogoOnNarrowScreen() {
        val clicks = mutableListOf<Int>()
        val labels = listOf("QR-scan", "Refresh", "Open settings")
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(280.dp, 120.dp)) {
                    AppScreenHeader(
                        title = "Home",
                        actions = {
                            labels.forEachIndexed { index, label ->
                                AppIconButton(onClick = { clicks.add(index) }) {
                                    Icon(Icons.Default.Check, contentDescription = label)
                                }
                            }
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithText("Home").assertDoesNotExist()
        val logo =
            composeRule
                .onNodeWithContentDescription("pa55word logo")
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
        var precedingRight = logo.right
        labels.forEach { label ->
            val action = composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue("Actions must not overlap", bounds.left >= precedingRight)
            assertTrue("Actions must fit in the header", bounds.right <= 280.dp)
            assertEquals(
                (logo.top + logo.bottom).value / 2f,
                (bounds.top + bounds.bottom).value / 2f,
                0.5f,
            )
            precedingRight = bounds.right
            action.performClick()
        }
        composeRule.runOnIdle { assertEquals(listOf(0, 1, 2), clicks) }
    }

    @Test
    fun screenHeadersKeepLogoBoundsAndTitleSizeAcrossViews() {
        var screen by mutableStateOf(0)
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(320.dp, 640.dp)) {
                    when (screen) {
                        0 ->
                            SettingsScreen(
                                servicePlan = ServicePlan.BASIC,
                                onOpenServicePlan = {},
                                onOpenAccounts = {},
                                onOpenRecoveryConnection = {},
                                onOpenDevices = {},
                                onBack = {},
                            )
                        1 -> ServicePlanScreen(onOpenBasic = {}, onOpenEnterprise = {}, onBack = {})
                        2,
                        3 ->
                            ServicePlanDetailsScreen(
                                plan =
                                    if (screen == 2) ServicePlan.ENTERPRISE else ServicePlan.BASIC,
                                selectedPlan = ServicePlan.BASIC,
                                onSelectPlan = {},
                                onBack = {},
                            )
                        else ->
                            RegistrationOptionsScreen(
                                onNewAccount = {},
                                onAddMobile = {},
                                onRecovery = {},
                            )
                    }
                }
            }
        }
        val expectedLogoBounds =
            composeRule.onNodeWithContentDescription("pa55word logo").getUnclippedBoundsInRoot()
        listOf("Settings", "Service plan", "Service plan", "Service plan", "Registration")
            .forEachIndexed { index, title ->
                composeRule.runOnIdle { screen = index }
                val logo =
                    composeRule.onNodeWithContentDescription("pa55word logo").assertIsDisplayed()
                assertEquals(expectedLogoBounds, logo.getUnclippedBoundsInRoot())
                val titleNode = composeRule.onNodeWithText(title).assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                    it(layouts)
                }
                assertEquals(18.sp, layouts.single().layoutInput.style.fontSize)
                assertTrue(
                    "Logo and title must not overlap",
                    logo.getUnclippedBoundsInRoot().right <
                        titleNode.getUnclippedBoundsInRoot().left,
                )
            }
    }

    @Test
    fun longHeaderWrapsAtLargeFontScaleWithoutMovingOrOverlappingLogo() {
        composeRule.setContent {
            ZeroIntrusionTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    Box(modifier = Modifier.requiredSize(280.dp, 320.dp)) {
                        AppScreenHeader(
                            "Connected device selection",
                            modifier = Modifier.testTag("test-header"),
                        )
                    }
                }
            }
        }
        val header = composeRule.onNodeWithTag("test-header").getUnclippedBoundsInRoot()
        val logo =
            composeRule
                .onNodeWithContentDescription("pa55word logo")
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("Connected device selection").assertIsDisplayed()
        val titleBounds = title.getUnclippedBoundsInRoot()
        assertEquals(120f, (logo.right - logo.left).value, 0.5f)
        assertEquals(28.8f, (logo.bottom - logo.top).value, 0.5f)
        assertEquals(header.left, logo.left)
        assertEquals(header.right, titleBounds.right)
        assertTrue("Title must remain inside the header", titleBounds.bottom <= header.bottom)
        assertTrue("Title must stay right of the logo", titleBounds.left > logo.right)
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Long titles must wrap instead of shrinking", layouts.single().lineCount > 1)
        assertEquals(18.sp, layouts.single().layoutInput.style.fontSize)
    }

    @Test
    fun settingsHeaderStaysFixedWhileContentScrolls() {
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(320.dp, 480.dp)) {
                    AppSettingsPage(title = "Settings", onBack = {}) {
                        repeat(40) { index ->
                            Text("Entry $index", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        val before =
            composeRule.onNodeWithContentDescription("pa55word logo").getUnclippedBoundsInRoot()
        composeRule.onNodeWithText("Entry 39").performScrollTo().assertIsDisplayed()
        assertEquals(
            before,
            composeRule
                .onNodeWithContentDescription("pa55word logo")
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot(),
        )
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsDisplayed()
    }

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
                    servicePlan = ServicePlan.ENTERPRISE,
                    onOpenServicePlan = { actions.add("Service plan") },
                    onOpenAccounts = { actions.add("Database connections") },
                    onOpenRecoveryConnection = { actions.add("Enterprise recovery") },
                    onOpenDevices = { actions.add("Devices") },
                    onBack = { actions.add("Back") },
                )
            }
        }

        val labels =
            listOf("Service plan", "Database connections", "Devices", "Enterprise recovery", "Back")
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
                    servicePlan = ServicePlan.ENTERPRISE,
                    onOpenAccounts = {},
                    onOpenRecoveryConnection = {},
                    onOpenDevices = {},
                    onBack = {},
                )
            }
        }

        listOf("Service plan", "Database connections", "Devices", "Enterprise recovery").forEach {
            label ->
            val button =
                composeRule.onNodeWithText(label).performScrollTo().getUnclippedBoundsInRoot()
            val text =
                composeRule.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(60f, (text.left - button.left).value, 1f)
        }
    }

    @Test
    fun settingsMenuLabelsHaveReadableForegroundInBothThemes() {
        var darkTheme by mutableStateOf(false)
        var foreground = Color.Unspecified
        composeRule.setContent {
            ZeroIntrusionTheme(darkTheme = darkTheme) {
                foreground = MaterialTheme.colorScheme.primary
                SettingsScreen(
                    onOpenAccounts = {},
                    onOpenRecoveryConnection = {},
                    onOpenDevices = {},
                    onBack = {},
                )
            }
        }

        listOf(false, true).forEach { theme ->
            composeRule.runOnIdle { darkTheme = theme }
            listOf("Service plan", "Devices").forEach { label ->
                val pixels =
                    composeRule
                        .onNodeWithText(label, useUnmergedTree = true)
                        .captureToImage()
                        .toPixelMap()
                var readablePixels = 0
                for (row in 0 until pixels.height) {
                    for (column in 0 until pixels.width) {
                        val color = pixels[column, row]
                        if (
                            kotlin.math.abs(color.red - foreground.red) < 0.05f &&
                                kotlin.math.abs(color.green - foreground.green) < 0.05f &&
                                kotlin.math.abs(color.blue - foreground.blue) < 0.05f
                        ) {
                            readablePixels++
                        }
                    }
                }
                assertTrue(
                    "$label must use the theme primary foreground in darkTheme=$theme",
                    readablePixels > 10,
                )
            }
        }
    }

    @Test
    fun enterpriseRecoveryCanRescanPendingRecoveryAndRequiresConfirmation() {
        var state by mutableStateOf(RecoveryUiState(stage = RecoveryStage.WAITING_FOR_SELECTION))
        val scanned = mutableListOf<String>()
        var confirmations = 0
        composeRule.setContent {
            ZeroIntrusionTheme {
                RecoveryScreenContent(
                    state = state,
                    enterpriseRecovery = true,
                    actions =
                        recoveryActions(
                            onQrScanned = { payload ->
                                scanned.add(payload)
                                state =
                                    RecoveryUiState(
                                        scannedQr =
                                            RecoveryQrPayload("recovery-id", "https://example.test")
                                    )
                            },
                            onConfirm = { confirmations++ },
                        ),
                    scanner = { onScanned, _ ->
                        Text("Test scanner")
                        AppButton(icon = Icons.Default.Check, onClick = { onScanned("test-qr") }) {
                            Text("Scan test QR")
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("Test scanner").assertDoesNotExist()
        composeRule.onNodeWithText("Scan recovery code").performScrollTo().performClick()
        composeRule.onNodeWithText("Test scanner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Scan test QR").performScrollTo().performClick()
        composeRule.onNodeWithText("Test scanner").assertDoesNotExist()
        composeRule.onNodeWithText("https://example.test").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf("test-qr"), scanned)
            assertEquals(0, confirmations)
        }
        composeRule.onNodeWithText("Confirm").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test
    fun enterpriseRecoveryDisablesActionsDuringRequests() {
        composeRule.setContent {
            ZeroIntrusionTheme {
                RecoveryScreenContent(
                    state =
                        RecoveryUiState(
                            stage = RecoveryStage.WAITING_FOR_SELECTION,
                            isWorking = true,
                        ),
                    enterpriseRecovery = true,
                    actions = recoveryActions(),
                    scanner = { _, _ -> Text("Test scanner") },
                )
            }
        }

        composeRule.onNodeWithText("Test scanner").assertDoesNotExist()
        listOf("Scan recovery code", "Refresh recovery status", "Cancel recovery").forEach { label
            ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test
    fun enterpriseRecoveryCanCancelInlineScannerWithoutStartingRecovery() {
        composeRule.setContent {
            ZeroIntrusionTheme {
                RecoveryScreenContent(
                    state = RecoveryUiState(),
                    enterpriseRecovery = true,
                    actions = recoveryActions(),
                    scanner = { _, _ ->
                        Column {
                            Text("Test scanner")
                            Spacer(Modifier.requiredSize(1.dp, 800.dp))
                            Text("Scanner end")
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("Test scanner").assertIsDisplayed()
        val back = composeRule.onNodeWithText("Back").assertIsDisplayed().getUnclippedBoundsInRoot()
        val cancel =
            composeRule.onNodeWithText("Cancel scan").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(back.top.value, cancel.top.value, 1f)
        assertEquals((back.right - back.left).value, (cancel.right - cancel.left).value, 1f)
        assertEquals(12f, (cancel.left - back.right).value, 1f)
        composeRule.onNodeWithText("Scanner end").performScrollTo().assertIsDisplayed()
        assertEquals(
            cancel,
            composeRule.onNodeWithText("Cancel scan").assertIsDisplayed().getUnclippedBoundsInRoot(),
        )
        composeRule.onNodeWithText("Cancel scan").performClick()
        composeRule.onNodeWithText("Test scanner").assertDoesNotExist()
        composeRule.onNodeWithText("Read QR code").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertDoesNotExist()
    }

    @Test
    fun enterpriseRecoveryReplacesContactDetailsWithGuidanceAndInlineScanner() {
        var backCount = 0
        composeRule.setContent {
            ZeroIntrusionTheme {
                RecoveryConnectionScreen(onBack = { backCount++ }) {
                    Text("QR Read")
                    Text("Scanner preview")
                }
            }
        }

        composeRule.onNodeWithText("Enterprise recovery").assertIsDisplayed()
        composeRule.onNodeWithText("User email").assertDoesNotExist()
        composeRule.onNodeWithText("Recovery").assertDoesNotExist()
        composeRule
            .onNodeWithText(
                "Start the recovery process with an email confirmation, as described in the desktop application.",
                substring = true,
            )
            .assertIsDisplayed()
        val description =
            composeRule
                .onNodeWithText("Use this feature if you have lost the phone", substring = true)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
        val scanner =
            composeRule.onNodeWithText("QR Read").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(
            "Scanner must follow the recovery description",
            scanner.top >= description.bottom,
        )
        composeRule.onNodeWithText("Scanner preview").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun servicePlanNavigationSelectsOnePlanAndControlsSettingsVisibility() {
        var servicePlan by mutableStateOf(ServicePlan.BASIC)
        var route by mutableStateOf("settings")
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(320.dp, 480.dp)) {
                    when (route) {
                        "settings" ->
                            SettingsScreen(
                                servicePlan = servicePlan,
                                onOpenServicePlan = { route = "plans" },
                                onOpenAccounts = {},
                                onOpenRecoveryConnection = {},
                                onOpenDevices = {},
                                onBack = {},
                            )
                        "plans" ->
                            ServicePlanScreen(
                                onOpenBasic = { route = "basic" },
                                onOpenEnterprise = { route = "enterprise" },
                                onBack = { route = "settings" },
                            )
                        else ->
                            ServicePlanDetailsScreen(
                                plan =
                                    if (route == "basic") ServicePlan.BASIC
                                    else ServicePlan.ENTERPRISE,
                                selectedPlan = servicePlan,
                                onSelectPlan = { servicePlan = it },
                                onBack = { route = "plans" },
                            )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Database connections").assertDoesNotExist()
        composeRule.onNodeWithText("Enterprise recovery").assertDoesNotExist()
        composeRule.onNodeWithText("Service plan").performScrollTo().performClick()
        composeRule.onNodeWithText("Basic").performScrollTo().performClick()
        composeRule.onNodeWithText("Dummy text.").assertIsDisplayed()
        composeRule.onNodeWithText("Current plan").assertIsOn().assertIsNotEnabled()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Enterprise").performScrollTo().performClick()
        composeRule.onNodeWithText("Dummy text.").assertIsDisplayed()
        composeRule.onNodeWithText("Select Enterprise").assertIsOff().performClick()
        composeRule.onNodeWithText("Current plan").assertIsOn().assertIsNotEnabled()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Database connections").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enterprise recovery").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Service plan").performScrollTo().performClick()
        composeRule.onNodeWithText("Basic").performScrollTo().performClick()
        composeRule.onNodeWithText("Select Basic").assertIsOff().performClick()
        composeRule.onNodeWithText("Current plan").assertIsOn()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Enterprise").performScrollTo().performClick()
        composeRule.onNodeWithText("Select Enterprise").assertIsOff()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Database connections").assertDoesNotExist()
        composeRule.onNodeWithText("Devices").assertExists()
        composeRule.onNodeWithText("Enterprise recovery").assertDoesNotExist()
    }

    @Test
    fun failedServicePlanSaveKeepsCheckboxUnselectedAndDisplaysError() {
        var errorCode by mutableStateOf<String?>(null)
        composeRule.setContent {
            ZeroIntrusionTheme {
                ServicePlanDetailsScreen(
                    plan = ServicePlan.ENTERPRISE,
                    selectedPlan = ServicePlan.BASIC,
                    onSelectPlan = { errorCode = "SETTINGS_SAVE_FAILED" },
                    errorCode = errorCode,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Select Enterprise").assertIsOff().performClick()
        composeRule.onNodeWithText("Select Enterprise").assertIsOff()
        composeRule.onNodeWithText("SETTINGS_SAVE_FAILED").assertIsDisplayed()
    }

    @Test
    fun settingsMenuUsesLargeButtonsAndKeepsTheirCallbacks() {
        val actions = mutableListOf<String>()
        val labels =
            listOf("Service plan", "Database connections", "Devices", "Enterprise recovery")
        composeRule.setContent {
            ZeroIntrusionTheme {
                Box(modifier = Modifier.requiredSize(320.dp, 480.dp)) {
                    SettingsScreen(
                        servicePlan = ServicePlan.ENTERPRISE,
                        onOpenServicePlan = { actions.add("Service plan") },
                        onOpenAccounts = { actions.add("Database connections") },
                        onOpenDevices = { actions.add("Devices") },
                        onOpenRecoveryConnection = { actions.add("Enterprise recovery") },
                        onBack = {},
                    )
                }
            }
        }
        labels.forEach { label ->
            composeRule
                .onNodeWithText(label)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHeightIsAtLeast(80.dp)
                .performClick()
        }
        composeRule.runOnIdle { assertEquals(labels, actions) }
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
            assertEquals(62f, (textBounds.left - buttonBounds.left).value, 1f)
            button.assertHeightIsEqualTo(80.dp).performClick()
        }
        composeRule.onNodeWithText("Back").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("Device information", "Remove devices", "Back"), actions)
        }
    }

    @Test
    fun databaseCardsShowStoredNamesAndKeepAccountDeletionIdentity() {
        val namespace =
            NamespaceEntry(
                namespaceName = "Stored database name",
                namespaceId = "namespace-id",
                namespaceUrl = " https://stored.example.com/ ",
                accountId = " account-id ",
                desktopDeviceId = "desktop-id",
            )
        val deletions = mutableListOf<Pair<String, String>>()
        composeRule.setContent {
            ZeroIntrusionTheme {
                AccountsScreen(
                    namespaces =
                        listOf(
                            namespace,
                            namespace.copy(namespaceName = "Duplicate name"),
                            namespace.copy(namespaceName = "Invalid entry", accountId = ""),
                        ),
                    deleting = false,
                    errorMessage = null,
                    onDeleteAccount = { url, accountId -> deletions.add(url to accountId) },
                    onAddDatabase = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Stored database name").assertIsDisplayed()
        composeRule.onNodeWithText("https://stored.example.com").assertDoesNotExist()
        composeRule.onNodeWithText("account-id").assertDoesNotExist()
        composeRule.onNodeWithText("Duplicate name").assertDoesNotExist()
        composeRule.onNodeWithText("Invalid entry").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Delete account").performClick()
        composeRule.onNodeWithText("Delete account?").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(deletions.isEmpty()) }
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("https://stored.example.com" to "account-id"), deletions)
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
                                onEmailChanged = {},
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
                            RecoveryConnectionScreen(onBack = { actions.add("Recovery back") }) {
                                Text("QR Read")
                            }
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
            if (destination == 2 || destination == 3) {
                val label =
                    when (destination) {
                        2 -> "Add"
                        else -> "Connect"
                    }
                assertEquals(
                    declineBounds,
                    composeRule.onNodeWithText(label).assertIsDisplayed().getUnclippedBoundsInRoot(),
                )
            }
            if (destination >= 4) composeRule.onNodeWithText("Back").performClick()
        }
        composeRule.runOnIdle {
            assertEquals(listOf("Devices back", "Recovery back", "Devices menu back"), actions)
        }
    }

    private fun recoveryActions(onQrScanned: (String) -> Unit = {}, onConfirm: () -> Unit = {}) =
        RecoveryActions(
            onBack = {},
            onCompleted = {},
            onQrScanned = onQrScanned,
            onScannerError = {},
            onConfirmServer = onConfirm,
            onRetry = {},
            onRetryFcm = {},
            onCancel = {},
        )
}
