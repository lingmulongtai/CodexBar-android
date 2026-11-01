package com.codexbar.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

/** Transparent state tints sit over the neutral glass, retaining their original lightness. */
internal fun glassPanelTint(color: Color, base: Color, dark: Boolean): Color =
    color.compositeOver(base).copy(alpha = if (dark) 0.74f else 0.72f)

/** Passive glass grouping, with no spurious click action on charts or settings sections. */
@Composable
fun CodexBarPanel(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large,
    color: Color = LocalCodexBarThemeProfile.current.cardColor, border: BorderStroke? = null,
    contentColor: Color = contentColorFor(color), tonalElevation: Dp = 0.dp,
    content: @Composable () -> Unit) {
    val profile = LocalCodexBarThemeProfile.current
    if (profile.style != AppThemeStyle.LIQUID_GLASS) {
        Surface(modifier, shape = shape, color = color, border = border, contentColor = contentColor, tonalElevation = tonalElevation, content = content)
        return
    }
    GlassSurface(LocalGlassCardBackdrop.current, shape, glassPanelTint(color, profile.cardColor, profile.isDark), color.compositeOver(profile.cardColor),
        Color.White.copy(alpha = if (profile.isDark) 0.18f else 0.70f), modifier.then(if (border != null) Modifier.border(border, shape) else Modifier),
        blurRadius = 16.dp, refraction = 0.18f, elevation = 2.dp) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

@Composable
fun CodexBarCard(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large,
    colors: CardColors = CardDefaults.cardColors(), elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) {
    if (LocalCodexBarThemeProfile.current.style != AppThemeStyle.LIQUID_GLASS) {
        Card(modifier, shape, colors, elevation, border, content)
    } else CodexBarPanel(modifier, shape, color = colors.containerColor, border = border, contentColor = colors.contentColor) { Column(content = content) }
}

@Composable
fun CodexBarCard(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large, colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(), border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit) {
    if (LocalCodexBarThemeProfile.current.style != AppThemeStyle.LIQUID_GLASS) {
        Card(onClick, modifier, enabled, shape, colors, elevation, border, content = content)
    } else CodexBarPanel(modifier, shape, color = if (enabled) colors.containerColor else colors.disabledContainerColor, border = border, contentColor = if (enabled) colors.contentColor else colors.disabledContentColor) {
        Surface(onClick, enabled = enabled, shape = shape, color = Color.Transparent, border = border) {
            Column(content = content)
        }
    }
}

@Composable
fun CodexBarButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = CircleShape, colors: ButtonColors = ButtonDefaults.buttonColors(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, content: @Composable RowScope.() -> Unit) {
    if (LocalCodexBarThemeProfile.current.style != AppThemeStyle.LIQUID_GLASS) {
        Button(onClick, modifier, enabled, shape, colors, border = border, contentPadding = contentPadding, content = content)
    } else GlassAction(onClick, modifier, enabled, shape,
        if (enabled) colors.containerColor else colors.disabledContainerColor,
        if (enabled) colors.contentColor else colors.disabledContentColor, border, contentPadding, content = content)
}

@Composable
fun CodexBarOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = CircleShape, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, content: @Composable RowScope.() -> Unit) {
    if (LocalCodexBarThemeProfile.current.style != AppThemeStyle.LIQUID_GLASS) {
        OutlinedButton(onClick, modifier, enabled, shape, colors, border = border ?: ButtonDefaults.outlinedButtonBorder(enabled), contentPadding = contentPadding, content = content)
    } else GlassAction(onClick, modifier, enabled, shape, MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        if (enabled) colors.contentColor else colors.disabledContentColor, border, contentPadding, content = content)
}

@Composable
fun CodexBarTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), content: @Composable RowScope.() -> Unit) {
    if (LocalCodexBarThemeProfile.current.style != AppThemeStyle.LIQUID_GLASS) {
        TextButton(onClick, modifier, enabled, colors = colors, content = content)
    } else GlassAction(onClick, modifier, enabled, CircleShape, MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
        if (enabled) colors.contentColor else colors.disabledContentColor, null, PaddingValues(horizontal = 12.dp, vertical = 10.dp), content = content)
}

@Composable
private fun GlassAction(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, shape: Shape,
    tint: Color, foreground: Color, border: BorderStroke?, padding: PaddingValues, selectedState: Boolean? = null, content: @Composable RowScope.() -> Unit) {
    val profile = LocalCodexBarThemeProfile.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    var width by remember { mutableIntStateOf(1) }
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) spring(dampingRatio = 0.60f, stiffness = 515f)
            else spring(dampingRatio = 0.37f, stiffness = 250f),
        label = "glass_action_press"
    )
    BoxWithConstraints(modifier.defaultMinSize(minHeight = 48.dp)
        .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
        .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .semantics { if (selectedState != null) selected = selectedState }, contentAlignment = Alignment.Center) {
        val innerWidth = if (constraints.hasFixedWidth) Modifier.fillMaxWidth() else Modifier
        Box(innerWidth, contentAlignment = Alignment.Center) {
            GlassSurface(LocalGlassCardBackdrop.current, shape, tint, tint.copy(alpha = 1f),
                if (profile.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f),
                modifier = Modifier.matchParentSize().onSizeChanged { width = it.width }.graphicsLayer {
                    scaleX = 1f + lift * (15.dp.toPx() / width.coerceAtLeast(1))
                    alpha = if (enabled) 1f else 0.35f
                }.then(if (border != null) Modifier.border(border, shape) else Modifier),
                blurRadius = 3.dp, elevation = if (profile.isDark) 0.dp else 1.dp, refraction = 0f) {}
            CompositionLocalProvider(LocalContentColor provides foreground) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    Row(Modifier.defaultMinSize(minHeight = 40.dp).padding(padding),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
                }
            }
        }
    }
}

@Composable
fun CodexBarFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalCodexBarThemeProfile.current.style != AppThemeStyle.LIQUID_GLASS) {
        FilterChip(selected, onClick, label, modifier, enabled)
    } else GlassAction(onClick, modifier, enabled, CircleShape, MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
        PaddingValues(horizontal = 14.dp, vertical = 8.dp), selectedState = selected) { label() }
}

/** Used for language and provider filters; each choice keeps its own accessible tab action. */
@Composable
fun GlassChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlassSegmentedControl(labels, selected, onSelect, modifier)
}

@Composable
fun CodexBarIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(), content: @Composable () -> Unit) {
    if (LocalCodexBarThemeProfile.current.style != AppThemeStyle.LIQUID_GLASS) {
        IconButton(onClick, modifier, enabled, colors, content = content)
    } else GlassAction(onClick, modifier.size(48.dp), enabled, CircleShape,
        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        if (enabled) colors.contentColor else colors.disabledContentColor, null, PaddingValues(12.dp)) { content() }
}
