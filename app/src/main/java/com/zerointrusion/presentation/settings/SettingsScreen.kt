package com.zerointrusion.presentation.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
fun SettingsScreen(
    onOpenAccounts: () -> Unit,
    onOpenRecoveryConnection: () -> Unit,
    onOpenDevices: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(brush = Brush.verticalGradient(colors = listOf(Dark, LightDark, Dark)))
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "Settings", style = MaterialTheme.typography.headlineMedium, color = White)

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = LightDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppButton(
                        icon = Icons.Default.Storage,
                        onClick = onOpenAccounts,
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Start,
                        horizontalContentPadding = 30.dp,
                    ) {
                        Text("Accounts - Servers")
                    }

                    AppButton(
                        icon = Icons.Default.Devices,
                        onClick = onOpenDevices,
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Start,
                        horizontalContentPadding = 30.dp,
                    ) {
                        Text("Devices")
                    }

                    AppButton(
                        icon = Icons.Default.Restore,
                        onClick = onOpenRecoveryConnection,
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Start,
                        horizontalContentPadding = 30.dp,
                    ) {
                        Text("Recovery connection")
                    }
                }
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
