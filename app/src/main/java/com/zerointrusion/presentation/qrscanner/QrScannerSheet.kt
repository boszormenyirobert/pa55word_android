package com.zerointrusion.presentation.qrscanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.zerointrusion.presentation.common.HeroPanel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerSheet(
    onDismiss: () -> Unit,
    onPayloadScanned: (String) -> Unit,
    onScannerError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanHandled = remember { AtomicBoolean(false) }
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
        if (!granted) {
            onScannerError("Camera permission is required to scan the recovery QR code.")
        }
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

    LaunchedEffect(hasCameraPermission, permissionRequested) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroPanel(
                eyebrow = "QR Scanner",
                title = "Read target device QR",
                body = "Point the camera at the desktop QR code. The app will extract deviceId, publicKey, namespaceName and namespaceUrl, then connect the phone and desktop to the target server.",
            )
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    QrCameraPreview(
                        lifecycleOwner = lifecycleOwner,
                        cameraExecutor = cameraExecutor,
                        barcodeScanner = barcodeScanner,
                        scanHandled = scanHandled,
                        onPayloadScanned = onPayloadScanned,
                        onScannerError = onScannerError,
                    )
                }
            } else {
                Text(
                    text = "Camera access is required to scan the QR code.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        permissionRequested = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Allow camera access")
                }
            }
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("Cancel")
            }
        }
    }
}
