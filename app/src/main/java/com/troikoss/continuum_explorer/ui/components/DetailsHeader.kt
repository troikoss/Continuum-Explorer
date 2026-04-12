package com.troikoss.continuum_explorer.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.model.FileColumnType
import com.troikoss.continuum_explorer.model.LibraryItem
import com.troikoss.continuum_explorer.model.SortOrder
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.VerticalResizeHandle
import com.troikoss.continuum_explorer.utils.contextMenuDetector

/**
 * Header row for the "Details" view mode. 
 */
@Composable
fun DetailsHeader(appState: FileExplorerState, scrollState: ScrollState, nameColumnWidth: Dp) {
    val density = LocalDensity.current
    var showColumnMenu by remember { mutableStateOf(false) }
    var columnMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    Box {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .contextMenuDetector(enableLongPress = true, aggressive = true) { offset: Offset ->
                    columnMenuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                    showColumnMenu = true
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(36.dp))

            SortableHeaderLabel(
                label = stringResource(R.string.details_header_name),
                modifier = Modifier.width(nameColumnWidth),
                isActive = appState.folderConfigs.sortParams.columnType == FileColumnType.NAME,
                order = appState.folderConfigs.sortParams.order,
                onClick = { appState.folderConfigs.toggleSort(FileColumnType.NAME, appState.getCurrentStorageKey()) { appState.refresh() } }
            )

            appState.folderConfigs.visibleColumns.forEachIndexed { index, column ->
                val currentWidth = appState.folderConfigs.columnWidths[column.type] ?: column.minWidth

                // Each handle resizes the column to its LEFT:
                // handle at index 0 → resizes Name; handle at index N → resizes visibleColumns[N-1]
                val prevColumnType = if (index == 0) FileColumnType.NAME
                                     else appState.folderConfigs.visibleColumns[index - 1].type
                val prevMinWidth = if (index == 0) 75.dp
                                   else appState.folderConfigs.visibleColumns[index - 1].minWidth

                VerticalResizeHandle(
                    onResize = { delta ->
                        val w = appState.folderConfigs.columnWidths[prevColumnType] ?: prevMinWidth
                        appState.folderConfigs.columnWidths[prevColumnType] = (w + delta).coerceIn(75.dp, 800.dp)
                        appState.folderConfigs.saveColumnWidths(appState.getCurrentStorageKey())
                    },
                    modifier = Modifier.height(24.dp)
                )

                SortableHeaderLabel(
                    label = column.label,
                    modifier = Modifier
                        .width(currentWidth)
                        .padding(start = 8.dp),
                    isActive = appState.folderConfigs.sortParams.columnType == column.type,
                    order = appState.folderConfigs.sortParams.order,
                    onClick = { appState.folderConfigs.toggleSort(column.type, appState.getCurrentStorageKey()) { appState.refresh() } }
                )
            }
        }

        DropdownMenu(
            expanded = showColumnMenu,
            onDismissRequest = { showColumnMenu = false },
            offset = columnMenuOffset
        ) {
            appState.folderConfigs.extraColumns.forEach { column ->
                val isVisible = column.type !in appState.folderConfigs.hiddenColumns
                val isDisabled = (column.type == FileColumnType.DATE_DELETED || column.type == FileColumnType.DELETED_FROM) && appState.libraryItem != LibraryItem.RecycleBin
                DropdownMenuItem(
                    text = {
                        Text(
                            column.label,
                            color = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        if (isVisible && !isDisabled) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Spacer(Modifier.size(16.dp))
                        }
                    },
                    onClick = {
                        if (!isDisabled) {
                            appState.folderConfigs.toggleColumnVisibility(column.type, appState.getCurrentStorageKey())
                            showColumnMenu = false
                        }
                    }
                )
            }
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
