package com.example.continuum_explorer.ui

import android.view.PointerIcon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.model.FileColumnType
import com.example.continuum_explorer.model.SortOrder

/**
 * Header row for the "Details" view mode. 
 */
@Composable
fun DetailsHeader(appState: FileExplorerState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(36.dp))

        SortableHeaderLabel(
            label = "Name",
            modifier = Modifier.weight(1f),
            isActive = appState.folderConfigs.sortParams.columnType == FileColumnType.NAME,
            order = appState.folderConfigs.sortParams.order,
            onClick = { appState.folderConfigs.toggleSort(FileColumnType.NAME, appState.getCurrentStorageKey()) { appState.refresh() } }
        )

        appState.folderConfigs.extraColumns.forEach { column ->
            val widthPx = appState.folderConfigs.columnWidths[column.type] ?: column.minWidth

            VerticalResizeHandle { delta ->
                val currentWidth = appState.folderConfigs.columnWidths[column.type] ?: column.minWidth
                appState.folderConfigs.columnWidths[column.type] = (currentWidth - delta).coerceIn(100f, 800f)
            }

            SortableHeaderLabel(
                label = column.label,
                modifier = Modifier
                    .width(with(LocalDensity.current) { widthPx.toDp() })
                    .padding(start = 8.dp),
                isActive = appState.folderConfigs.sortParams.columnType == column.type,
                order = appState.folderConfigs.sortParams.order,
                onClick = { appState.folderConfigs.toggleSort(column.type, appState.getCurrentStorageKey()) { appState.refresh() } }
            )
        }
    }
}

@Composable
fun SortableHeaderLabel(
    label: String,
    modifier: Modifier,
    isActive: Boolean,
    order: SortOrder,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        if (isActive) {
            Icon(
                imageVector = if (order == SortOrder.Ascending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun VerticalResizeHandle(onResize: (Float) -> Unit) {
    val context = LocalContext.current
    val resizeIcon = remember(context) {
        androidx.compose.ui.input.pointer.PointerIcon(
            PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
        )
    }

    Box(
        modifier = Modifier
            .width(16.dp)
            .height(24.dp)
            .pointerHoverIcon(resizeIcon)
            .pointerInput(onResize) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val dragChange = event.changes.firstOrNull() ?: break
                        if (!dragChange.pressed) break
                        val delta = dragChange.positionChange().x
                        if (delta != 0f) {
                            onResize(delta)
                            dragChange.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        VerticalDivider(
            modifier = Modifier
                .height(24.dp)
                .width(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
