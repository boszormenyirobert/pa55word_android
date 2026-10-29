package com.zerointrusion.presentation.qrread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.app.QrNamespaceRegistrationUiState
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppSettingsPage
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.Green
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

@Composable
fun QrNamespaceDeviceSelectionScreen(
    qrNamespaceRegistrationState: QrNamespaceRegistrationUiState,
    onConnectedDeviceSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    AppSettingsPage(title = "Connected device selection", onBack = onBack) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightDark),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (qrNamespaceRegistrationState) {
                    is QrNamespaceRegistrationUiState.AwaitingDeviceSelection -> {
                        var expanded by
                            remember(qrNamespaceRegistrationState.deviceOptions) {
                                mutableStateOf(false)
                            }
                        var selectedDeviceId by
                            remember(qrNamespaceRegistrationState.deviceOptions) {
                                mutableStateOf(
                                    qrNamespaceRegistrationState.deviceOptions
                                        .firstOrNull()
                                        ?.deviceId
                                )
                            }
                        val selectedOption =
                            qrNamespaceRegistrationState.deviceOptions.firstOrNull { option ->
                                option.deviceId == selectedDeviceId
                            }

                        Text(
                            text =
                                if (qrNamespaceRegistrationState.namespaceName.isBlank()) {
                                    "Select the connected device that should be linked to the scanned namespace."
                                } else {
                                    "Select the connected device for namespace: ${qrNamespaceRegistrationState.namespaceName}"
                                },
                            color = White,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            AppButton(
                                icon = Icons.Default.Devices,
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text =
                                        selectedOption?.let { option ->
                                            "${option.deviceName} (${option.deviceId})"
                                        } ?: "Select connected device",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                Column(
                                    modifier =
                                        Modifier.heightIn(max = 280.dp)
                                            .verticalScroll(rememberScrollState())
                                ) {
                                    qrNamespaceRegistrationState.deviceOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text("${option.deviceName} (${option.deviceId})")
                                            },
                                            onClick = {
                                                selectedDeviceId = option.deviceId
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        AppButton(
                            icon = Icons.Default.Link,
                            onClick = { selectedDeviceId?.let(onConnectedDeviceSelected) },
                            enabled = selectedDeviceId != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Connect")
                        }
                    }

                    QrNamespaceRegistrationUiState.Idle ->
                        Text(
                            text = "Scan a namespace QR code first.",
                            color = White,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                    QrNamespaceRegistrationUiState.InProgress ->
                        Text(
                            text = "Connecting scanned namespace...",
                            color = White,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                    is QrNamespaceRegistrationUiState.Success ->
                        Text(
                            text = qrNamespaceRegistrationState.message,
                            color = Green,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                    is QrNamespaceRegistrationUiState.Error ->
                        Text(
                            text = qrNamespaceRegistrationState.message,
                            color = ErrorTextLight,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                }
            }
        }
    }
}
