package com.zerointrusion.presentation.qrread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.app.QrNamespaceRegistrationUiState
import com.zerointrusion.ui.theme.Dark
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
            text = "Connected device selection",
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
                when (qrNamespaceRegistrationState) {
                    is QrNamespaceRegistrationUiState.AwaitingDeviceSelection -> {
                        var expanded by remember(qrNamespaceRegistrationState.deviceOptions) { mutableStateOf(false) }
                        var selectedDeviceId by remember(qrNamespaceRegistrationState.deviceOptions) {
                            mutableStateOf(qrNamespaceRegistrationState.deviceOptions.firstOrNull()?.deviceId)
                        }
                        val selectedOption = qrNamespaceRegistrationState.deviceOptions.firstOrNull { option ->
                            option.deviceId == selectedDeviceId
                        }

                        Text(
                            text = if (qrNamespaceRegistrationState.namespaceName.isBlank()) {
                                "Select the connected device that should be linked to the scanned namespace."
                            } else {
                                "Select the connected device for namespace: ${qrNamespaceRegistrationState.namespaceName}"
                            },
                            color = White,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(
                                    text = selectedOption?.deviceName ?: "Select connected device",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                qrNamespaceRegistrationState.deviceOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(option.deviceName)
                                        },
                                        onClick = {
                                            selectedDeviceId = option.deviceId
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { selectedDeviceId?.let(onConnectedDeviceSelected) },
                            enabled = selectedDeviceId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Connect")
                        }
                    }

                    QrNamespaceRegistrationUiState.Idle -> Text(
                        text = "Scan a namespace QR code first.",
                        color = White,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    QrNamespaceRegistrationUiState.InProgress -> Text(
                        text = "Connecting scanned namespace...",
                        color = White,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    is QrNamespaceRegistrationUiState.Success -> Text(
                        text = "Namespace connected: ${qrNamespaceRegistrationState.namespaceName} (desktop: ${qrNamespaceRegistrationState.desktopDeviceId})",
                        color = Green,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    is QrNamespaceRegistrationUiState.Error -> Text(
                        text = qrNamespaceRegistrationState.message,
                        color = ErrorTextLight,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back")
        }
    }
}