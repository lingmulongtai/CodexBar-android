package com.codexbar.android.core.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
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
import com.codexbar.android.di.appSingletonEntryPointOrNull
import kotlinx.coroutines.withTimeoutOrNull

class QuotaGlanceWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {

    // Display state is in WidgetPrefsManager; no Glance DataStore is needed.
    override val stateDefinition = null

    // Use only the sizes the launcher actually requests, without a predefined variant matrix.
    override val sizeMode: SizeMode = SizeMode.Exact

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        Log.e(TAG, "Widget composition failed for id=$appWidgetId", throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    /**
     * Nothing here may throw or block indefinitely. The launcher keeps showing
     * `widget_loading` until [provideContent] returns a composition, so a failed or slow setup
     * read has to degrade into a rendered state instead of aborting the update.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val dependencies = WidgetDependencies.of(appContext)
        val appWidgetId = runCatching { GlanceAppWidgetManager(appContext).getAppWidgetId(id) }
            .getOrElse { error ->
                Log.w(TAG, "Could not resolve the App Widget ID for $id", error)
                AppWidgetManager.INVALID_APPWIDGET_ID
            }
        val config = runCatching { dependencies.widgetPrefs.getWidgetConfig(appWidgetId) }
            .getOrElse { error ->
                Log.e(TAG, "Could not read the configuration for id=$appWidgetId", error)
                WidgetDisplayConfig()
            }
        val redactQuotaDetails = dependencies.readWidgetRedaction()
        val strings = runCatching { WidgetStrings(ContextCompat.getContextForLanguage(context)) }
            .getOrElse { WidgetStrings(context) }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    config = config,
                    widgetPrefs = dependencies.widgetPrefs,
                    redactQuotaDetails = redactQuotaDetails,
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
        val heightDp = size.height.value.toInt()
        val compact = WidgetRenderPolicy.isCompact(size.width.value.toInt(), heightDp)
        val maxServices = WidgetRenderPolicy.maxServices(heightDp)
        val maxRows = WidgetRenderPolicy.maxRows(heightDp, config.maxRows)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = if (compact) 4.dp else 16.dp)
        ) {
            if (compact && (redactQuotaDetails || selectedServices.isEmpty())) {
                Text(
                    text = if (redactQuotaDetails) strings.quotaHidden else strings.noServices,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                    maxLines = 1
                )
            } else if (redactQuotaDetails) {
                RedactedState(strings)
            } else if (selectedServices.isEmpty()) {
                EmptyState(strings)
            } else if (compact) {
                Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    selectedServices.take(WidgetRenderPolicy.compactServices(heightDp)).forEach { service ->
                        CompactService(service, widgetPrefs, strings)
                    }
                }
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
                            config = config.copy(maxRows = maxRows),
                            showRefresh = index == 0,
                            strings = strings
                        )
                    }
                    if (selectedServices.size > maxServices) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = strings.moreServices(selectedServices.size - maxServices),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CompactService(service: AiService, prefs: WidgetPrefsManager, strings: WidgetStrings) {
        val label = prefs.getCachedLabels(service).maxByOrNull { prefs.getCachedUtilization(service, it) }
        val remaining = label?.let { prefs.getCachedRemainingLabel(service, it) }
            ?: prefs.getCachedStatusMessage(service)
            ?: strings.waitingForData
        val severity = label?.let { severityForUtilization(prefs.getCachedUtilization(service, it)) }
            ?: QuotaSeverity.Unknown
        Column(modifier = GlanceModifier.fillMaxWidth().height(24.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (service == AiService.COPILOT) "Copilot" else service.displayName,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = remaining,
                    style = TextStyle(color = severityColor(severity), fontSize = 12.sp),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            LinearProgressIndicator(
                progress = label?.let { prefs.getCachedBarProgress(service, it) }?.coerceIn(0f, 1f) ?: 0f,
                modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                color = severityColor(severity),
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )
        }
    }

    @Composable
    private fun EmptyState(strings: WidgetStrings) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = strings.noServices,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = strings.openDetails,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }
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
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = strings.openDetails,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
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
                .background(GlanceTheme.colors.outline)
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
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                if (tier != null) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(6.dp)
                            .background(GlanceTheme.colors.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tier,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSecondaryContainer,
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
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (config.showFreshness && freshness != null) {
                Text(
                    text = strings.updated(freshness),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp
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
                        color = GlanceTheme.colors.onSurfaceVariant,
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
        val resetPlanText = widgetPrefs.getCachedResetPlanLabel(service, label).orEmpty()
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
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = remainingLabel,
                    style = TextStyle(
                        color = severityColor(severity),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Progress bar
            QuotaProgressBar(barProgress, severity)

            // Reset time
            val detailText = listOf(
                resetText.takeIf { config.showReset },
                resetPlanText.ifBlank { paceText }.takeIf { config.showPace }
            ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
            if (detailText.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = detailText,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 2
                    )
                }
            }
        }
    }

    @Composable
    private fun QuotaProgressBar(barProgress: Float, severity: QuotaSeverity) {
        LinearProgressIndicator(
            progress = barProgress.coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = severityColor(severity),
            backgroundColor = GlanceTheme.colors.surfaceVariant
        )
    }

    companion object {
        private const val TAG = "CodexBarWidget"

        fun severityForUtilization(utilization: Float): QuotaSeverity {
            return when {
                utilization >= 0.85f -> QuotaSeverity.Critical
                utilization >= 0.60f -> QuotaSeverity.Warning
                else -> QuotaSeverity.Good
            }
        }

        /**
         * Severity has to stay recognizable on both a light and a dark launcher, so the colors
         * come from resources with a values-night variant rather than one fixed value.
         */
        @Composable
        fun severityColor(severity: QuotaSeverity): ColorProvider {
            val resourceId = when (severity) {
                    QuotaSeverity.Critical -> R.color.widget_severity_critical
                    QuotaSeverity.Warning -> R.color.widget_severity_warning
                    QuotaSeverity.Redacted,
                    QuotaSeverity.Unknown -> R.color.widget_severity_unknown
                    QuotaSeverity.Good -> R.color.widget_severity_good
            }
            val context = LocalContext.current
            return remember(context, resourceId) {
                fun colorForMode(nightMode: Int): Color {
                    val configuration = Configuration(context.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
                    }
                    return Color(ContextCompat.getColor(context.createConfigurationContext(configuration), resourceId))
                }
                // Let the launcher select the palette, even when its theme differs from the app.
                androidx.glance.color.ColorProvider(
                    day = colorForMode(Configuration.UI_MODE_NIGHT_NO),
                    night = colorForMode(Configuration.UI_MODE_NIGHT_YES)
                )
            }
        }
    }
}

/**
 * Resolves the application-scoped managers the widget renders from.
 *
 * Constructing them per render re-ran the legacy-preferences migration and warmed a second
 * credential cache on every launcher update, which is exactly the work that can outlast a
 * broadcast. Hilt already owns one instance of each; direct construction stays as a fallback for
 * hosts that update the widget before the application component exists.
 */
internal class WidgetDependencies private constructor(
    val widgetPrefs: WidgetPrefsManager,
    private val encryptedPrefs: EncryptedPrefsManager?
) {
    /**
     * Returns whether quota values must stay hidden. A failed or slow read keeps the
     * fail-closed default from [EncryptedPrefsManager] rather than blocking the composition.
     */
    suspend fun readWidgetRedaction(): Boolean {
        val prefsManager = encryptedPrefs ?: return true
        val warmed = runCatching {
            withTimeoutOrNull(SETTINGS_TIMEOUT_MILLIS) { prefsManager.warmCache() }
        }.getOrNull()
        if (warmed == null) {
            Log.w(TAG, "Widget privacy settings were unavailable within the render deadline")
        }
        return runCatching { prefsManager.getPrivacySettings().widgetRedactionEnabled }
            .getOrDefault(true)
    }

    companion object {
        private const val TAG = "CodexBarWidget"
        private const val SETTINGS_TIMEOUT_MILLIS = 2_000L

        fun of(appContext: Context): WidgetDependencies {
            val entryPoint = appSingletonEntryPointOrNull(appContext)
            val widgetPrefs = runCatching { entryPoint?.widgetPrefsManager() }.getOrNull()
                ?: WidgetPrefsManager(appContext)
            val prefsManager = runCatching { entryPoint?.encryptedPrefsManager() }.getOrNull()
                ?: runCatching { EncryptedPrefsManager(appContext) }.getOrNull()
            return WidgetDependencies(widgetPrefs, prefsManager)
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
        WidgetUpdater.update(context, GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }
}
