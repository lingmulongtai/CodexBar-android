package com.codexbar.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService

/** Long-press anywhere on a row; TalkBack exposes equivalent move actions. */
@Composable
fun ProviderOrderEditor(services: List<AiService>, onChange: (List<AiService>) -> Unit) {
    val grid = rememberLazyGridState()
    val drag = rememberProviderDragReorder(grid, services, onChange)
    LazyVerticalGrid(columns = GridCells.Fixed(1), state = grid,
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        itemsIndexed(services, key = { _, service -> service.name }) { index, service ->
            Row(Modifier.fillMaxWidth().height(56.dp).providerDragReorder(drag, service, services), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.width(28.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProviderIcon(service, Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(service.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.DragHandle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

