package com.zerointrusion.presentation.connecteddevices

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.app.ConnectedDevicesUiState
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

@Composable
fun ConnectedDevicesScreen(
    accountId: String,
    state: ConnectedDevicesUiState,
    onRetry: () -> Unit,
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
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Connected devices",
            style = MaterialTheme.typography.headlineMedium,
            color = White,
        )
        Text(
            text = "Account ID: ${accountId.ifBlank { "(empty)" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = White,
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = LightDark),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConnectedDevicesContent(
                    state = state,
                    onRetry = onRetry,
                )
            }
        }

        AppButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text("Back")
        }
    }
}