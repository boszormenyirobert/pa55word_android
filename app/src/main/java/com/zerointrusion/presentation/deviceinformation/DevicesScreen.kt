package com.zerointrusion.presentation.deviceinformation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.common.AppBottomActions
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

@Composable
fun DevicesScreen(
    onOpenDeviceInformation: () -> Unit,
    onOpenRemoveDevices: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Dark, LightDark, Dark)))
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "Devices", style = MaterialTheme.typography.headlineMedium, color = White)

            AppButton(
                icon = Icons.Default.Info,
                onClick = onOpenDeviceInformation,
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Start,
                horizontalContentPadding = 30.dp,
            ) {
                Text("Device information")
            }

            AppButton(
                icon = Icons.Default.Delete,
                onClick = onOpenRemoveDevices,
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Start,
                horizontalContentPadding = 30.dp,
            ) {
                Text("Remove devices")
            }
        }

        AppBottomActions {
            AppButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text("Back")
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
