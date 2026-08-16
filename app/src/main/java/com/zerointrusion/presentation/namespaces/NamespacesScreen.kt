package com.zerointrusion.presentation.namespaces

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
fun NamespacesScreen(
    namespaces: List<NamespaceEntry>,
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
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Namespaces",
            style = MaterialTheme.typography.headlineMedium,
            color = White,
        )

        if (namespaces.isEmpty()) {
            NamespaceCard(
                title = "No namespaces stored yet.",
                body = null,
            )
        } else {
            namespaces.forEach { namespace ->
                NamespaceCard(
                    title = namespace.namespaceName,
                    body = listOf(
                        "Namespace ID: ${namespace.namespaceId}",
                        "Namespace URL: ${namespace.namespaceUrl}",
                        "Account ID: ${namespace.accountId}",
                        "Device ID: ${namespace.deviceId}",
                    ),
                )
            }
        }

        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(2.dp, Yellow),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text("Back to main view", color = White)
        }
    }
}

@Composable
private fun NamespaceCard(
    title: String,
    body: List<String>?,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = White,
            )
            body?.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = White,
                )
            }
        }
    }
}