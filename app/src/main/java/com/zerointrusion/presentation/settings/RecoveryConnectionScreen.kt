package com.zerointrusion.presentation.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppSettingsPage
import com.zerointrusion.presentation.common.AppSettingsPanel

@Composable
fun RecoveryConnectionScreen(
    onBack: () -> Unit,
    onCancelScan: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppSettingsPage(
        title = "Corporate recovery",
        onBack = onBack,
        secondaryAction = if (onCancelScan != null) {
            {
                AppButton(
                    icon = Icons.Default.Close,
                    onClick = onCancelScan,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel scan")
                }
            }
        } else {
            null
        },
    ) {
        AppSettingsPanel {
            Text(
                text = "Use this feature if you have lost the phone linked to your corporate account. Start the recovery process with an email confirmation, as described in the desktop application. Then scan the recovery QR code displayed on your desktop device to restore the connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        content()
    }
}
