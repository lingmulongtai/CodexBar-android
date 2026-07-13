package com.codexbar.android.core.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.ui.theme.CodexBarTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigurationActivity : ComponentActivity() {

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

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Configure Widget") }
                            )
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Select services to display in this widget:",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (availableServices.isEmpty()) {
                                Text(
                                    text = "No services configured yet. Please add credentials in the app settings first.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                for (service in AiService.entries) {
                                    val hasCredential = service in availableServices
                                    ServiceCheckRow(
                                        service = service,
                                        checked = checkedState[service] ?: false,
                                        enabled = hasCredential,
                                        onCheckedChange = { checkedState[service] = it }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Display options",
                                style = MaterialTheme.typography.titleMedium
                            )
                            ConfigCheckRow(
                                title = "Show reset countdown",
                                checked = showReset,
                                onCheckedChange = { showReset = it }
                            )
                            ConfigCheckRow(
                                title = "Show pace and forecast",
                                checked = showPace,
                                onCheckedChange = { showPace = it }
                            )
                            ConfigCheckRow(
                                title = "Show freshness",
                                checked = showFreshness,
                                onCheckedChange = { showFreshness = it }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Rows per provider: $maxRows",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { maxRows = (maxRows - 1).coerceAtLeast(1) }
                                ) {
                                    Text("-")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { maxRows = (maxRows + 1).coerceAtMost(6) }
                                ) {
                                    Text("+")
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Action buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp)
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

                                Spacer(modifier = Modifier.width(12.dp))

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
                                    Text("Confirm")
                                }
                            }
                        }
                    }
                }
            }
        }
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
private fun ConfigCheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ServiceCheckRow(
    service: AiService,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) Color(service.brandColor) else Color.Gray
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = service.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!enabled) {
                Text(
                    text = "No credentials configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
