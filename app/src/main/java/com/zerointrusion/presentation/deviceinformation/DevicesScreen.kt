package com.zerointrusion.presentation.deviceinformation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import com.zerointrusion.presentation.common.AppMenuButton
import com.zerointrusion.presentation.common.AppSettingsPage

@Composable
fun DevicesScreen(
    onOpenDeviceInformation: () -> Unit,
    onOpenRemoveDevices: () -> Unit,
    onBack: () -> Unit,
) {
    AppSettingsPage(title = "Devices", onBack = onBack) {
        AppMenuButton("Device information", Icons.Default.Info, onOpenDeviceInformation)
        AppMenuButton("Remove devices", Icons.Default.Delete, onOpenRemoveDevices)
    }
}
