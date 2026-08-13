package com.zerointrusion.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.NamespaceEntry
import com.zerointrusion.presentation.NamespaceDraftUiState
import com.zerointrusion.presentation.NamespaceFetchUiState
import com.zerointrusion.presentation.NamespaceSaveUiState
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White
import com.zerointrusion.ui.theme.Yellow

@Composable
fun NamespacesScreen(
    namespaces: List<NamespaceEntry>,
    activeNamespaceId: String?,
    fetchState: NamespaceFetchUiState,
    onAddNewNamespace: () -> Unit,
    onNamespaceSelected: (NamespaceEntry) -> Unit,
    onCancel: () -> Unit,
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

        when (fetchState) {
            NamespaceFetchUiState.Idle -> Unit
            NamespaceFetchUiState.InProgress -> NamespaceLoadingCard()
            is NamespaceFetchUiState.Error -> NamespaceMessageCard(
                fetchState.message,
                isError = true,
            )
        }

        if (namespaces.isEmpty()) {
            NamespaceMessageCard("No namespaces stored yet.")
        } else {
            namespaces.forEach { namespace ->
                NamespaceCard(
                    namespace = namespace,
                    isActive = namespace.namespaceId == activeNamespaceId,
                    onClick = { onNamespaceSelected(namespace) },
                )
            }
        }

        Button(
            onClick = onAddNewNamespace,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add new namespace")
        }

        OutlinedButton(
            onClick = onCancel,
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
fun AddNamespaceScreen(
    draft: NamespaceDraftUiState,
    saveState: NamespaceSaveUiState,
    onNamespaceNameChanged: (String) -> Unit,
    onNamespaceUrlChanged: (String) -> Unit,
    onSave: () -> Unit,
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
            text = "Add new namespace",
            style = MaterialTheme.typography.headlineMedium,
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
                NamespaceTextField(
                    value = draft.namespaceName,
                    onValueChange = onNamespaceNameChanged,
                    label = "Namespace name",
                    supportingText = draft.namespaceNameError ?: "Only the new namespace name is required here.",
                    isError = draft.namespaceNameError != null,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                )
                NamespaceTextField(
                    value = draft.namespaceUrl,
                    onValueChange = onNamespaceUrlChanged,
                    label = "Namespace URL",
                    supportingText = draft.namespaceUrlError ?: "Base URL with port only, for example http://192.168.178.20:3000.",
                    isError = draft.namespaceUrlError != null,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                )
            }
        }

        when (saveState) {
            NamespaceSaveUiState.Idle -> Unit
            NamespaceSaveUiState.InProgress -> {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = LightDark),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("Saving namespace...", color = White)
                    }
                }
            }
            is NamespaceSaveUiState.Success -> NamespaceMessageCard(
                "Namespace ${saveState.namespaceName} saved successfully.",
            )
            is NamespaceSaveUiState.Error -> NamespaceMessageCard(
                saveState.message,
                isError = true,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onSave,
            enabled = saveState !is NamespaceSaveUiState.InProgress,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text("Save")
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

@Composable
private fun NamespaceCard(
    namespace: NamespaceEntry,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) Yellow else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = namespace.namespaceName.ifBlank { "(empty)" },
                style = MaterialTheme.typography.titleMedium,
                color = White,
            )
            if (isActive) {
                Text(text = "Active namespace", color = Yellow, style = MaterialTheme.typography.labelLarge)
            }
            Text(text = "Namespace ID: ${namespace.namespaceId}", color = White)
            Text(text = "Namespace URL: ${namespace.namespaceUrl}", color = White)
            Text(text = "Account ID: ${namespace.accountId}", color = White)
            Text(text = "Device ID: ${namespace.deviceId}", color = White)
        }
    }
}

@Composable
private fun NamespaceMessageCard(
    message: String,
    isError: Boolean = false,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            color = if (isError) ErrorTextLight else White,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun NamespaceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    isError: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        isError = isError,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun NamespaceLoadingCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator()
            Text("Loading namespaces...", color = White)
        }
    }
}