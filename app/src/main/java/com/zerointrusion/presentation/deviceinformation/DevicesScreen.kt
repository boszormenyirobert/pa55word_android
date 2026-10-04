package com.zerointrusion.presentation.deviceinformation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import com.zerointrusion.ui.theme.Yellow

@Composable
fun DevicesScreen(
    onOpenDeviceInformation: () -> Unit,
    onOpenRemoveDevices: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Dark, LightDark, Dark)))
            .verticalScroll(rememberScrollState())
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Devices",
            style = MaterialTheme.typography.headlineMedium,
            color = White,
        )

        Button(
            onClick = onOpenDeviceInformation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Device information")
        }

        Button(
            onClick = onOpenRemoveDevices,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Remove devices")
        }

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