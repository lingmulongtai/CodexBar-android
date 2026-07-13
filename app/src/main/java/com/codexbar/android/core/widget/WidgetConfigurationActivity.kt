package com.codexbar.android.core.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.codexbar.android.MainActivity
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import com.codexbar.android.ui.theme.CodexBarTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class WidgetConfigurationActivity : AppCompatActivity() {

    @Inject
    lateinit var encryptedPrefsManager: EncryptedPrefsManager

    @Inject
    lateinit var widgetPrefsManager: WidgetPrefsManager

    private val appWidgetId: Int by lazy {
        intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default result is CANCELED — if user backs out, widget isn't added
        setResult(RESULT_CANCELED)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()

        lifecycleScope.launch {
            encryptedPrefsManager.warmCache()

            setContent {
                CodexBarTheme {
                    val availableServices = AiService.entries.filter {
                        encryptedPrefsManager.hasCredential(it)
                    }
                    val existingConfig = widgetPrefsManager.getWidgetConfig(appWidgetId)
                    val checkedState = remember {
                        mutableStateMapOf<AiService, Boolean>().apply {
                            val existingServices = existingConfig.services.toSet()
                            availableServices.forEach { service ->
                                this[service] = if (existingServices.isEmpty()) true else service in existingServices
                            }
                        }
                    }
                    var showReset by remember { mutableStateOf(existingConfig.showReset) }
                    var showPace by remember { mutableStateOf(existingConfig.showPace) }
                    var showFreshness by remember { mutableStateOf(existingConfig.showFreshness) }
                    var maxRows by remember { mutableStateOf(existingConfig.maxRows.coerceIn(1, 6)) }
                    val anyChecked = checkedState.values.any { it }
                    val isReconfigure = existingConfig.services.isNotEmpty()

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Widget setup") }
                            )
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Choose the connected services and details shown on your home screen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Services",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Select at least one connected service.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (availableServices.isEmpty()) {
                                        Text(
                                            text = "Connect a service in the app before adding the widget.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        OutlinedButton(onClick = ::openAppSettings) {
                                            Text("Open app settings")
                                        }
                                    } else {
                                        availableServices.forEach { service ->
                                            ServiceCheckRow(
                                                service = service,
                                                checked = checkedState[service] ?: false,
                                                onCheckedChange = { checkedState[service] = it }
                                            )
                                        }
                                    }
                                }
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Display options",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    ConfigToggleRow(
                                        title = "Reset countdown",
                                        subtitle = "Show when each quota window resets.",
                                        checked = showReset,
                                        onCheckedChange = { showReset = it }
                                    )
                                    ConfigToggleRow(
                                        title = "Pace and forecast",
                                        subtitle = "Show whether current usage can last until reset.",
                                        checked = showPace,
                                        onCheckedChange = { showPace = it }
                                    )
                                    ConfigToggleRow(
                                        title = "Last updated",
                                        subtitle = "Show how fresh the displayed data is.",
                                        checked = showFreshness,
                                        onCheckedChange = { showFreshness = it }
                                    )
                                    Text(
                                        text = "Maximum rows per service: $maxRows",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Slider(
                                        value = maxRows.toFloat(),
                                        onValueChange = { maxRows = it.roundToInt().coerceIn(1, 6) },
                                        valueRange = 1f..6f,
                                        steps = 4
                                    )
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text(
                                    text = "Quota data loads after you save. You can also tap the refresh icon on the widget at any time.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        setResult(RESULT_CANCELED)
                                        finish()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel")
                                }

                                Button(
                                    onClick = {
                                        confirmSelection(
                                            checkedState = checkedState,
                                            showReset = showReset,
                                            showPace = showPace,
                                            showFreshness = showFreshness,
                                            maxRows = maxRows
                                        )
                                    },
                                    enabled = anyChecked,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isReconfigure) "Save" else "Add widget")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openAppSettings() {
        setResult(RESULT_CANCELED)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                data = Uri.parse("codexbar://settings")
            }
        )
        finish()
    }

    private fun confirmSelection(
        checkedState: Map<AiService, Boolean>,
        showReset: Boolean,
        showPace: Boolean,
        showFreshness: Boolean,
        maxRows: Int
    ) {
        val selectedServices = checkedState
            .filter { it.value }
            .keys

        // commit() ensures data is persisted before the widget reads it
        widgetPrefsManager.saveWidgetConfig(
            appWidgetId,
            WidgetDisplayConfig(
                services = selectedServices.sortedBy { it.ordinal },
                showReset = showReset,
                showPace = showPace,
                showFreshness = showFreshness,
                maxRows = maxRows
            )
        )

        // Return RESULT_OK first so the launcher places the widget
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)

        WorkManagerInitializer.enqueueManualQuotaRefresh(
            context = this,
            source = "widget_config"
        )

        // Trigger widget update asynchronously to avoid main-thread deadlock
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigurationActivity)
                    .getGlanceIdBy(appWidgetId)
                QuotaGlanceWidget().update(this@WidgetConfigurationActivity, glanceId)
            } catch (_: Exception) {
                // Widget will pick up saved config on next periodic update
            }
            finish()
        }
    }
}

@Composable
private fun ConfigToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ServiceCheckRow(
    service: AiService,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color(service.brandColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = service.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
