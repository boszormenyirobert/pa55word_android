package com.zerointrusion.presentation.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerointrusion.presentation.common.AppBackground
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.BannerTone
import com.zerointrusion.presentation.common.HeroPanel
import com.zerointrusion.presentation.common.PrimaryActionButton
import com.zerointrusion.presentation.common.StatusBanner
import com.zerointrusion.presentation.qrscanner.QrScannerContent
import com.zerointrusion.presentation.qrscanner.QrScannerSheet
import com.zerointrusion.presentation.settings.RecoveryConnectionScreen

@Composable
fun RecoveryScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    corporateRecovery: Boolean = false,
    viewModel: RecoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.refreshOnScreenOpened()
    }
    DisposableEffect(viewModel) {
        onDispose(viewModel::onScreenExited)
    }

    RecoveryScreenContent(
        state = state,
        corporateRecovery = corporateRecovery,
        actions = RecoveryActions(
            onBack = onBack,
            onCompleted = onCompleted,
            onQrScanned = viewModel::onQrScanned,
            onScannerError = viewModel::onScannerError,
            onConfirmServer = viewModel::confirmServer,
            onRetry = viewModel::retry,
            onRetryFcm = viewModel::retryFcm,
            onCancel = viewModel::cancel,
        ),
    )
}

internal data class RecoveryActions(
    val onBack: () -> Unit,
    val onCompleted: () -> Unit,
    val onQrScanned: (String) -> Unit,
    val onScannerError: (String) -> Unit,
    val onConfirmServer: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryFcm: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
internal fun RecoveryScreenContent(
    state: RecoveryUiState,
    corporateRecovery: Boolean,
    actions: RecoveryActions,
    scanner: @Composable ((String) -> Unit, (String) -> Unit) -> Unit = { onScanned, onError ->
        QrScannerContent(onPayloadScanned = onScanned, onScannerError = onError)
    },
) {
    var scannerVisible by rememberSaveable { mutableStateOf(corporateRecovery && state.step == RecoveryStep.SCAN) }
    val onPayloadScanned: (String) -> Unit = { payload ->
        scannerVisible = false
        actions.onQrScanned(payload)
    }

    LaunchedEffect(state.step) {
        if (state.step != RecoveryStep.SCAN) scannerVisible = false
    }

    val showInlineScanner = corporateRecovery && scannerVisible && !state.isWorking && !state.isCompleted
    val recoveryContent: @Composable ColumnScope.() -> Unit = {
        if (!corporateRecovery) {
            HeroPanel(
                eyebrow = "Account recovery",
                title = "Restore an existing account",
                body = "Use the recovery QR code from your active desktop device to restore access to your existing account. Confirm the recovery server, then match the pairing code on your desktop to approve this mobile device.",
            )
        }
        state.scannedQr?.let { qr ->
            StatusBanner(
                title = "Recovery server",
                message = qr.apiBaseUrl,
                tone = BannerTone.Success,
            )
        }
        state.pairingCode?.let { code ->
            StatusBanner(
                title = "Pairing code",
                message = code,
                tone = BannerTone.Success,
            )
        }
        state.errorCode?.let { errorCode ->
            StatusBanner(
                title = "Recovery code unavailable",
                message = errorCode.name,
                tone = BannerTone.Error,
            )
        }
        if (showInlineScanner) {
            Text("QR Read", color = MaterialTheme.colorScheme.onBackground)
            scanner(onPayloadScanned, actions.onScannerError)
        } else {
            RecoveryActionButtons(state, actions, onScanRequested = { scannerVisible = true })
        }
    }

    if (corporateRecovery) {
        RecoveryConnectionScreen(
            onBack = actions.onBack,
            onCancelScan = if (showInlineScanner) ({ scannerVisible = false }) else null,
            content = recoveryContent,
        )
    } else {
        AppBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                recoveryContent()
                AppButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = actions.onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back")
                }
            }
        }
    }

    if (scannerVisible && !corporateRecovery && !state.isWorking && !state.isCompleted) {
        QrScannerSheet(
            onDismiss = { scannerVisible = false },
            onPayloadScanned = onPayloadScanned,
            onScannerError = actions.onScannerError,
        )
    }
}

@Composable
private fun RecoveryActionButtons(
    state: RecoveryUiState,
    actions: RecoveryActions,
    onScanRequested: () -> Unit,
) {
    when (state.step) {
        RecoveryStep.SCAN -> {
            PrimaryActionButton(
                icon = Icons.Default.QrCodeScanner,
                text = "Read QR code",
                onClick = onScanRequested,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RecoveryStep.CONFIRM_SERVER -> {
            PrimaryActionButton(
                icon = Icons.Default.Check,
                text = "Confirm",
                onClick = actions.onConfirmServer,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RecoveryStep.COMPLETED -> {
            if (state.errorCode != null) {
                AppButton(
                    icon = Icons.Default.Refresh,
                    onClick = actions.onRetryFcm,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retry push registration")
                }
            }
            PrimaryActionButton(
                icon = Icons.Default.AccountCircle,
                text = "Open account",
                onClick = actions.onCompleted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RecoveryStep.WAITING -> {
            AppButton(
                icon = Icons.Default.QrCodeScanner,
                onClick = onScanRequested,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scan recovery code")
            }
            PrimaryActionButton(
                icon = Icons.Default.Refresh,
                text = "Refresh recovery status",
                onClick = actions.onRetry,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(
                icon = Icons.Default.Close,
                onClick = actions.onCancel,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel recovery")
            }
        }
    }

}