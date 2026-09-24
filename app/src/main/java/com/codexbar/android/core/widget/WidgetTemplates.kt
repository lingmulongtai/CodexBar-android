package com.codexbar.android.core.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** All ten templates share the same cached, privacy-filtered values and remain native text. */
@Composable
internal fun WidgetTemplates(config: WidgetDisplayConfig, providers: List<WidgetProvider>) {
    val size = LocalSize.current
    val style = config.style.normalized()
    val rows = style.template in listOf(WidgetTemplate.LEDGER, WidgetTemplate.METERS,
        WidgetTemplate.DUAL, WidgetTemplate.RESET) || size.width.value < 270
    val count = if (rows) WidgetRenderPolicy.rowCount(size.height.value, style.fontScale) else 3
    val data = providers.take(count)
    if (rows) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            data.forEach { provider ->
                ProviderLine(provider, config,
                    ((size.height.value - 8) / data.size).coerceIn(18f, 48f))
            }
        }
    } else if (style.template == WidgetTemplate.FOCUS) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            data.firstOrNull()?.let {
                Column(GlanceModifier.defaultWeight().padding(end = 8.dp)) {
                    ProviderTile(it, config, focus = true)
                }
            }
            if (data.size > 1) {
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    data.drop(1).forEach { provider ->
                        WText(provider.name + if (provider.needsAttention) " !" else "", style, 10f,
                            bold = true, color = style.accent(provider.service))
                        WText(provider.primary?.let { "${it.percent}  ${resetText(it, config)}" }
                            ?: provider.message, style, 11f)
                    }
                }
            }
        }
    } else {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
            data.forEachIndexed { index, provider ->
                if (index > 0) Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight()) { ProviderTile(provider, config) }
            }
        }
    }
}

@Composable
private fun ProviderLine(provider: WidgetProvider, config: WidgetDisplayConfig, height: Float) {
    val style = config.style
    val metric = provider.primary
    val accent = style.accent(provider.service)
    val compact = LocalSize.current.width.value < 270
    Row(GlanceModifier.fillMaxWidth().height(height.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(2.dp).height(11.dp).background(Color(accent))) {}
        Spacer(GlanceModifier.width(5.dp))
        WText(provider.name + if (provider.needsAttention) " !" else "", style, 10f, bold = true,
            modifier = GlanceModifier.width(if (compact) 54.dp else 60.dp))
        if (metric == null) {
            WText(provider.message, style, 10f, modifier = GlanceModifier.defaultWeight())
        } else when (if (compact) WidgetTemplate.LEDGER else style.template) {
            WidgetTemplate.METERS -> {
                WText(metric.percent, style, 12f, bold = true, modifier = GlanceModifier.width(36.dp))
                Track(metric.remaining, accent, GlanceModifier.defaultWeight().height(4.dp))
                Spacer(GlanceModifier.width(8.dp))
                WText(resetText(metric, config), style, 9f, modifier = GlanceModifier.width(60.dp))
                if (!compact && style.showSecondary) WText(secondaryText(provider, config), style, 9f,
                    modifier = GlanceModifier.width(85.dp))
            }
            WidgetTemplate.DUAL -> {
                listOfNotNull(metric, provider.secondary.takeIf { style.showSecondary }).forEach { item ->
                    Column(GlanceModifier.defaultWeight().padding(horizontal = 4.dp)) {
                        WText("${item.shortLabel} ${item.percent} ${resetText(item, config)}", style, 9f)
                        Track(item.remaining, accent, GlanceModifier.fillMaxWidth().height(2.dp))
                    }
                }
            }
            WidgetTemplate.RESET -> {
                WText(resetText(metric, config), style, 12f, bold = true,
                    modifier = GlanceModifier.width(if (compact) 58.dp else 76.dp))
                WText("${metric.shortLabel}  ${metric.percent}", style, 10f,
                    modifier = GlanceModifier.defaultWeight())
                if (!compact && style.showSecondary) WText(secondaryText(provider, config), style, 9f)
            }
            else -> {
                WText(metric.percent, style, 12f, bold = true, color = accent,
                    modifier = GlanceModifier.width(38.dp))
                WText("${metric.shortLabel} ${resetText(metric, config)}", style, 9f,
                    modifier = GlanceModifier.defaultWeight())
                if (!compact && style.showSecondary) WText(secondaryText(provider, config), style, 9f,
                    modifier = GlanceModifier.defaultWeight())
                if (!compact && config.showFreshness) WText(provider.age ?: "—", style, 8f,
                    modifier = GlanceModifier.width(22.dp))
            }
        }
    }
}

@Composable
private fun ProviderTile(provider: WidgetProvider, config: WidgetDisplayConfig, focus: Boolean = false) {
    val style = config.style
    val metric = provider.primary
    val accent = style.accent(provider.service)
    val tile = style.template == WidgetTemplate.TILES
    val modifier = if (tile) GlanceModifier.fillMaxWidth().cornerRadius(10.dp)
        .background(Color(style.foregroundArgb).copy(alpha = 0.07f)).padding(horizontal = 5.dp)
        else GlanceModifier.fillMaxWidth()
    Column(modifier) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WText(provider.name + if (provider.needsAttention) " !" else "", style, 10f, bold = true,
                color = accent, modifier = GlanceModifier.defaultWeight())
            if (config.showFreshness) WText(provider.age ?: "—", style, 8f)
        }
        if (metric == null) {
            WText(provider.message, style, 10f)
        } else {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (style.template == WidgetTemplate.RINGS) {
                    val bitmap = remember(metric.remaining, accent) { ringBitmap(metric.remaining, accent) }
                    Image(ImageProvider(bitmap), null, GlanceModifier.size(24.dp))
                    Spacer(GlanceModifier.width(4.dp))
                }
                if (style.template == WidgetTemplate.VERTICAL) {
                    Box(GlanceModifier.width(7.dp).height(23.dp).background(Color(accent).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.BottomCenter) {
                        metric.remaining?.let {
                            Box(GlanceModifier.width(7.dp).height((23 * it.coerceIn(0f, 1f)).dp).background(Color(accent))) {}
                        }
                    }
                    Spacer(GlanceModifier.width(6.dp))
                }
                WText(metric.percent, style, if (focus) {
                    if (LocalSize.current.height.value < 80) 18f else 23f
                } else 17f, bold = true)
                Spacer(GlanceModifier.width(4.dp))
                WText(metric.shortLabel, style, 8f)
            }
            if (style.template == WidgetTemplate.SEGMENTS) {
                Row(GlanceModifier.fillMaxWidth()) {
                    repeat(10) { index ->
                        Box(GlanceModifier.defaultWeight().padding(end = 1.dp).height(2.dp)) {
                            Box(GlanceModifier.fillMaxSize().background(Color(accent)
                                .copy(alpha = if (metric.remaining != null && index < metric.remaining * 10) 1f else 0.18f))) {}
                        }
                    }
                }
            } else if (style.template in listOf(WidgetTemplate.COLUMNS, WidgetTemplate.TILES)) {
                Track(metric.remaining, accent, GlanceModifier.fillMaxWidth().height(2.dp))
            }
            if (config.showReset) WText(resetText(metric, config), style, 9f)
            if (style.showSecondary && provider.secondary != null) WText(secondaryText(provider, config), style, 8f)
            if (config.showPace && LocalSize.current.height.value >= 100) metric.pace?.let { WText(it, style, 9f) }
            if (config.showReset && LocalSize.current.height.value >= 130) metric.resetAdvice?.let { WText(it, style, 9f) }
        }
    }
}

private fun resetText(metric: WidgetMetric?, config: WidgetDisplayConfig): String =
    if (config.showReset) metric?.reset?.let { "↻ $it" } ?: "↻ —" else ""

private fun secondaryText(provider: WidgetProvider, config: WidgetDisplayConfig): String =
    provider.secondary?.let { "${it.shortLabel} ${it.percent} ${resetText(it, config)}" }.orEmpty()

@Composable
private fun Track(remaining: Float?, accent: Int, modifier: GlanceModifier) {
    LinearProgressIndicator(remaining?.coerceIn(0f, 1f) ?: 0f, modifier,
        color = ColorProvider(Color(accent)), backgroundColor = ColorProvider(Color(accent).copy(alpha = 0.16f)))
}

@Composable
private fun WText(text: String, style: WidgetStyle, size: Float, bold: Boolean = false,
    color: Int = style.foregroundArgb, modifier: GlanceModifier = GlanceModifier) {
    Text(text, modifier, style = TextStyle(color = ColorProvider(Color(color)),
        fontSize = (size * style.fontScale).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
}

/** Only the decorative ring is a tiny bitmap; labels remain selectable accessibility text. */
private fun ringBitmap(remaining: Float?, color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
    paint.color = (color and 0xFFFFFF) or 0x33000000
    canvas.drawCircle(48f, 48f, 40f, paint)
    paint.color = color
    remaining?.let { canvas.drawArc(8f, 8f, 88f, 88f, -90f, 360 * it.coerceIn(0f, 1f), false, paint) }
    return bitmap
}
