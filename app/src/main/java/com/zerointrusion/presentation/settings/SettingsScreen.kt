package com.zerointrusion.presentation.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.registration.ServicePlan
import com.zerointrusion.presentation.common.AppButtonSize
import com.zerointrusion.presentation.common.AppMenuButton
import com.zerointrusion.presentation.common.AppSettingsPage
import com.zerointrusion.presentation.common.AppSettingsPanel

@Composable
fun SettingsScreen(
    onOpenAccounts: () -> Unit,
    onOpenRecoveryConnection: () -> Unit,
    onOpenDevices: () -> Unit,
    onBack: () -> Unit,
    onOpenServicePlan: () -> Unit = {},
    servicePlan: ServicePlan = ServicePlan.BASIC,
    errorCode: String? = null,
) {
    AppSettingsPage(title = "Settings", onBack = onBack) {
        if (errorCode != null) {
            Text(errorCode, color = MaterialTheme.colorScheme.error)
        }
        AppSettingsPanel(cornerRadius = 24.dp) {
            AppMenuButton(
                "Service plan",
                Icons.Default.WorkspacePremium,
                onOpenServicePlan,
                size = AppButtonSize.Large,
            )
            if (servicePlan == ServicePlan.ENTERPRISE) {
                AppMenuButton(
                    "Database connections",
                    Icons.Default.Storage,
                    onOpenAccounts,
                    size = AppButtonSize.Large,
                )
            }
            AppMenuButton(
                "Devices",
                Icons.Default.Devices,
                onOpenDevices,
                size = AppButtonSize.Large,
            )
            if (servicePlan == ServicePlan.ENTERPRISE) {
                AppMenuButton(
                    "Enterprise recovery",
                    Icons.Default.Restore,
                    onOpenRecoveryConnection,
                    size = AppButtonSize.Large,
                )
            }
        }
    }
}
