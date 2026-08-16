package com.zerointrusion.presentation.namespaces

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import com.zerointrusion.ui.theme.Yellow

@Composable
fun NamespacesScreen(
    namespaces: List<NamespaceEntry>,
    gatewayAccountId: String,
    gatewayNamespaceId: String,
    gatewayNamespaceName: String,
    gatewayNamespaceUrl: String,
    errorMessage: String?,
    onDeleteNamespace: (NamespaceEntry) -> Unit,
    onBack: () -> Unit,
) {
    var namespacePendingDelete by remember { mutableStateOf<NamespaceEntry?>(null) }

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
            text = "Namespaces",
            style = MaterialTheme.typography.headlineMedium,
            color = White,
        )

        errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorTextLight,
            )
        }

        if (namespaces.isEmpty()) {
            NamespaceCard(
                title = "No namespaces stored yet.",
                body = null,
                isGateway = false,
                showDeleteConfirmation = false,
                onDeleteClick = {},
                onConfirmDelete = {},
            )
        } else {
            namespaces.forEach { namespace ->
                val isGateway = namespace.accountId == gatewayAccountId &&
                    namespace.namespaceId == gatewayNamespaceId &&
                    namespace.namespaceName == gatewayNamespaceName &&
                    namespace.namespaceUrl == gatewayNamespaceUrl

                NamespaceCard(
                    title = namespace.namespaceName,
                    body = listOf(
                        "Namespace ID: ${namespace.namespaceId}",
                        "Namespace URL: ${namespace.namespaceUrl}",
                        "Account ID: ${namespace.accountId}",
                        "Device ID: ${namespace.deviceId}",
                        "Device name: ${namespace.deviceName.orEmpty()}",
                    ),
                    isGateway = isGateway,
                    showDeleteConfirmation = namespacePendingDelete == namespace,
                    onDeleteClick = {
                        namespacePendingDelete = if (namespacePendingDelete == namespace) null else namespace
                    },
                    onConfirmDelete = {
                        namespacePendingDelete = null
                        onDeleteNamespace(namespace)
                    },
                )
            }
        }

        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(2.dp, Yellow),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text("Back to main view", color = White)
        }
    }
}

@Composable
private fun NamespaceCard(
    title: String,
    body: List<String>?,
    isGateway: Boolean,
    showDeleteConfirmation: Boolean,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, White.copy(alpha = 0.28f)), RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.size(8.dp))
                if (isGateway) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Yellow.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Yellow),
                    ) {
                        Text(
                            text = "Gateway",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Yellow,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete namespace",
                            tint = ActionDeclineRed,
                        )
                    }
                }
            }
            body?.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = White,
                )
            }

            if (isGateway) {
                Text(
                    text = "To delete this namespace, first set another gateway in Device settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Yellow,
                )
            } else if (showDeleteConfirmation) {
                Spacer(modifier = Modifier.size(4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, ActionDeclineRed), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = ActionDeclineRed.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = "Deleting this namespace ends the account registration under \"$title\". The credentials saved for the deregistration flow under this namespace will be permanently deleted. This action is final and cannot be undone.",
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