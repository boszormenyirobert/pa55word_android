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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.common.AppBackground
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.BannerTone
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
    isExistingAccount: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
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
            Text(
                text = if (isExistingAccount) "Add new mobile to existing account" else "New account registration",
                style = MaterialTheme.typography.headlineSmall,
            )
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Registration details",
                        style = MaterialTheme.typography.titleLarge,
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
                            icon = Icons.Default.Check,
                            text = "Confirm",
                            onClick = onConfirm,
                            enabled = !state.isAwaitingMobileRegistration,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AppButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Back")
                    }
                    state.mobileRegistrationQrPayload?.takeIf { isExistingAccount }?.let { payload ->
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
        }
    }
}
