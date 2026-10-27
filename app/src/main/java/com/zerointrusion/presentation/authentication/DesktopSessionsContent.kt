package com.zerointrusion.presentation.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.presentation.app.DesktopSessionsUiState
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.White

@Composable
internal fun DesktopSessionsContent(
    state: DesktopSessionsUiState,
    onRetry: () -> Unit,
    onRevokeSession: (ActiveDesktopSession) -> Unit,
) {
    when (state) {
        DesktopSessionsUiState.Idle -> Text(
            text = "No data loaded yet.",
            color = White,
        )

        DesktopSessionsUiState.InProgress -> {
            CircularProgressIndicator()
            Text(
                text = "Loading desktop sessions...",
                color = White,
            )
        }

        is DesktopSessionsUiState.Success -> {
            if (!state.message.isNullOrBlank()) {
                Text(
                    text = state.message,
                    color = ErrorTextLight,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.sessions.isEmpty()) {
                Text(
                    text = "No active desktop sessions found.",
                    color = White,
                )
            } else {
                state.sessions.forEach { session ->
                    DesktopSessionCard(
                        session = session,
                        isRevoking = state.revokingAttestationId == session.attestationId,
                        onRevokeSession = onRevokeSession,
                    )
                }
            }
        }

        is DesktopSessionsUiState.Error -> {
            Text(
                text = state.message,
                color = ErrorTextLight,
                style = MaterialTheme.typography.bodyMedium,
            )
            AppButton(icon = Icons.Default.Refresh, onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun DesktopSessionCard(
    session: ActiveDesktopSession,
    isRevoking: Boolean,
    onRevokeSession: (ActiveDesktopSession) -> Unit,
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Dark.copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SessionField(label = "Namespace", value = session.namespaceName)
            SessionField(label = "Desktop device ID", value = session.deviceId)
            SessionField(label = "Attestation ID", value = session.attestationId)
            SessionField(label = "Issued at", value = session.issuedAt.toDisplayTimestamp())
            SessionField(label = "Expires at", value = session.expiresAt.toDisplayTimestamp())
            AppButton(
                icon = Icons.Default.LinkOff,
                onClick = { onRevokeSession(session) },
                enabled = !isRevoking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isRevoking) "Revoking..." else "Revoke session")
            }
        }
    }
}

@Composable
private fun SessionField(
    label: String,
    value: String,
) {
    Text(
        text = "$label: $value",
        color = White,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
    )
}

private fun String.toDisplayTimestamp(): String =
    removeSuffix("Z").replace('T', ' ') + if (endsWith("Z")) " UTC" else ""