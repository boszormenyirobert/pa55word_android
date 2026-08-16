package com.zerointrusion.presentation.deviceinformation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import com.zerointrusion.ui.theme.Yellow

@Composable
fun DeviceInformationScreen(
    model: DeviceInformationUiModel,
    namespaces: List<NamespaceEntry>,
    errorMessage: String?,
    onNamespaceSelected: (NamespaceEntry) -> Unit,
    onBack: () -> Unit,
) {
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Device information",
            style = MaterialTheme.typography.headlineMedium,
            color = White,
        )

        DeviceInformationContent(
            model = model,
            namespaces = namespaces,
            errorMessage = errorMessage,
            onNamespaceSelected = onNamespaceSelected,
        )

        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(2.dp, Yellow),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Back", color = White)
        }
    }
}