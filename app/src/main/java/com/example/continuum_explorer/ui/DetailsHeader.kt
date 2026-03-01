package com.example.continuum_explorer.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.model.FileColumnType
import com.example.continuum_explorer.model.SortOrder
import com.example.continuum_explorer.utils.VerticalResizeHandle

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
            val currentWidthPx = appState.folderConfigs.columnWidths[column.type] ?: column.minWidth

            // This is your resize handle
            VerticalResizeHandle(
                onResize = { delta ->
                    // Use + delta so dragging right makes it wider
                    val newWidth = currentWidthPx + delta
                    appState.folderConfigs.columnWidths[column.type] = newWidth.coerceIn(100f, 800f)
                },
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center
            )

            SortableHeaderLabel(
                label = column.label,
                modifier = Modifier
                    .width(with(LocalDensity.current) { currentWidthPx.toDp() })
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


