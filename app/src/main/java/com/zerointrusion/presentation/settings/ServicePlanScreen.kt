package com.zerointrusion.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zerointrusion.domain.registration.ServicePlan
import com.zerointrusion.presentation.common.AppButton
import com.zerointrusion.presentation.common.AppButtonSize
import com.zerointrusion.presentation.common.AppSettingsPage
import com.zerointrusion.ui.theme.ErrorTextLight
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

@Composable
fun ServicePlanScreen(onOpenBasic: () -> Unit, onOpenEnterprise: () -> Unit, onBack: () -> Unit) {
    AppSettingsPage(title = "Service plan", onBack = onBack) {
        ServicePlan.entries.forEach { plan ->
            AppButton(
                onClick = if (plan == ServicePlan.BASIC) onOpenBasic else onOpenEnterprise,
                icon =
                    if (plan == ServicePlan.BASIC) Icons.Default.Person else Icons.Default.Business,
                modifier = Modifier.fillMaxWidth(),
                size = AppButtonSize.Large,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        plan.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ServicePlanDetailsScreen(
    plan: ServicePlan,
    selectedPlan: ServicePlan,
    onSelectPlan: (ServicePlan) -> Unit,
    onBack: () -> Unit,
    errorCode: String? = null,
) {
    val selected = selectedPlan == plan
    AppSettingsPage(title = "Service plan", onBack = onBack) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = LightDark, contentColor = White),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Dummy text.", style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = selected,
                                enabled = !selected,
                                role = Role.Checkbox,
                                onValueChange = { checked -> if (checked) onSelectPlan(plan) },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        enabled = !selected,
                        colors =
                            CheckboxDefaults.colors(
                                uncheckedColor = White.copy(alpha = 0.72f),
                                disabledCheckedColor = White.copy(alpha = 0.72f),
                                checkmarkColor = LightDark,
                            ),
                    )
                    Text(
                        text = if (selected) "Current plan" else "Select ${plan.title}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (errorCode != null) {
                    Text(errorCode, color = ErrorTextLight)
                }
            }
        }
    }
}

private val ServicePlan.title: String
    get() =
        when (this) {
            ServicePlan.BASIC -> "Basic"
            ServicePlan.ENTERPRISE -> "Enterprise"
        }
