package com.codexbar.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Stateful settings sections stay composed while their cards flow into readable columns. */
@Composable
internal fun SettingsColumns(columns: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = 16.dp.roundToPx()
        val width = constraints.maxWidth
        val columnWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val bottoms = IntArray(columns)
        val placements = measurables.map { measurable ->
            val column = bottoms.indices.minBy { bottoms[it] }
            val child = measurable.measure(Constraints.fixedWidth(columnWidth).copy(maxHeight = Constraints.Infinity))
            val top = bottoms[column]
            bottoms[column] = top + child.height + gap
            Triple(child, column * (columnWidth + gap), top)
        }
        val height = ((bottoms.maxOrNull() ?: gap) - gap).coerceAtLeast(0)
        layout(width, height) { placements.forEach { (child, x, y) -> child.placeRelative(x, y) } }
    }
}
