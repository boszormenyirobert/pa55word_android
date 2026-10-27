package com.zerointrusion.presentation.connecteddevices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.connecteddevices.RemovableAccountDevice
import com.zerointrusion.presentation.app.DeviceRemovalUiState
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppIconButton
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import com.zerointrusion.ui.theme.Yellow

@Composable
fun DeviceRemovalScreen(
    currentMobileDeviceId: String,
    state: DeviceRemovalUiState,
    onLoad: () -> Unit,
    onDelete: (RemovableAccountDevice) -> Unit,
    onBack: () -> Unit,
) {
    var pendingDeviceId by remember { mutableStateOf<String?>(null) }
    var isServerMenuExpanded by remember { mutableStateOf(false) }
    var selectedServerUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onLoad() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Dark, LightDark, Dark)))
            .verticalScroll(rememberScrollState())
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Remove devices",
            style = MaterialTheme.typography.headlineMedium,
            color = White,
        )

        when (state) {
            DeviceRemovalUiState.Idle,
            DeviceRemovalUiState.InProgress -> Text("Loading devices...", color = White)
            is DeviceRemovalUiState.Error -> {
                Text(state.message, color = ErrorTextLight)
                AppButton(icon = Icons.Default.Refresh, onClick = onLoad, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
            }
            is DeviceRemovalUiState.Success -> {
                state.errorMessage?.let { Text(it, color = ErrorTextLight) }
                val serverUrls = state.devices
                    .map { device -> device.namespaceUrl.trim().trimEnd('/') }
                    .filter { serverUrl -> serverUrl.isNotEmpty() }
                    .distinct()
                    .sorted()
                LaunchedEffect(serverUrls) {
                    if (selectedServerUrl !in serverUrls) {
                        selectedServerUrl = serverUrls.firstOrNull()
                    }
                }
                val displayedDevices = state.devices.filter { device ->
                    selectedServerUrl == null ||
                        device.namespaceUrl.trim().trimEnd('/') == selectedServerUrl
                }

                if (serverUrls.isNotEmpty()) {
                    ServerUrlDropdown(
                        serverUrls = serverUrls,
                        selectedServerUrl = selectedServerUrl ?: serverUrls.first(),
                        expanded = isServerMenuExpanded,
                        onExpandedChange = { isServerMenuExpanded = it },
                        onServerSelected = { serverUrl ->
                            selectedServerUrl = serverUrl
                            isServerMenuExpanded = false
                        },
                    )
                }

                if (displayedDevices.isEmpty()) {
                    Text("No devices are available to remove.", color = White)
                } else {
                    displayedDevices.forEach { device ->
                        DeviceRemovalCard(
                            device = device,
                            isCurrentPhone = device.targetMobileDeviceId == currentMobileDeviceId,
                            deleting = state.deletingDeviceId == device.id,
                            showConfirmation = pendingDeviceId == device.id,
                            onRequestDelete = { pendingDeviceId = device.id },
                            onCancelDelete = { pendingDeviceId = null },
                            onConfirmDelete = {
                                pendingDeviceId = null
                                onDelete(device)
                            },
                        )
                    }
                }
            }
        }

        AppButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text("Back")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerUrlDropdown(
    serverUrls: List<String>,
    selectedServerUrl: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onServerSelected: (String) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        OutlinedTextField(
            value = selectedServerUrl,
            onValueChange = {},
            readOnly = true,
            label = { Text("Server") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            serverUrls.forEach { serverUrl ->
                DropdownMenuItem(
                    text = { Text(serverUrl) },
                    onClick = { onServerSelected(serverUrl) },
                )
            }
        }
    }
}

@Composable
private fun DeviceRemovalCard(
    device: RemovableAccountDevice,
    isCurrentPhone: Boolean,
    deleting: Boolean,
    showConfirmation: Boolean,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (device.isMobile) "Mobile device" else "Desktop device",
                style = MaterialTheme.typography.labelLarge,
                color = White.copy(alpha = 0.72f),
            )
            if (isCurrentPhone) {
                Text(
                    text = "Current phone",
                    style = MaterialTheme.typography.labelLarge,
                    color = Yellow,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = device.deviceName ?: device.id,
                        style = MaterialTheme.typography.titleMedium,
                        color = White,
                    )
                    Text(
                        text = "ID: ${device.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.72f),
                    )
                }
                AppIconButton(
                    onClick = onRequestDelete,
                    enabled = !deleting && !showConfirmation,
                    color = ActionDeclineRed,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove ${device.deviceName ?: device.id}",
                        tint = ActionDeclineRed,
                    )
                }
            }
            Text(
                text = "Server: ${device.namespaceUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = White.copy(alpha = 0.72f),
            )
            device.lastTimeUsed?.let { lastTimeUsed ->
                Text(
                    text = "Last used: $lastTimeUsed",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.72f),
                )
            }
            if (showConfirmation) {
                Text("Remove this device from the account?", color = White)
                AppButton(
                    icon = Icons.Default.Delete,
                    onClick = onConfirmDelete,
                    enabled = !deleting,
                    color = ActionDeclineRed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (deleting) "Removing..." else "Confirm removal")
                }
                AppButton(icon = Icons.Default.Close, onClick = onCancelDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}