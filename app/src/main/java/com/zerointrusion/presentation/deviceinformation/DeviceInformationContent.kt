package com.zerointrusion.presentation.deviceinformation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

@Composable
internal fun DeviceInformationContent(model: DeviceInformationUiModel) {
    Text(
        text = "Account ID: ${model.accountId.ifBlank { "(empty)" }}",
        style = MaterialTheme.typography.bodyMedium,
        color = White,
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DeviceInformationRow(label = "Device ID", value = model.deviceId)
            DeviceInformationRow(label = "Namespace", value = model.namespaceName)
            DeviceInformationRow(label = "Email", value = model.email)
        }
    }
}

@Composable
private fun DeviceInformationRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = White, style = MaterialTheme.typography.titleSmall)
        Text(text = value.ifBlank { "(empty)" }, color = White, style = MaterialTheme.typography.bodyMedium)
    }
}