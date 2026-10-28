package com.zerointrusion.presentation.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.common.AppMenuButton
import com.zerointrusion.presentation.common.AppSettingsPage
import com.zerointrusion.presentation.common.AppSettingsPanel

@Composable
fun SettingsScreen(
    onOpenAccounts: () -> Unit,
    onOpenRecoveryConnection: () -> Unit,
    onOpenDevices: () -> Unit,
    onBack: () -> Unit,
    corporateMode: Boolean = false,
    onCorporateModeChanged: (Boolean) -> Unit = {},
    errorCode: String? = null,
) {
    AppSettingsPage(title = "Settings", onBack = onBack) {
        SettingsModeSelector(corporateMode, onCorporateModeChanged)
        if (errorCode != null) {
            Text(errorCode, color = MaterialTheme.colorScheme.error)
        }
        AppSettingsPanel(cornerRadius = 24.dp) {
            if (corporateMode) {
                AppMenuButton("Database connections", Icons.Default.Storage, onOpenAccounts)
            }
            AppMenuButton("Devices", Icons.Default.Devices, onOpenDevices)
            if (corporateMode) {
                AppMenuButton("Corporate recovery", Icons.Default.Restore, onOpenRecoveryConnection)
            }
        }
    }
}

@Composable
private fun SettingsModeSelector(
    corporateMode: Boolean,
    onCorporateModeChanged: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        listOf(false to "Private", true to "Corporate").forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = corporateMode == value,
                        onClick = { onCorporateModeChanged(value) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = corporateMode == value, onClick = null)
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
