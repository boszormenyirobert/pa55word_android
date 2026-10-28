package com.zerointrusion.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppIconButton
import com.zerointrusion.presentation.common.AppSettingsPage
import com.zerointrusion.ui.theme.ActionDeclineRed
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

private data class AccountListItem(val namespaceUrl: String, val accountId: String)

@Composable
fun AccountsScreen(
    namespaces: List<NamespaceEntry>,
    deleting: Boolean,
    errorMessage: String?,
    onDeleteAccount: (namespaceUrl: String, accountId: String) -> Unit,
    onAddDatabase: () -> Unit,
    onBack: () -> Unit,
) {
    val accounts =
        remember(namespaces) {
            namespaces
                .map { namespace ->
                    AccountListItem(
                        namespaceUrl = namespace.namespaceUrl.trim().trimEnd('/'),
                        accountId = namespace.accountId.trim(),
                    )
                }
                .filter { item -> item.namespaceUrl.isNotEmpty() && item.accountId.isNotEmpty() }
                .distinctBy { item ->
                    item.namespaceUrl.lowercase() + "|" + item.accountId.lowercase()
                }
                .sortedWith(
                    compareBy(
                        { item -> item.namespaceUrl.lowercase() },
                        { item -> item.accountId.lowercase() },
                    )
                )
        }
    var pendingDeletion by remember { mutableStateOf<AccountListItem?>(null) }

    pendingDeletion?.let { account ->
        AccountDeleteConfirmation(
            account = account,
            isLastAccount = accounts.size == 1,
            deleting = deleting,
            onConfirm = {
                onDeleteAccount(account.namespaceUrl, account.accountId)
                pendingDeletion = null
            },
            onBack = { pendingDeletion = null },
        )
    }

    AppSettingsPage(
        title = "Accounts",
        onBack = onBack,
        secondaryAction = {
            AppButton(
                icon = Icons.Default.Add,
                onClick = onAddDatabase,
                enabled = !deleting,
                modifier = Modifier.weight(1f),
            ) {
                Text("Add")
            }
        },
    ) {
        errorMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { message ->
                Text(
                    text = message,
                    color = ErrorTextLight,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

        if (accounts.isEmpty()) {
            Text(
                text = "No accounts stored on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = White,
            )
        } else {
            accounts.forEach { account ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = LightDark),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = account.namespaceUrl,
                                color = White,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = account.accountId,
                                color = White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        AppIconButton(
                            onClick = { pendingDeletion = account },
                            enabled = !deleting,
                            color = ActionDeclineRed,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete account",
                                tint = ActionDeclineRed,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDeleteConfirmation(
    account: AccountListItem,
    isLastAccount: Boolean,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onBack() },
        title = { Text("Delete account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Deleting this account deregisters this device from ${account.namespaceUrl}. All credentials stored on that server will be removed. This action is irreversible."
                )
                if (isLastAccount) {
                    Text(
                        "This is the last account on this device. After deletion, the application can be removed from this device. To use the service again, reinstall the mobile application and register it again."
                    )
                }
            }
        },
        dismissButton = {
            AppButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                enabled = !deleting,
            ) {
                Text("Back")
            }
        },
        confirmButton = {
            AppButton(
                icon = Icons.Default.Delete,
                onClick = onConfirm,
                enabled = !deleting,
                color = ActionDeclineRed,
            ) {
                Text(if (deleting) "Deleting..." else "Confirm")
            }
        },
    )
}
