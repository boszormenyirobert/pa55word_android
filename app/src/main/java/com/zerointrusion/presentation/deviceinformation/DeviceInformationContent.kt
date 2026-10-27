package com.zerointrusion.presentation.deviceinformation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceInformationContent(
    model: DeviceInformationUiModel,
    namespaces: List<NamespaceEntry>,
    errorMessage: String?,
    onNamespaceSelected: (NamespaceEntry) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val visibleNamespaces = namespaces
    val selectedNamespace = visibleNamespaces.firstOrNull { namespace ->
        namespace.namespaceId == model.namespaceId &&
            namespace.namespaceUrl == model.namespaceUrl &&
            namespace.accountId == model.accountId
    }
    var pendingNamespace by remember(
        model.accountId,
        model.namespaceId,
        model.namespaceUrl,
        visibleNamespaces,
    ) {
        mutableStateOf(selectedNamespace)
    }
    val hasPendingNamespaceChange = pendingNamespace != null && pendingNamespace != selectedNamespace

    Text(
        text = "Root device name: ${model.deviceName.ifBlank { "(empty)" }}",
        style = MaterialTheme.typography.headlineSmall,
        color = White,
    )

    errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Backup connection device",
                style = MaterialTheme.typography.titleMedium,
                color = White,
            )
            DeviceInformationRow(label = "Name", value = model.namespaceName)
            DeviceInformationRow(label = "URL", value = model.namespaceUrl)
        }
    }

    Text(
        text = "A valid namespace must always be set here so the option to add a new Desktop as an end root-device remains available. If the current namespace is no longer valid or reachable, select another valid namespace first.",
        style = MaterialTheme.typography.bodyMedium,
        color = White,
    )

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { if (visibleNamespaces.isNotEmpty()) isExpanded = !isExpanded },
    ) {
        OutlinedTextField(
            value = pendingNamespace?.namespaceName ?: model.namespaceName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Default namespace") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
            },
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                        enabled = visibleNamespaces.isNotEmpty(),
                )
                .fillMaxWidth(),
        )

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            visibleNamespaces.forEach { namespace ->
                DropdownMenuItem(
                    text = { Text(namespace.namespaceName) },
                    onClick = {
                        isExpanded = false
                        pendingNamespace = namespace
                    },
                )
            }
        }
    }

    AppButton(
        icon = Icons.Default.Save,
        onClick = {
            pendingNamespace?.let(onNamespaceSelected)
        },
        enabled = hasPendingNamespaceChange,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save")
    }
}

@Composable
private fun DeviceInformationRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = White, style = MaterialTheme.typography.titleSmall)
        Text(text = value.ifBlank { "(empty)" }, color = White, style = MaterialTheme.typography.bodyMedium)
    }
}