package com.zerointrusion.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.presentation.deviceinformation.DeviceInformationUiModel
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import com.zerointrusion.ui.theme.Yellow

@Composable
fun SettingsConnectedDevicesScreen(
    deviceInformation: DeviceInformationUiModel,
    namespaces: List<NamespaceEntry>,
    connectedDeviceGroups: List<ConnectedDeviceGroup>,
    namespaceManagementErrorMessage: String?,
    onDeleteNamespace: (NamespaceEntry) -> Unit,
    onBack: () -> Unit,
) {
    var namespacePendingDelete by remember { mutableStateOf<NamespaceEntry?>(null) }
    var showHeadExplanation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Dark, LightDark, Dark),
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Connected devices",
            style = MaterialTheme.typography.headlineMedium,
            color = White,
        )

        ConnectedDevicesSettingsContent(
            deviceInformation = deviceInformation,
            namespaces = namespaces,
            connectedDeviceGroups = connectedDeviceGroups,
            namespaceManagementErrorMessage = namespaceManagementErrorMessage,
            namespacePendingDelete = namespacePendingDelete,
            onDeleteClick = { namespace ->
                namespacePendingDelete = if (namespacePendingDelete == namespace) null else namespace
            },
            onConfirmDelete = { namespace ->
                namespacePendingDelete = null
                onDeleteNamespace(namespace)
            },
            showHeadExplanation = showHeadExplanation,
            onHeadClick = { showHeadExplanation = !showHeadExplanation },
        )

        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(2.dp, Yellow),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text("Back", color = White)
        }
    }
}

internal data class SettingsConnectedDeviceGroupUiModel(
    val deviceId: String,
    val deviceLabel: String,
    val namespaces: List<NamespaceEntry>,
)

internal fun buildSettingsConnectedDeviceGroups(
    namespaces: List<NamespaceEntry>,
    connectedDeviceGroups: List<ConnectedDeviceGroup>,
): List<SettingsConnectedDeviceGroupUiModel> {
    val namespacesByDeviceId = namespaces.groupBy { it.desktopDeviceId.trim() }
    val deviceIdsInDisplayOrder = linkedSetOf<String>()

    connectedDeviceGroups.forEach { group ->
        val deviceId = group.deviceId.trim()
        if (deviceId.isNotEmpty()) {
            deviceIdsInDisplayOrder += deviceId
        }
    }

    namespacesByDeviceId.keys.filter { it.isNotEmpty() }.sorted().forEach { deviceId ->
        deviceIdsInDisplayOrder += deviceId
    }

    return deviceIdsInDisplayOrder.map { deviceId ->
        val deviceNamespaces = namespacesByDeviceId[deviceId].orEmpty()
            .sortedWith(compareBy({ it.namespaceName.lowercase() }, { it.namespaceUrl.lowercase() }))
        val deviceLabel = deviceNamespaces.firstNotNullOfOrNull { namespace ->
            namespace.deviceName?.trim()?.takeIf { it.isNotEmpty() }
        } ?: deviceId

        SettingsConnectedDeviceGroupUiModel(
            deviceId = deviceId,
            deviceLabel = deviceLabel,
            namespaces = deviceNamespaces,
        )
    }.sortedWith(compareBy({ it.deviceLabel.lowercase() }, { it.deviceId.lowercase() }))
}

@Composable
private fun ConnectedDevicesSettingsContent(
    deviceInformation: DeviceInformationUiModel,
    namespaces: List<NamespaceEntry>,
    connectedDeviceGroups: List<ConnectedDeviceGroup>,
    namespaceManagementErrorMessage: String?,
    namespacePendingDelete: NamespaceEntry?,
    onDeleteClick: (NamespaceEntry) -> Unit,
    onConfirmDelete: (NamespaceEntry) -> Unit,
    showHeadExplanation: Boolean,
    onHeadClick: () -> Unit,
) {
    val visibleNamespaces = namespaces
    val groupedDevices = remember(visibleNamespaces, connectedDeviceGroups) {
        buildSettingsConnectedDeviceGroups(
            namespaces = visibleNamespaces,
            connectedDeviceGroups = connectedDeviceGroups,
        )
    }

    namespaceManagementErrorMessage?.takeIf { it.isNotBlank() }?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ErrorTextLight,
        )
    }

    if (groupedDevices.isEmpty()) {
        Text(
            text = "No connected devices stored yet.",
            color = White,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groupedDevices.forEach { group ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Dark.copy(alpha = 0.36f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = LightDark,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Root device",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = White.copy(alpha = 0.72f),
                                )
                                Text(
                                    text = group.deviceLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = White,
                                )
                                Text(
                                    text = "Desktop ID: ${group.deviceId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = White.copy(alpha = 0.72f),
                                )
                            }

                        }
                    }

                    Column(
                        modifier = Modifier.padding(start = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    )
                    {
                        if (group.namespaces.isEmpty()) {
                            Text(
                                text = "No stored namespace connections for this device.",
                                color = White.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                            )
                        } else {
                            group.namespaces.forEachIndexed { index, namespace ->
                                NamespaceConnectionCard(
                                    namespace = namespace,
                                    isGateway = namespace.accountId == deviceInformation.accountId &&
                                        namespace.namespaceId == deviceInformation.namespaceId &&
                                        namespace.namespaceName == deviceInformation.namespaceName &&
                                        namespace.namespaceUrl == deviceInformation.namespaceUrl,
                                    showDeleteConfirmation = namespacePendingDelete == namespace,
                                    onDeleteClick = { onDeleteClick(namespace) },
                                    onConfirmDelete = { onConfirmDelete(namespace) },
                                    showHeadExplanation = showHeadExplanation,
                                    onHeadClick = onHeadClick,
                                )
                                if (index != group.namespaces.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 26.dp, top = 10.dp, bottom = 10.dp),
                                        thickness = 1.dp,
                                        color = White.copy(alpha = 0.18f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NamespaceConnectionCard(
    namespace: NamespaceEntry,
    isGateway: Boolean,
    showDeleteConfirmation: Boolean,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    showHeadExplanation: Boolean,
    onHeadClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NamespaceConnector()

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = namespace.namespaceName,
                    color = White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )

                if (isGateway) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Yellow.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Yellow),
                        modifier = Modifier.height(24.dp),
                        onClick = onHeadClick,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Head",
                                color = Yellow,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                } else {
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete namespace",
                            tint = ActionDeclineRed,
                        )
                    }
                }
            }

            if (isGateway && showHeadExplanation) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Yellow.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Yellow.copy(alpha = 0.55f)),
                ) {
                    Text(
                        text = "The server is configured as the root server, which is a prerequisite for multiple-server support. Before removing the \"Head\" server, if there is an additional server, the Head must first be changed. Multiple-server support enables independent credential databases for each server. Removing a server, including the \"Head\" server, permanently removes only the credentials stored in the database assigned to that server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Text(
                text = namespace.namespaceUrl,
                color = White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Account ID: ${namespace.accountId}",
                color = White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Desktop ID: ${namespace.desktopDeviceId}",
                color = White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Namespace ID: ${namespace.namespaceId}",
                color = White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )

            if (showDeleteConfirmation) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, ActionDeclineRed), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = ActionDeclineRed.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = "Deleting this namespace removes the stored registration for this desktop connection. This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Button(
                    onClick = onConfirmDelete,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActionDeclineRed,
                        contentColor = White,
                    ),
                ) {
                    Text("Confirm delete")
                }
            }
        }
    }
}

@Composable
private fun NamespaceConnector() {
    Column(
        modifier = Modifier.padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(width = 2.dp, height = 14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(White.copy(alpha = 0.32f)),
        )
        Box(
            modifier = Modifier
                .size(12.dp, 2.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(White.copy(alpha = 0.32f)),
        )
    }
}

