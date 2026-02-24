package com.example.continuum_explorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.model.*
import com.example.continuum_explorer.utils.*
import com.example.continuum_explorer.utils.IconHelper.FileThumbnail
import com.example.continuum_explorer.utils.IconHelper.isMimeTypePreviewable

/**
 * Renders a single file or folder item. 
 */
@Composable
fun FileView(
    file: UniversalFile,
    itemPositions: MutableMap<UniversalFile, Rect>,
    containerCoordinates: LayoutCoordinates?,
    mousePosition: Offset?,
    appState: FileExplorerState,
    focusRequester: FocusRequester
) {
    val selectionManager = appState.selectionManager
    val name = file.name
    val isFolder = file.isDirectory
    val icon = IconHelper.getIconForItem(file)

    val interactionSource = remember { MutableInteractionSource() }

    DisposableEffect(file) {
        onDispose { itemPositions.remove(file) }
    }

    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero)}
    val density = LocalDensity.current

    val isHovered = mousePosition?.let { pos ->
        itemPositions[file]?.contains(pos)
    } ?: false

    Box {
        // We put all gesture detectors on this Surface.
        // We avoid putting selection-based modifiers here so it doesn't recompose
        // and cancel active gestures (like dragging) when selection changes.
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .fileDragSource(file, selectionManager, appState)
                .then(
                    if (isFolder) {
                        Modifier.fileDropTarget(
                            appState = appState,
                            destPath = file.fileRef,
                            destSafUri = file.documentFileRef?.uri
                        )
                    } else Modifier
                )
                .trackPosition(file, itemPositions, containerCoordinates)
                .hoverable(interactionSource = interactionSource)
                .itemGestures(
                    file = file,
                    selectionManager = selectionManager,
                    focusRequester = focusRequester,
                    appState = appState
                )
                .contextMenuDetector(enableLongPress = false, aggressive = true) { offset ->
                    if (!selectionManager.isSelected(file)) {
                        selectionManager.handleRowClick(file, false, false)
                    }
                    menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                    showMenu = true
                }
        ) {
            // Internal content that handles the background and actual visuals
            Box(modifier = Modifier.selectionBackground(file, selectionManager, isHovered)) {
                when (appState.activeViewMode) {
                    ViewMode.GRID -> {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val isSelected = selectionManager.isSelected(file)
                            Box(contentAlignment = Alignment.BottomEnd) {
                                FileThumbnail(
                                    file = file,
                                    modifier = Modifier.size((appState.folderConfigs.gridItemSize * 0.6f).dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )

                                if (isMimeTypePreviewable(file)){
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(3.dp),

                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            Text(text = name,textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    ViewMode.CONTENT -> {
                        Column {
                            val isSelected = selectionManager.isSelected(file)
                            ListItem(
                                headlineContent = { Text(name) },
                                supportingContent = { Text(if (isFolder) "Folder" else appState.formatSize(file.length)) },
                                leadingContent = {
                                    FileThumbnail(
                                        file = file,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            HorizontalDivider()
                        }
                    }

                    ViewMode.DETAILS -> {
                        Column {
                            val isSelected = selectionManager.isSelected(file)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(name, modifier = Modifier.weight(1f), maxLines = 1)

                                appState.folderConfigs.extraColumns.forEach { column ->
                                    val widthPx = appState.folderConfigs.columnWidths[column.type] ?: column.minWidth
                                    val text = when(column.type) {
                                        FileColumnType.DATE -> appState.formatDate(file.lastModified)
                                        FileColumnType.SIZE -> if (isFolder) "--" else appState.formatSize(file.length)
                                        else -> ""
                                    }

                                    Text(
                                        text = text,
                                        modifier = Modifier
                                            .width(with(LocalDensity.current) { widthPx.toDp() })
                                            .padding(start = 32.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.offset(menuOffset.x, menuOffset.y)){
            ItemContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                appState = appState
            )
        }
    }
}
