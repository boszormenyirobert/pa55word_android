package com.zerointrusion.presentation.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zerointrusion.presentation.common.AppBackground
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.BannerTone
import com.zerointrusion.presentation.common.HeroPanel
import com.zerointrusion.presentation.common.PrimaryActionButton
import com.zerointrusion.presentation.common.StatusBanner
import com.zerointrusion.presentation.qrscanner.QrScannerSheet

@Composable
fun RecoveryScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: RecoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var scannerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshOnScreenOpened()
    }
    DisposableEffect(Unit) {
        onDispose(viewModel::onScreenExited)
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroPanel(
                eyebrow = "Account recovery",
                title = "Restore an existing account",
                body = "Use the recovery QR code from your active desktop device to restore access to your existing account. Confirm the recovery server, then match the pairing code on your desktop to approve this mobile device.",
            )
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
            state.errorMessage?.let { message ->
                StatusBanner(
                    title = "Recovery code unavailable",
                    message = message,
                    tone = BannerTone.Error,
                )
            }
            if (state.scannedQr == null) {
                PrimaryActionButton(
                    icon = Icons.Default.QrCodeScanner,
                    text = "Read QR code",
                    onClick = { scannerVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (state.stage == null) {
                PrimaryActionButton(
                    icon = Icons.Default.Check,
                    text = "Confirm",
                    onClick = viewModel::confirmServer,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (state.isCompleted) {
                if (state.errorMessage != null) {
                    AppButton(
                        icon = Icons.Default.Refresh,
                        onClick = viewModel::retryFcm,
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry push registration")
                    }
                }
                PrimaryActionButton(
                    icon = Icons.Default.AccountCircle,
                    text = "Open account",
                    onClick = onCompleted,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AppButton(
                    icon = Icons.Default.QrCodeScanner,
                    onClick = { scannerVisible = true },
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Scan another recovery code")
                }
                PrimaryActionButton(
                    icon = Icons.Default.Refresh,
                    text = "Refresh recovery status",
                    onClick = viewModel::retry,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(icon = Icons.Default.Close, onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel recovery")
                }
            }
            AppButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }

    if (scannerVisible) {
        QrScannerSheet(
            onDismiss = { scannerVisible = false },
            onPayloadScanned = { payload ->
                scannerVisible = false
                viewModel.onQrScanned(payload)
            },
            onScannerError = viewModel::onScannerError,
        )
    }
}