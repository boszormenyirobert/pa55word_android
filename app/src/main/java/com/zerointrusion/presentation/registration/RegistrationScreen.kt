package com.zerointrusion.presentation.registration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.common.AppBackground
import com.zerointrusion.presentation.common.BannerTone
import com.zerointrusion.presentation.common.ChecklistLine
import com.zerointrusion.presentation.common.HeroPanel
import com.zerointrusion.presentation.common.PillLabel
import com.zerointrusion.presentation.common.PrimaryActionButton
import com.zerointrusion.presentation.common.QrCodeImage
import com.zerointrusion.presentation.common.RegistrationTextField
import com.zerointrusion.presentation.common.StatusBanner

@Composable
fun RegistrationScreen(
    state: RegistrationUiState,
    onEmailChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onNamespaceNameChanged: (String) -> Unit,
    onApiUrlChanged: (String) -> Unit,
    onAdditionalApiUrlChanged: (String) -> Unit,
    onThirdApiUrlChanged: (String) -> Unit,
    onRegister: () -> Unit,
    onRegisterNewMobile: () -> Unit,
) {
    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HeroPanel(
                eyebrow = "Milestone 7",
                title = "Secure device registration, with the dangerous parts kept out of sight.",
                body = "This flow generates the keystore keypair, builds the backend payload and stores the returned account state without exposing sensitive material in the UI.",
            )
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillLabel(text = "Plain POST /api/v1/account")
                        PillLabel(text = "Local signature only")
                    }
                    Text(
                        text = "Registration details",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Provide the namespace endpoint and identity fields. The app derives device identifiers and encrypted key material locally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RegistrationTextField(
                        value = state.email,
                        onValueChange = onEmailChanged,
                        label = "Email",
                        supporting = state.emailError ?: "Used in the registration payload only.",
                        isError = state.emailError != null,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    )
                    RegistrationTextField(
                        value = state.phone,
                        onValueChange = onPhoneChanged,
                        label = "Phone",
                        supporting = state.phoneError ?: "International format is recommended.",
                        isError = state.phoneError != null,
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    )
                    RegistrationTextField(
                        value = state.namespaceName,
                        onValueChange = onNamespaceNameChanged,
                        label = "Namespace Name",
                        supporting = state.namespaceNameError ?: "Shown in the stored registration state.",
                        isError = state.namespaceNameError != null,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    )
                    RegistrationTextField(
                        value = state.apiUrl,
                        onValueChange = onApiUrlChanged,
                        label = "API URL",
                        supporting = state.apiUrlError ?: "Base URL with port only, for example http://192.168.178.20:3000. The app appends /api/v1/account.",
                        isError = state.apiUrlError != null,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    )
                    RegistrationTextField(
                        value = state.additionalApiUrl,
                        onValueChange = onAdditionalApiUrlChanged,
                        label = "Additional API URL (optional)",
                        supporting = state.additionalApiUrlError ?: "Second existing-account server URL.",
                        isError = state.additionalApiUrlError != null,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    )
                    RegistrationTextField(
                        value = state.thirdApiUrl,
                        onValueChange = onThirdApiUrlChanged,
                        label = "Third API URL (optional)",
                        supporting = state.thirdApiUrlError ?: "Third existing-account server URL.",
                        isError = state.thirdApiUrlError != null,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    )
                    if (!state.errorMessage.isNullOrBlank()) {
                        StatusBanner(
                            title = "Registration failed",
                            message = state.errorMessage,
                            tone = BannerTone.Error,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PrimaryActionButton(
                            text = "Register Device",
                            onClick = onRegister,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedButton(
                        onClick = onRegisterNewMobile,
                        enabled = !state.isAwaitingMobileRegistration,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("New mobile to existing account")
                    }
                    state.mobileRegistrationQrPayload?.let { payload ->
                        Text(
                            text = "Scan this code with a mobile device already connected to the account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        QrCodeImage(
                            payload = payload,
                            contentDescription = "New mobile registration QR code",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                        Text(
                            text = "Waiting for approval...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "What happens next",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    ChecklistLine("An RSA keypair is kept inside Android Keystore.")
                    ChecklistLine("A Base64 X.509 public key and RSA-OAEP encrypted DEK are generated locally.")
                    ChecklistLine("The returned account ID is stored securely after success.")
                    ChecklistLine("Sensitive crypto values are never rendered or logged in the UI.")
                }
            }
        }
    }
}
