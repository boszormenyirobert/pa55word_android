package com.zerointrusion.presentation.deviceinformation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppScreenHeader
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.LightDark

@Composable
fun DeviceInformationScreen(
    model: DeviceInformationUiModel,
    namespaces: List<NamespaceEntry>,
    errorMessage: String?,
    onNamespaceSelected: (NamespaceEntry) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(brush = Brush.verticalGradient(colors = listOf(Dark, LightDark, Dark)))
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader("Device information")

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            DeviceInformationContent(
                model = model,
                namespaces = namespaces,
                errorMessage = errorMessage,
                onNamespaceSelected = onNamespaceSelected,
            )
        }

        AppButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack) { Text("Back") }
    }
}
