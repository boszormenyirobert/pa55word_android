package com.zerointrusion.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.app.AddDatabaseUiState
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppSettingsPage
import com.zerointrusion.presentation.common.AppSettingsPanel
import com.zerointrusion.ui.theme.ErrorTextLight

@Composable
fun AddDatabaseScreen(
    state: AddDatabaseUiState,
    onNameChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(enabled = state.isSubmitting) {}
    AppSettingsPage(
        title = "Add new database",
        onBack = onBack,
        modifier = Modifier.imePadding(),
        backEnabled = !state.isSubmitting,
        secondaryAction = {
            AppButton(
                icon = Icons.Default.AddLink,
                onClick = onSubmit,
                enabled = !state.isSubmitting && !state.isSuccess,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.isSubmitting) "Connecting..." else "Connect")
            }
        },
    ) {
        AppSettingsPanel {
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChanged,
                label = { Text("Email") },
                enabled = !state.isSubmitting,
                isError = state.emailError != null,
                supportingText = { state.emailError?.let { Text(it) } },
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChanged,
                label = { Text("Name") },
                enabled = !state.isSubmitting,
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChanged,
                label = { Text("URL") },
                enabled = !state.isSubmitting,
                isError = state.urlError != null,
                supportingText = {
                    Text(
                        state.urlError ?: "Server base URL, for example http://192.168.178.20:3000."
                    )
                },
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            state.errorMessage?.let {
                Text(it, color = ErrorTextLight, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
