package com.zerointrusion.presentation.auth

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zerointrusion.presentation.common.AppBackground
import com.zerointrusion.presentation.common.BannerTone
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

    val biometricPrompt: BiometricPrompt? = remember(activity) {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(it),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            onAuthenticationFailed(errString.toString())
                        }
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
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
            AuthAvailability.Available -> prompt.authenticate(buildPromptInfo())
            is AuthAvailability.Unavailable -> onAuthenticationFailed(status.message)
        }
    }

    AppBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Device information")
                        }
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Qr-Read")
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No active notification",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Unlock ZeroIntrusion to interact with notifications.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    StatusBanner(
                        title = "Authentication blocked",
                        message = errorMessage,
                        tone = BannerTone.Error,
                    )
                    Button(
                        onClick = { promptNonce++ },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Text("Retry Authentication")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp)),
                ) {
                    Text("Allow")
                }
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp)),
                ) {
                    Text("Decline")
                }
            }

            if (errorMessage.isNullOrBlank()) {
                Button(
                    onClick = { promptNonce++ },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

private sealed class AuthAvailability {
    data object Available : AuthAvailability()
    data class Unavailable(val message: String) : AuthAvailability()
}

private fun canAuthenticate(context: android.content.Context): AuthAvailability {
    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        val biometricResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
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
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> AuthAvailability.Unavailable("This device has no biometric hardware.")
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> AuthAvailability.Unavailable("Biometric hardware is currently unavailable.")
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthAvailability.Unavailable("No biometric or device credential is enrolled.")
        else -> AuthAvailability.Unavailable("Authentication is unavailable on this device.")
    }
}

private fun buildPromptInfo(): BiometricPrompt.PromptInfo {
    val builder = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock ZeroIntrusion")
        .setSubtitle("Confirm your identity to continue")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
    } else {
        builder.setDeviceCredentialAllowed(true)
    }

    return builder.build()
}