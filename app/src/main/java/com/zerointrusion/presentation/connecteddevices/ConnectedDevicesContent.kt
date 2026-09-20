package com.zerointrusion.presentation.connecteddevices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceNamespace
import com.zerointrusion.presentation.app.ConnectedDevicesUiState
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

@Composable
internal fun ConnectedDevicesContent(
    state: ConnectedDevicesUiState,
    onRetry: () -> Unit,
) {
    when (state) {
        ConnectedDevicesUiState.Idle -> Text(
            text = "No data loaded yet.",
            color = White,
        )

        ConnectedDevicesUiState.InProgress -> {
            CircularProgressIndicator()
            Text(
                text = "Loading connected devices...",
                color = White,
            )
        }

        is ConnectedDevicesUiState.Success -> {
            if (state.groups.isEmpty()) {
                Text(
                    text = "No connected devices found.",
                    color = White,
                )
            } else {
                state.groups.forEach { group ->
                    ConnectedDeviceGroupCard(group = group)
                }
            }
        }

        is ConnectedDevicesUiState.Error -> {
            Text(
                text = state.message,
                color = ErrorTextLight,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ConnectedDeviceGroupCard(group: ConnectedDeviceGroup) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Dark.copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Account ID: ${group.accountId}",
                color = White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Device ID: ${group.deviceId}",
                color = White,
                style = MaterialTheme.typography.bodyMedium,
            )
            group.deviceName?.takeIf { it.isNotBlank() }?.let { deviceName ->
                Text(
                    text = "Device name: $deviceName",
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (group.namespaces.isEmpty()) {
                Text(
                    text = "No namespaces returned for this device.",
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                group.namespaces.forEach { namespace ->
                    ConnectedNamespaceRow(namespace = namespace)
                }
            }
        }
    }
}

@Composable
private fun ConnectedNamespaceRow(namespace: ConnectedDeviceNamespace) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Account ID:",
                    color = White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = namespace.accountId,
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Namespace:",
                    color = White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = namespace.namespaceName,
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Desktop ID:",
                    color = White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = namespace.desktopDeviceId,
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Namespace ID:",
                    color = White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = namespace.namespaceId,
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "URL:",
                    color = White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = namespace.namespaceUrl,
                    color = White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (namespace.deviceId != namespace.desktopDeviceId) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Device ID fallback:",
                        color = White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = namespace.deviceId,
                        color = White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}