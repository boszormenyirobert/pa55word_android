package com.zerointrusion.presentation.qrread

import android.annotation.SuppressLint
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val QR_ROUTE_LOG_TAG = "ZeroIntrusionQr"

@Composable
fun QrReadScreen(
    onBack: () -> Unit,
    onPayloadScanned: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanHandled = remember { AtomicBoolean(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            PreviewView(viewContext).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { previewView ->
                            previewView.bindQrCamera(
                                lifecycleOwner = lifecycleOwner,
                                cameraExecutor = cameraExecutor,
                                barcodeScanner = barcodeScanner,
                                scanHandled = scanHandled,
                                onPayloadScanned = onPayloadScanned,
                                onScannerError = { scannerError = it },
                            )
                        },
                    )
                }
                if (!scannerError.isNullOrBlank()) {
                    Text(
                        text = scannerError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
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

@SuppressLint("UnsafeOptInUsageError")
private fun PreviewView.bindQrCamera(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraExecutor: java.util.concurrent.ExecutorService,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    scanHandled: AtomicBoolean,
    onPayloadScanned: (String) -> Unit,
    onScannerError: (String) -> Unit,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            val cameraProvider = runCatching { cameraProviderFuture.get() }
                .getOrElse {
                    onScannerError("Unable to start the camera for QR scanning.")
                    return@addListener
                }

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(1280, 720))
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (scanHandled.get()) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val payload = barcodes.firstNotNullOfOrNull { barcode ->
                            barcode.rawValue?.trim()?.takeIf(String::isNotEmpty)
                        }
                        if (payload != null && scanHandled.compareAndSet(false, true)) {
                            Log.d(QR_ROUTE_LOG_TAG, "QR code scanned successfully. payloadLength=${payload.length}")
                            onPayloadScanned(payload)
                        }
                    }
                    .addOnFailureListener {
                        if (!scanHandled.get()) {
                            Log.e(QR_ROUTE_LOG_TAG, "QR scanning failed while processing the camera frame.")
                            onScannerError("QR scanning failed while processing the camera frame.")
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }

            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure {
                onScannerError("Unable to bind the camera preview for QR scanning.")
            }
        },
        ContextCompat.getMainExecutor(context),
    )
}