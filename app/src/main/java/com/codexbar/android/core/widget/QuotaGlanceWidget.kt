package com.codexbar.android.core.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.core.content.ContextCompat
import com.codexbar.android.MainActivity
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaSeverity
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

class QuotaGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 90.dp),
            DpSize(250.dp, 120.dp),
            DpSize(320.dp, 180.dp),
            DpSize(420.dp, 260.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetPrefs = WidgetPrefsManager(context)
        val prefsManager = EncryptedPrefsManager(context)
        prefsManager.warmCache()
        val privacySettings = prefsManager.getPrivacySettings()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = widgetPrefs.getWidgetConfig(appWidgetId)
        val strings = WidgetStrings(ContextCompat.getContextForLanguage(context))

        provideContent {
            GlanceTheme {
                WidgetContent(
                    config = config,
                    widgetPrefs = widgetPrefs,
                    redactQuotaDetails = privacySettings.widgetRedactionEnabled,
                    strings = strings
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        config: WidgetDisplayConfig,
        widgetPrefs: WidgetPrefsManager,
        redactQuotaDetails: Boolean,
        strings: WidgetStrings
    ) {
        val size = LocalSize.current
        val selectedServices = config.services
        val maxServices = when {
            size.height < 110.dp -> 1
            size.height < 180.dp -> 2
            else -> selectedServices.size
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(ColorProvider(Color(0xB01C1B1F)))
                .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp)
        ) {
            if (redactQuotaDetails) {
                RedactedState(strings)
            } else if (selectedServices.isEmpty()) {
                EmptyState(strings)
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    for ((index, service) in selectedServices.take(maxServices).withIndex()) {
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Divider()
                            Spacer(modifier = GlanceModifier.height(8.dp))
                        }
                        ServiceSection(
                            service = service,
                            widgetPrefs = widgetPrefs,
                            config = config,
                            showRefresh = index == 0,
                            strings = strings
                        )
                    }
                    if (selectedServices.size > maxServices) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = strings.moreServices(selectedServices.size - maxServices),
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.45f)),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyState(strings: WidgetStrings) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.noServices,
                style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.6f)), fontSize = 14.sp)
            )
        }
    }

    @Composable
    private fun RedactedState(strings: WidgetStrings) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = strings.quotaHidden,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = strings.openDetails,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.55f)),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun Divider() {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorProvider(Color.White.copy(alpha = 0.1f)))
        ) {}
    }

    @Composable
    private fun ServiceSection(
        service: AiService,
        widgetPrefs: WidgetPrefsManager,
        config: WidgetDisplayConfig,
        showRefresh: Boolean,
        strings: WidgetStrings
    ) {
        val labels = widgetPrefs.getCachedLabels(service).take(config.maxRows)
        val tier = widgetPrefs.getCachedTier(service)
        val freshness = widgetPrefs.getCachedFreshness(service)
        val statusMessage = widgetPrefs.getCachedStatusMessage(service)

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // Header: service name + tier + refresh button
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Service icon dot
                Box(
                    modifier = GlanceModifier
                        .size(10.dp)
                        .cornerRadius(5.dp)
                        .background(ColorProvider(Color(service.brandColor)))
                ) {}
                Spacer(modifier = GlanceModifier.width(8.dp))

                Text(
                    text = service.displayName,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                if (tier != null) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(4.dp)
                            .background(ColorProvider(Color.White.copy(alpha = 0.15f)))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tier,
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                if (showRefresh) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = strings.refreshDescription,
                        modifier = GlanceModifier
                            .size(18.dp)
                            .clickable(actionRunCallback<RefreshWidgetAction>()),
                        colorFilter = ColorFilter.tint(ColorProvider(Color.White.copy(alpha = 0.5f)))
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (config.showFreshness && freshness != null) {
                Text(
                    text = strings.updated(freshness),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.45f)),
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
            }

            // Each usage window — same layout as app dashboard
            for ((index, label) in labels.withIndex()) {
                if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                WindowRow(service, label, widgetPrefs, config)
            }

            // Show placeholder if no cached data yet
            if (labels.isEmpty()) {
                Text(
                    text = statusMessage ?: strings.waitingForData,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.4f)),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun WindowRow(
        service: AiService,
        label: String,
        widgetPrefs: WidgetPrefsManager,
        config: WidgetDisplayConfig
    ) {
        val utilization = widgetPrefs.getCachedUtilization(service, label)
        val barProgress = widgetPrefs.getCachedBarProgress(service, label)
        val remainingLabel = widgetPrefs.getCachedRemainingLabel(service, label)
        val resetText = widgetPrefs.getCachedResetLabel(service, label).orEmpty()
        val paceText = widgetPrefs.getCachedPaceLabel(service, label).orEmpty()
        val severity = widgetPrefs.getCachedSeverity(service, label)
            ?.let { runCatching { QuotaSeverity.valueOf(it) }.getOrNull() }
            ?: severityForUtilization(utilization)

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // Label + percentage
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = remainingLabel,
                    style = TextStyle(
                        color = severityColor(severity),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Progress bar
            SegmentedProgressBar(barProgress, severity)

            // Reset time
            val detailText = listOf(
                resetText.takeIf { config.showReset },
                paceText.takeIf { config.showPace }
            ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
            if (detailText.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = detailText,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.4f)),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun SegmentedProgressBar(barProgress: Float, severity: QuotaSeverity) {
        val totalSegments = 24
        val filledSegments = (barProgress * totalSegments).roundToInt().coerceIn(0, totalSegments)
        val fillColor = severityColor(severity)
        val trackColor = ColorProvider(Color.White.copy(alpha = 0.1f))

        Row(
            modifier = GlanceModifier.fillMaxWidth().height(4.dp)
        ) {
            for (i in 0 until totalSegments) {
                val color = if (i < filledSegments) fillColor else trackColor
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(4.dp)
                        .background(color)
                ) {}
                if (i < totalSegments - 1) {
                    Spacer(modifier = GlanceModifier.width(1.dp))
                }
            }
        }
    }

    companion object {
        fun utilizationColor(utilization: Float): ColorProvider {
            return severityColor(severityForUtilization(utilization))
        }

        fun severityForUtilization(utilization: Float): QuotaSeverity {
            return when {
                utilization >= 0.85f -> QuotaSeverity.Critical
                utilization >= 0.60f -> QuotaSeverity.Warning
                else -> QuotaSeverity.Good
            }
        }

        fun severityColor(severity: QuotaSeverity): ColorProvider {
            val color = when {
                severity == QuotaSeverity.Critical -> Color(0xFFEF5350)
                severity == QuotaSeverity.Warning -> Color(0xFFFFB74D)
                severity == QuotaSeverity.Redacted -> Color.White.copy(alpha = 0.35f)
                severity == QuotaSeverity.Unknown -> Color.White.copy(alpha = 0.45f)
                else -> Color(0xFF81C784)
            }
            return ColorProvider(color)
        }

        fun formatResetTime(epochSecond: Long): String {
            val now = Instant.now()
            val resetAt = Instant.ofEpochSecond(epochSecond)
            if (resetAt.isBefore(now)) return ""
            val duration = Duration.between(now, resetAt)
            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60
            return when {
                hours >= 24 -> "${hours / 24}d ${hours % 24}h"
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        }
    }
}

private class WidgetStrings(private val context: Context) {
    val noServices: String = context.getString(R.string.widget_no_services)
    val quotaHidden: String = context.getString(R.string.widget_quota_hidden)
    val openDetails: String = context.getString(R.string.widget_open_details)
    val waitingForData: String = context.getString(R.string.widget_waiting_for_data)
    val refreshDescription: String = context.getString(R.string.widget_refresh_description)

    fun moreServices(count: Int): String = context.getString(R.string.widget_more_services, count)

    fun updated(freshness: String): String = context.getString(R.string.widget_updated, freshness)
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WorkManagerInitializer.enqueueManualQuotaRefresh(context, source = "widget")
        QuotaGlanceWidget().update(context, glanceId)
    }
}
