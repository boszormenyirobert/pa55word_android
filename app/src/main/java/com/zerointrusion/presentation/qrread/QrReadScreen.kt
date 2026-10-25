package com.zerointrusion.presentation.qrread

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.zerointrusion.presentation.app.QrNamespaceRegistrationUiState
import com.zerointrusion.presentation.common.QrCodeImage
import com.zerointrusion.presentation.qrscanner.QrCameraPreview
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.Green
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrReadScreen(
    qrNamespaceRegistrationState: QrNamespaceRegistrationUiState,
    onClearQrNamespaceRegistrationState: () -> Unit,
    onBack: () -> Unit,
    onPayloadScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanHandled = remember { AtomicBoolean(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    var cameraPermissionGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
        cameraPermissionRequested = true
        scannerError = if (granted) null else "Camera permission is required to scan the QR code."
    }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }

    DisposableEffect(barcodeScanner, cameraExecutor) {
        onDispose {
            barcodeScanner.close()
            cameraExecutor.shutdown()
        }
    }

    val followUpQrPayload = (qrNamespaceRegistrationState as? QrNamespaceRegistrationUiState.Success)
        ?.followUpQrPayload
    LaunchedEffect(followUpQrPayload, cameraPermissionGranted) {
        if (followUpQrPayload == null && !cameraPermissionGranted && !cameraPermissionRequested) {
            cameraPermissionRequested = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Dark, LightDark, Dark),
                ),
            )
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "QR Read",
            style = MaterialTheme.typography.headlineMedium,
            color = White,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightDark),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Point the camera at the desktop QR code to connect its namespace and register both devices on the target server.",
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (followUpQrPayload != null) {
                    Text(
                        text = "Scan this code with the new mobile to connect its additional namespace servers.",
                        color = White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    QrCodeImage(
                        payload = followUpQrPayload,
                        contentDescription = "Additional mobile namespaces QR code",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                } else if (cameraPermissionGranted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                    ) {
                        QrCameraPreview(
                            lifecycleOwner = lifecycleOwner,
                            cameraExecutor = cameraExecutor,
                            barcodeScanner = barcodeScanner,
                            scanHandled = scanHandled,
                            onPayloadScanned = onPayloadScanned,
                            onScannerError = { scannerError = it },
                        )
                    }
                } else {
                    Text(
                        text = "Camera access is required to scan the namespace QR code.",
                        color = White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            cameraPermissionRequested = true
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Allow camera access")
                    }
                }
                if (!scannerError.isNullOrBlank()) {
                    Text(
                        text = scannerError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                when (qrNamespaceRegistrationState) {
                    QrNamespaceRegistrationUiState.Idle -> Unit
                    is QrNamespaceRegistrationUiState.AwaitingDeviceSelection -> Unit
                    QrNamespaceRegistrationUiState.InProgress -> Text(
                        text = "Connecting scanned namespace...",
                        color = White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is QrNamespaceRegistrationUiState.Success -> Text(
                        text = qrNamespaceRegistrationState.message,
                        color = Green,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is QrNamespaceRegistrationUiState.Error -> Text(
                        text = qrNamespaceRegistrationState.message,
                        color = ErrorTextLight,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Button(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("Back")
        }
    }
}
