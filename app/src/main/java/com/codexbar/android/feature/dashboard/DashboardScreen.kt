package com.codexbar.android.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AcquisitionMode
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.ui.components.LocalBottomChromeInset
import com.codexbar.android.ui.components.ProviderOrderEditor
import com.codexbar.android.ui.components.glassBackdropSource
import com.codexbar.android.ui.components.providerDragReorder
import com.codexbar.android.ui.components.rememberGlassBackdrop
import com.codexbar.android.ui.components.rememberProviderDragReorder
import com.codexbar.android.ui.theme.CodexBarSpacing
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import java.time.Instant

private const val TwoPaneMinWidthDp = 720f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToConnections: () -> Unit,
    initialSelectedService: AiService? = null,
    onInitialSelectionConsumed: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refresh by viewModel.refreshState.collectAsStateWithLifecycle()
    val providerOrder by viewModel.providerOrder.collectAsStateWithLifecycle()

    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(Unit) {
        viewModel.refreshIfStale(localeTag)
    }

    DashboardContent(
        uiState = uiState,
        refresh = refresh,
        onRefresh = viewModel::refresh,
        onNavigateToConnections = onNavigateToConnections,
        initialSelectedService = initialSelectedService,
        onInitialSelectionConsumed = onInitialSelectionConsumed,
        providerOrder = providerOrder,
        onProviderOrderChange = viewModel::setProviderOrder,
        onClearResetHistory = viewModel::clearResetHistory
    )
}

@Composable
fun DashboardPreviewScreen(
    snapshot: QuotaPresentationSnapshot,
    onNavigateToConnections: () -> Unit = {}
) {
    var order by remember { mutableStateOf(emptyList<AiService>()) }
    DashboardContent(
        providerOrder = order,
        onProviderOrderChange = { order = it },
        uiState = DashboardUiState.Content(snapshot),
        refresh = DashboardRefreshState(),
        onRefresh = {},
        onNavigateToConnections = onNavigateToConnections
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    refresh: DashboardRefreshState,
    onRefresh: () -> Unit,
    onNavigateToConnections: () -> Unit,
    initialSelectedService: AiService? = null,
    onInitialSelectionConsumed: () -> Unit = {},
    providerOrder: List<AiService> = emptyList(),
    onProviderOrderChange: (List<AiService>) -> Unit = {},
    onClearResetHistory: (AiService) -> Unit = {}
) {
    val themeProfile = LocalCodexBarThemeProfile.current
    var selectedServiceName by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedServiceName by rememberSaveable { mutableStateOf<String?>(null) }
    var showOnlyAttention by rememberSaveable { mutableStateOf(false) }
    var showOrder by rememberSaveable { mutableStateOf(false) }
    val snapshot = (uiState as? DashboardUiState.Content)?.snapshot
    val services = snapshot?.services.orEmpty()
    val orderedServices = remember(services, providerOrder) {
        DashboardProviderOrder.apply(services, providerOrder) { it.service }
    }
    if (showOrder) {
        ModalBottomSheet(onDismissRequest = { showOrder = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.provider_order_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.dashboard_order_hint), style = MaterialTheme.typography.bodyMedium)
                ProviderOrderEditor(orderedServices.map { it.service }, onProviderOrderChange)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onProviderOrderChange(emptyList()) }, enabled = providerOrder.isNotEmpty()) {
                        Text(stringResource(R.string.dashboard_order_automatic))
                    }
                    Button(onClick = { showOrder = false }) { Text(stringResource(R.string.provider_order_done)) }
                }
            }
        }
    }

    // A notification or Now Bar entry names the provider it was opened for.
    LaunchedEffect(initialSelectedService) {
        if (initialSelectedService != null) {
            selectedServiceName = initialSelectedService.name
            onInitialSelectionConsumed()
        }
    }

    val explicitlySelectedService = services.firstOrNull { it.service.name == selectedServiceName }
    val glass = themeProfile.style == AppThemeStyle.LIQUID_GLASS
    val title = stringResource(R.string.navigation_dashboard)
    val grid = rememberLazyGridState()
    val backdrop = rememberGlassBackdrop()
    val updatedAt = snapshot?.generatedAt

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTwoPane = useTwoPaneDashboard(maxWidth.value)
        Column(Modifier.fillMaxSize()) {
            if (!glass) {
                // On wide screens the navigation rail already carries the design's mark.
                DashboardHeader(
                    refresh = refresh,
                    updatedAt = updatedAt,
                    onRefresh = onRefresh,
                    showMark = LocalConfiguration.current.screenWidthDp < 600
                )
            }
            // The pull gesture starts a refresh; progress is shown in place, never over the content.
            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                when (val state = uiState) {
                    is DashboardUiState.Loading -> {
                        Column(Modifier.fillMaxSize()) {
                            if (glass) GlassLargeTitle(title, refresh, updatedAt, Modifier.padding(horizontal = 16.dp))
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                SmallSpinner(32.dp)
                            }
                        }
                    }

                    is DashboardUiState.Content -> {
                        if (state.snapshot.services.isEmpty()) {
                            Column(Modifier.fillMaxSize()) {
                                if (glass) GlassLargeTitle(title, refresh, updatedAt, Modifier.padding(horizontal = 16.dp))
                                EmptyState(
                                    onOpenConnections = onNavigateToConnections,
                                    windowsMode = state.acquisitionMode == AcquisitionMode.WINDOWS,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            val failedServices = state.snapshot.services
                                .filter { it.needsAttention() }
                                .joinToString(", ") { it.service.displayName }
                            val errorBanner = failedServices.takeIf { it.isNotBlank() }
                                ?.let {
                                    stringResource(R.string.dashboard_needs_attention, it)
                                }
                            val paneService = explicitlySelectedService
                                ?: orderedServices.first()
                            val summary = remember(state.snapshot) {
                                state.snapshot.toDashboardSummary()
                            }
                            // The filter row disappears once everything recovers, so a stale
                            // selection must not leave the list empty with no way back.
                            val filterAttention = showOnlyAttention && summary.attentionCount > 0
                            val visibleServices = if (filterAttention) {
                                orderedServices.filter { it.needsAttention() }
                            } else {
                                orderedServices
                            }
                            val reorder = { order: List<AiService> ->
                                onProviderOrderChange(DashboardProviderOrder.merge(orderedServices.map { it.service }, order))
                            }

                            if (useTwoPane) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    CardList(
                                        grid = grid,
                                        services = visibleServices,
                                        allServices = state.snapshot.services,
                                        summary = summary,
                                        errorBanner = errorBanner,
                                        attentionCount = summary.attentionCount,
                                        showOnlyAttention = filterAttention,
                                        onAttentionFilterChange = { showOnlyAttention = it },
                                        selectedServiceName = paneService.service.name,
                                        expandedServiceName = null,
                                        refresh = refresh,
                                        updatedAt = updatedAt,
                                        onServiceClick = {
                                            selectedServiceName = it.service.name
                                        },
                                        onOpenDetails = { selectedServiceName = it.service.name },
                                        onManageConnection = onNavigateToConnections,
                                        onSummaryProviderClick = { selectedServiceName = it.name },
                                        onShowOrder = { showOrder = true },
                                        onReorder = reorder,
                                        modifier = Modifier
                                            .weight(0.55f)
                                            .then(if (glass) Modifier.glassBackdropSource(backdrop) else Modifier)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(0.45f)
                                            .fillMaxHeight()
                                            .then(if (glass) Modifier.statusBarsPadding() else Modifier)
                                            .padding(
                                                start = CodexBarSpacing.small,
                                                end = CodexBarSpacing.large,
                                                top = CodexBarSpacing.small,
                                                bottom = CodexBarSpacing.large + LocalBottomChromeInset.current
                                            )
                                    ) {
                                        ServiceDetailPane(
                                            service = paneService,
                                            onClearResetHistory = { onClearResetHistory(paneService.service) },
                                            onRefresh = onRefresh,
                                            onManageConnection = onNavigateToConnections
                                        )
                                    }
                                }
                            } else {
                                CardList(
                                    grid = grid,
                                    services = visibleServices,
                                    allServices = state.snapshot.services,
                                    summary = summary,
                                    errorBanner = errorBanner,
                                    attentionCount = summary.attentionCount,
                                    showOnlyAttention = filterAttention,
                                    onAttentionFilterChange = { showOnlyAttention = it },
                                    selectedServiceName = null,
                                    expandedServiceName = expandedServiceName,
                                    refresh = refresh,
                                    updatedAt = updatedAt,
                                    onServiceClick = {
                                        if (themeProfile.style == AppThemeStyle.INSTRUMENT) {
                                            // Instrument opens readings in place; Details opens the sheet.
                                            expandedServiceName = it.service.name.takeIf { name -> name != expandedServiceName }
                                        } else {
                                            selectedServiceName = it.service.name
                                        }
                                    },
                                    onOpenDetails = { selectedServiceName = it.service.name },
                                    onManageConnection = onNavigateToConnections,
                                    onSummaryProviderClick = { selectedServiceName = it.name },
                                    onShowOrder = { showOrder = true },
                                    onReorder = reorder,
                                    modifier = if (glass) Modifier.glassBackdropSource(backdrop) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }

        if (glass && uiState is DashboardUiState.Content && services.isNotEmpty()) {
            val collapsed by remember(grid) {
                derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 120 }
            }
            AnimatedVisibility(
                visible = collapsed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                GlassCollapsedHeader(
                    title = title,
                    refresh = refresh,
                    updatedAt = updatedAt,
                    backdrop = backdrop,
                    modifier = if (useTwoPane) Modifier.fillMaxWidth(0.55f) else Modifier
                )
            }
        }

        if (!useTwoPane) {
            explicitlySelectedService?.let { service ->
                ServiceDetailSheet(
                    service = service,
                    onClearResetHistory = { onClearResetHistory(service.service) },
                    onDismiss = { selectedServiceName = null },
                    onRefresh = onRefresh,
                    onManageConnection = {
                        selectedServiceName = null
                        onNavigateToConnections()
                    }
                )
            }
        }
    }
}

@Composable
private fun CardList(
    grid: LazyGridState,
    services: List<ServiceQuotaPresentation>,
    allServices: List<ServiceQuotaPresentation>,
    summary: DashboardSummary,
    errorBanner: String?,
    attentionCount: Int,
    showOnlyAttention: Boolean,
    onAttentionFilterChange: (Boolean) -> Unit,
    selectedServiceName: String?,
    expandedServiceName: String?,
    refresh: DashboardRefreshState,
    updatedAt: Instant?,
    onServiceClick: (ServiceQuotaPresentation) -> Unit,
    onOpenDetails: (ServiceQuotaPresentation) -> Unit,
    onManageConnection: () -> Unit,
    onSummaryProviderClick: (AiService) -> Unit,
    onShowOrder: () -> Unit,
    onReorder: (List<AiService>) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeProfile = LocalCodexBarThemeProfile.current
    val order = services.map { it.service }
    val drag = rememberProviderDragReorder(grid, order, onReorder)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Instrument's table rows need room for two reading cells beside the name.
        val cardMinWidth = when (themeProfile.style) {
            AppThemeStyle.MATERIAL_3 -> 300.dp
            AppThemeStyle.LIQUID_GLASS -> 340.dp
            AppThemeStyle.INSTRUMENT -> 420.dp
        }
        val columns = ((maxWidth - 32.dp) / cardMinWidth).toInt().coerceIn(1, 3)
        // A single column groups the providers into one list; wider grids use separate cards.
        val grouped = columns == 1
        val cardSpacing: Dp = when {
            !grouped -> 12.dp
            themeProfile.style == AppThemeStyle.MATERIAL_3 -> 3.dp
            else -> 0.dp
        }
        val sectionGap = Modifier.padding(bottom = 16.dp - cardSpacing)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            modifier = Modifier
                .fillMaxSize()
                .background(themeProfile.backgroundBrush),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 16.dp + LocalBottomChromeInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(cardSpacing),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (themeProfile.style == AppThemeStyle.LIQUID_GLASS) {
                item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                    GlassLargeTitle(
                        title = stringResource(R.string.navigation_dashboard),
                        refresh = refresh,
                        updatedAt = updatedAt,
                        modifier = sectionGap
                    )
                }
            }

            item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                DashboardHero(
                    summary = summary,
                    services = allServices,
                    onProviderClick = onSummaryProviderClick,
                    modifier = sectionGap
                )
            }

            if (attentionCount > 0) {
                item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                    Row(sectionGap, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showOnlyAttention,
                            onClick = { onAttentionFilterChange(false) },
                            label = { Text(stringResource(R.string.dashboard_filter_all)) }
                        )
                        FilterChip(
                            selected = showOnlyAttention,
                            onClick = { onAttentionFilterChange(true) },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.dashboard_filter_attention,
                                        attentionCount
                                    )
                                )
                            }
                        )
                    }
                }
            }

            if (errorBanner != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(sectionGap),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = errorBanner,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            item(key = "section", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    count = services.size,
                    canReorder = allServices.size > 1,
                    onShowOrder = onShowOrder,
                    modifier = Modifier.padding(bottom = 8.dp - cardSpacing.coerceAtMost(8.dp))
                )
            }

            itemsIndexed(services, key = { _, service -> service.service.name }) { index, service ->
                val grouping = when {
                    !grouped || services.size == 1 -> CardGrouping.Alone
                    index == 0 -> CardGrouping.First
                    index == services.lastIndex -> CardGrouping.Last
                    else -> CardGrouping.Middle
                }
                ServiceCard(
                    service = service,
                    onClick = { onServiceClick(service) },
                    selected = service.service.name == selectedServiceName,
                    refreshing = service.service in refresh.pending,
                    grouping = grouping,
                    expanded = service.service.name == expandedServiceName,
                    onOpenDetails = { onOpenDetails(service) },
                    onManageConnection = onManageConnection,
                    modifier = Modifier.providerDragReorder(drag, service.service, order)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(count: Int, canReorder: Boolean, onShowOrder: () -> Unit, modifier: Modifier = Modifier) {
    val style = LocalCodexBarThemeProfile.current.style
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (style == AppThemeStyle.LIQUID_GLASS) 16.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.dashboard_section_services),
            style = if (style == AppThemeStyle.LIQUID_GLASS) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = colors.outline
        )
        Spacer(Modifier.weight(1f))
        if (canReorder) {
            if (style == AppThemeStyle.LIQUID_GLASS) {
                TextButton(onClick = onShowOrder) {
                    Text(stringResource(R.string.provider_order_action), fontWeight = FontWeight.SemiBold)
                }
            } else {
                IconButton(onClick = onShowOrder) {
                    Icon(
                        Icons.Rounded.SwapVert,
                        contentDescription = stringResource(R.string.provider_order_action),
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun EmptyState(onOpenConnections: () -> Unit, windowsMode: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dashboard_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(if (windowsMode) R.string.desktop_empty_message else R.string.dashboard_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onOpenConnections) {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_open_connections))
            }
        }
    }
}

internal fun useTwoPaneDashboard(widthDp: Float): Boolean {
    return widthDp >= TwoPaneMinWidthDp
}
