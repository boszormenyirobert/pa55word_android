package com.zerointrusion.presentation.auth

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.BannerTone
import com.zerointrusion.presentation.common.Pa55wordLogo
import com.zerointrusion.presentation.common.StatusBanner

@Composable
fun SecurityScreen(
    errorMessage: String?,
    onAuthenticationSucceeded: () -> Unit,
    onAuthenticationFailed: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var promptNonce by rememberSaveable { mutableIntStateOf(0) }
    var awaitingConfirmation by remember { mutableStateOf(false) }

    val biometricPrompt: BiometricPrompt? =
        remember(activity) {
            activity?.let {
                BiometricPrompt(
                    it,
                    ContextCompat.getMainExecutor(it),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            awaitingConfirmation = false
                            if (
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                    errorCode != BiometricPrompt.ERROR_CANCELED
                            ) {
                                onAuthenticationFailed(errString.toString())
                            }
                        }

                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            awaitingConfirmation = false
                            onAuthenticationSucceeded()
                        }

                        override fun onAuthenticationFailed() {
                            onAuthenticationFailed("Authentication failed. Please retry.")
                        }
                    },
                )
            }
        }

    LaunchedEffect(activity, promptNonce) {
        val prompt = biometricPrompt
        if (activity == null || prompt == null) {
            onAuthenticationFailed("Authentication is unavailable on this device.")
            return@LaunchedEffect
        }

        when (val status = canAuthenticate(context)) {
            AuthAvailability.Available -> {
                awaitingConfirmation = true
                prompt.authenticate(buildPromptInfo())
            }
            is AuthAvailability.Unavailable -> onAuthenticationFailed(status.message)
        }
    }

    SecurityScreenContent(
        errorMessage = errorMessage,
        onRetry = { promptNonce++ },
        awaitingConfirmation = awaitingConfirmation,
    )
}

@Composable
internal fun SecurityScreenContent(
    errorMessage: String?,
    onRetry: () -> Unit,
    awaitingConfirmation: Boolean = false,
) {
    val ringRotation =
        if (awaitingConfirmation) {
            rememberInfiniteTransition(label = "authentication-logo")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
                    label = "logo-ring-rotation",
                )
        } else null
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black)
                .testTag("security-background")
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Pa55wordLogo(
                modifier =
                    Modifier.widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .aspectRatio(1000f / 240f)
                        .drawWithContent {
                            drawContent()
                            ringRotation?.let { rotation ->
                                val scale = size.width / 1000f
                                val center = Offset(405f * scale, 120f * scale)
                                rotate(rotation.value, center) {
                                    drawCircle(
                                        brush =
                                            Brush.sweepGradient(
                                                colors =
                                                    listOf(
                                                        Color(0xFF78CD00),
                                                        Color(0xFFFFD600),
                                                        Color(0xFFFF4D5A),
                                                        Color(0xFF78CD00),
                                                    ),
                                                center = center,
                                            ),
                                        center = center,
                                        radius = 110f * scale,
                                        style = Stroke(width = 8f * scale),
                                    )
                                }
                            }
                        }
            )
        }
        if (!errorMessage.isNullOrBlank()) {
            StatusBanner(
                title = "Authentication blocked",
                message = errorMessage,
                tone = BannerTone.Error,
            )
        }
        AppButton(icon = Icons.Default.Fingerprint, onClick = onRetry) {
            Text(if (errorMessage.isNullOrBlank()) "Retry" else "Retry Authentication")
        }
    }
}

private sealed class AuthAvailability {
    data object Available : AuthAvailability()

    data class Unavailable(val message: String) : AuthAvailability()
}

private fun canAuthenticate(context: android.content.Context): AuthAvailability {
    val biometricManager = BiometricManager.from(context)
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        val biometricResult =
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (biometricResult == BiometricManager.BIOMETRIC_SUCCESS) {
            return AuthAvailability.Available
        }

        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isDeviceSecure == true) {
            return AuthAvailability.Available
        }
    }

    return when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.Available
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            AuthAvailability.Unavailable("This device has no biometric hardware.")
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            AuthAvailability.Unavailable("Biometric hardware is currently unavailable.")
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            AuthAvailability.Unavailable("No biometric or device credential is enrolled.")
        else -> AuthAvailability.Unavailable("Authentication is unavailable on this device.")
    }
}

private fun buildPromptInfo(): BiometricPrompt.PromptInfo {
    val builder =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ZeroIntrusion")
            .setSubtitle("Confirm your identity to continue")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
    } else {
        builder.setDeviceCredentialAllowed(true)
    }

    return builder.build()
}
