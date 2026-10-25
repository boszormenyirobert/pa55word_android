package com.zerointrusion.presentation.qrscanner

import android.annotation.SuppressLint
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun QrCameraPreview(
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    barcodeScanner: BarcodeScanner,
    scanHandled: AtomicBoolean,
    onPayloadScanned: (String) -> Unit,
    onScannerError: (String) -> Unit,
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
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
                onScannerError = onScannerError,
            )
        },
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun PreviewView.bindQrCamera(
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    barcodeScanner: BarcodeScanner,
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
                            Log.d("ZeroIntrusionQr", "QR code scanned successfully. payloadLength=${payload.length}")
                            onPayloadScanned(payload)
                        }
                    }
                    .addOnFailureListener {
                        if (!scanHandled.get()) {
                            Log.e("ZeroIntrusionQr", "QR scanning failed while processing the camera frame.")
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