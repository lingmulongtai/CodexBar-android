package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
    var selectedServiceName by remember { mutableStateOf<String?>(null) }
    val selectedService = (uiState as? DashboardUiState.Content)
        ?.snapshot
        ?.services
        ?.firstOrNull { it.service.name == selectedServiceName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CodexBar") },
                actions = {
                    IconButton(
                        onClick = {
                            if (isMonitoring) viewModel.stopMonitoring() else viewModel.startMonitoring()
                        }
                    ) {
                        Icon(
                            imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isMonitoring) "Stop monitoring" else "Start monitoring",
                            tint = if (isMonitoring) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            }
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is DashboardUiState.Content -> {
                    if (state.snapshot.services.isEmpty()) {
                        EmptyState()
                    } else {
                        val failedServices = state.snapshot.services
                            .filterNot {
                                it.status == ServiceQuotaStatus.Fresh ||
                                    it.status == ServiceQuotaStatus.Redacted
                            }
                            .joinToString(", ") { it.service.displayName }
                        CardList(
                            services = state.snapshot.services,
                            errorBanner = failedServices.takeIf { it.isNotBlank() }
                                ?.let { "Needs attention: $it" },
                            onServiceClick = { selectedServiceName = it.service.name }
                        )
                    }
                }
            }
        }
    }

    selectedService?.let { service ->
        ServiceDetailSheet(
            service = service,
            onDismiss = { selectedServiceName = null },
            onRefresh = { viewModel.refresh() },
            onOpenSettings = {
                selectedServiceName = null
                onNavigateToSettings()
            }
        )
    }
}

@Composable
private fun CardList(
    services: List<ServiceQuotaPresentation>,
    errorBanner: String?,
    onServiceClick: (ServiceQuotaPresentation) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (errorBanner != null) {
            item {
                Text(
                    text = errorBanner,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        items(services, key = { it.service.name }) { service ->
            ServiceCard(
                service = service,
                onClick = { onServiceClick(service) }
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No services configured",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Go to Settings to add your API credentials",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
