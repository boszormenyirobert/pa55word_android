package com.zerointrusion.presentation.deviceinformation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
    val selectedNamespace = namespaces.firstOrNull { namespace ->
        namespace.namespaceId == model.namespaceId &&
            namespace.namespaceUrl == model.namespaceUrl &&
            namespace.accountId == model.accountId
    }
    var pendingNamespace by remember(
        model.accountId,
        model.namespaceId,
        model.namespaceUrl,
        namespaces,
    ) {
        mutableStateOf(selectedNamespace)
    }
    val hasPendingNamespaceChange = pendingNamespace != null && pendingNamespace != selectedNamespace

    Text(
        text = "Account ID: ${model.accountId.ifBlank { "(empty)" }}",
        style = MaterialTheme.typography.bodyMedium,
        color = White,
    )

    Text(
        text = "Device ID: ${model.deviceId.ifBlank { "(empty)" }}",
        style = MaterialTheme.typography.bodyMedium,
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
            DeviceInformationRow(label = "Namespace Name", value = model.namespaceName)
            DeviceInformationRow(label = "Namespace ID", value = model.namespaceId)
            DeviceInformationRow(label = "Namespace URL", value = model.namespaceUrl)
            DeviceInformationRow(label = "Email", value = model.email)
        }
    }

    Text(
        text = "For a completely new desktop registration, this namespace is set as the default gateway. If this namespace is deleted later and the server is no longer reachable, please choose another namespace that can become the new default.",
        style = MaterialTheme.typography.bodyMedium,
        color = White,
    )

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { if (namespaces.isNotEmpty()) isExpanded = !isExpanded },
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
                .menuAnchor()
                .fillMaxWidth(),
        )

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            namespaces.forEach { namespace ->
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

    Button(
        onClick = {
            pendingNamespace?.let(onNamespaceSelected)
        },
        enabled = hasPendingNamespaceChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LightDark,
            contentColor = White,
        ),
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