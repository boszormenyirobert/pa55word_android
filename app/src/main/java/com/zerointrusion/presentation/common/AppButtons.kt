package com.zerointrusion.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppButtonSize {
    Small,
    Compact,
    Large,
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    size: AppButtonSize = AppButtonSize.Compact,
    contentAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    horizontalContentPadding: Dp = if (size == AppButtonSize.Large) 20.dp else 16.dp,
    disabledContentAlpha: Float = 0.38f,
    content: @Composable RowScope.() -> Unit,
) {
    val isSmall = size == AppButtonSize.Small
    val isLarge = size == AppButtonSize.Large
    OutlinedButton(
        onClick = onClick,
        modifier =
            if (isSmall) modifier.height(32.dp)
            else modifier.heightIn(min = if (isLarge) 80.dp else 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.6f else 0.2f)),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = color,
                disabledContentColor = color.copy(alpha = disabledContentAlpha),
            ),
        contentPadding =
            PaddingValues(
                horizontal = horizontalContentPadding,
                vertical = if (isSmall) 4.dp else if (isLarge) 18.dp else 10.dp,
            ),
    ) {
        Row(
            modifier =
                if (contentAlignment == Alignment.Start) Modifier.fillMaxWidth() else Modifier,
            horizontalArrangement = Arrangement.aligned(contentAlignment),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(if (isSmall) 16.dp else if (isLarge) 26.dp else 20.dp),
            )
            Spacer(modifier = Modifier.width(if (isSmall) 6.dp else if (isLarge) 16.dp else 10.dp))
            ProvideTextStyle(
                if (isSmall) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.titleMedium
            ) {
                content()
            }
        }
    }
}

@Composable
fun AppMenuButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    AppButton(
        onClick = onClick,
        icon = icon,
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Start,
        horizontalContentPadding = 30.dp,
    ) {
        Text(text)
    }
}

@Composable
fun AppBottomActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    IconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clip(shape),
        enabled = enabled,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = color,
                disabledContentColor = color.copy(alpha = 0.38f),
            ),
        content = content,
    )
}
