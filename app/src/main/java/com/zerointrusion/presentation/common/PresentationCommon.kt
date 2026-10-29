package com.zerointrusion.presentation.common

import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.SVG
import com.zerointrusion.R
import com.zerointrusion.ui.theme.Dark
import com.zerointrusion.ui.theme.LightDark
import com.zerointrusion.ui.theme.White

@Composable
fun AppSettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    secondaryAction: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Dark, LightDark, Dark)))
                .then(modifier)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(title)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
        AppBottomActions {
            AppButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                enabled = backEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text("Back")
            }
            if (secondaryAction == null) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                secondaryAction()
            }
        }
    }
}

@Composable
fun AppScreenHeader(
    title: String = "",
    modifier: Modifier = Modifier,
    logoModifier: Modifier = Modifier,
    logoWidth: Dp = 120.dp,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableLogoWidth =
            if (actions != null) logoWidth.coerceAtMost((maxWidth - 160.dp).coerceAtLeast(0.dp))
            else logoWidth
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(width = availableLogoWidth, height = 72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Pa55wordLogo(
                    modifier =
                        Modifier.size(
                                width = availableLogoWidth,
                                height = availableLogoWidth * 0.24f,
                            )
                            .then(logoModifier)
                )
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp).wrapContentHeight(),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                        ),
                    color = White,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
fun AppSettingsPanel(cornerRadius: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = LightDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun Pa55wordLogo(modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center) {
    val context = LocalContext.current
    val painter =
        remember(context) { SvgLogoPainter(SVG.getFromResource(context, R.raw.pa55word_logo)) }
    Image(
        painter = painter,
        contentDescription = "pa55word logo",
        contentScale = ContentScale.Fit,
        alignment = alignment,
        modifier = modifier,
    )
}

private class SvgLogoPainter(private val svg: SVG) : Painter() {
    override val intrinsicSize = Size(svg.documentWidth, svg.documentHeight)

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val checkpoint = nativeCanvas.save()
            try {
                nativeCanvas.scale(
                    size.width / intrinsicSize.width,
                    size.height / intrinsicSize.height,
                )
                svg.renderToCanvas(
                    nativeCanvas,
                    RectF(0f, 0f, intrinsicSize.width, intrinsicSize.height),
                )
            } finally {
                nativeCanvas.restoreToCount(checkpoint)
            }
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    AppBackground {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppScreenHeader("Loading")
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        PillLabel(text = "Please wait")
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(text = message, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text =
                                "The app is working locally with keystore material or waiting for the backend response.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppBackground(content: @Composable ColumnScope.() -> Unit) {
    val gradient =
        Brush.verticalGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    MaterialTheme.colorScheme.background,
                )
        )

    Column(modifier = Modifier.fillMaxSize().background(gradient), content = content)
}

@Composable
fun HeroPanel(eyebrow: String, title: String, body: String) {
    Surface(shape = RoundedCornerShape(32.dp), color = Color.Transparent) {
        Column(
            modifier =
                Modifier.clip(RoundedCornerShape(32.dp))
                    .background(
                        brush =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
                                    )
                            )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(32.dp),
                    )
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillLabel(text = eyebrow)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
fun PillLabel(text: String) {
    Box(
        modifier =
            Modifier.clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun RegistrationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String,
    isError: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = isError,
        supportingText = { Text(supporting) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.32f),
                errorContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
            ),
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppButton(onClick = onClick, icon = icon, modifier = modifier, enabled = enabled) { Text(text) }
}

enum class BannerTone {
    Error,
    Warning,
    Info,
    Success,
}

@Composable
fun StatusBanner(title: String, message: String, tone: BannerTone) {
    val container =
        when (tone) {
            BannerTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            BannerTone.Warning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
            BannerTone.Info -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            BannerTone.Success -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
        }
    val border =
        when (tone) {
            BannerTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
            BannerTone.Warning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            BannerTone.Info -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            BannerTone.Success -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(container)
                .border(1.dp, border, RoundedCornerShape(20.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ChecklistLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier =
                Modifier.padding(top = 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .size(8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
