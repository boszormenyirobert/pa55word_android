package com.zerointrusion.presentation.registration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppButtonSize
import com.zerointrusion.presentation.common.AppScreenHeader

@Composable
fun RegistrationOptionsScreen(
    onNewAccount: () -> Unit,
    onAddMobile: () -> Unit,
    onRecovery: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black)
                .testTag("registration-options")
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppScreenHeader("Registration")
        Column(
            modifier =
                Modifier.weight(1f)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            RegistrationOptionButton(
                "New account registration",
                Icons.Default.PersonAdd,
                onNewAccount,
            )
            RegistrationOptionButton(
                "Add new mobile to existing account",
                Icons.Default.PhoneAndroid,
                onAddMobile,
            )
            RegistrationOptionButton("Recovery", Icons.Default.Restore, onRecovery)
        }
    }
}

@Composable
private fun RegistrationOptionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    AppButton(
        onClick = onClick,
        icon = icon,
        modifier = Modifier.fillMaxWidth(),
        size = AppButtonSize.Large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
