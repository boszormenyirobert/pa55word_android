package com.zerointrusion.presentation.registration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppSettingsPage
import com.zerointrusion.presentation.common.BannerTone
import com.zerointrusion.presentation.common.QrCodeImage
import com.zerointrusion.presentation.common.RegistrationTextField
import com.zerointrusion.presentation.common.StatusBanner

@Composable
fun RegistrationScreen(
    state: RegistrationUiState,
    onEmailChanged: (String) -> Unit,
    onNamespaceNameChanged: (String) -> Unit,
    onApiUrlChanged: (String) -> Unit,
    isExistingAccount: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    AppSettingsPage(
        title = if (isExistingAccount) "Add new mobile to existing account" else "New account registration",
        onBack = onBack,
        modifier = Modifier.imePadding(),
        secondaryAction = {
            AppButton(
                icon = Icons.Default.Check,
                onClick = onConfirm,
                enabled = !state.isAwaitingMobileRegistration,
                modifier = Modifier.weight(1f),
            ) {
                Text("Confirm")
            }
        },
    ) {
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
                if (!state.errorMessage.isNullOrBlank()) {
                    StatusBanner(
                        title = "Registration failed",
                        message = state.errorMessage,
                        tone = BannerTone.Error,
                    )
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
