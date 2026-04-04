package com.troikoss.continuum_explorer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.managers.selectionBackground
import com.troikoss.continuum_explorer.model.*
import com.troikoss.continuum_explorer.ui.components.ItemContextMenu
import com.troikoss.continuum_explorer.utils.*
import com.troikoss.continuum_explorer.utils.IconHelper.FileThumbnail
import com.troikoss.continuum_explorer.utils.IconHelper.isMimeTypePreviewable

/**
 * Renders a single file or folder item, switching layout based on the current ViewMode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileView(
    file: UniversalFile,
    itemPositions: MutableMap<UniversalFile, Rect>,
    containerCoordinates: LayoutCoordinates?,
    mousePosition: () -> Offset?,
    appState: FileExplorerState,
    focusRequester: FocusRequester,
    isHovered: Boolean
) {
    // 1. Selection & State Manager
    val selectionManager = appState.selectionManager
    val isSelected = selectionManager.isSelected(file)
    val isLead = selectionManager.leadItem == file
    val icon = remember(file) { IconHelper.getIconForItem(file) }
    
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var isOverflowing by remember { mutableStateOf(false) }
    
    val density = LocalDensity.current
    val tooltipState = rememberTooltipState()

    // 2. Lifecycle & Positioning
    DisposableEffect(file) {
        onDispose { itemPositions.remove(file) }
    }

    val mouseTooltipProvider = remember {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize
            ): androidx.compose.ui.unit.IntOffset {
                val pos = mousePosition()
                val itemRect = itemPositions[file]
                val relativeOffset = if (pos != null && itemRect != null) pos - itemRect.topLeft else Offset.Zero
                return androidx.compose.ui.unit.IntOffset(
                    x = anchorBounds.left + relativeOffset.x.toInt(),
                    y = anchorBounds.top + relativeOffset.y.toInt() + 40
                )
            }
        }
    }

    // 3. Layout Configuration
    val viewMode = appState.activeViewMode
    val isGridView = viewMode == ViewMode.GRID
    val shape = if (isGridView) RoundedCornerShape(8.dp) else RectangleShape
    
    Box(modifier = if (isGridView) Modifier.padding(4.dp) else Modifier) {
        TooltipBox(
            positionProvider = mouseTooltipProvider,
            tooltip = { if (isOverflowing) PlainTooltip { Text(file.name) } },
            state = tooltipState
        ) {
            Surface(
                color = Color.Transparent,
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth()
                    .fileDragSource(
                        file = file,
                        selectionManager = selectionManager,
                        appState = appState,
                        onShowContextMenu = { offset ->
                            menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                            showMenu = true
                        },
                        onDismissContextMenu = { showMenu = false }
                    )
                    .then(
                        if (file.isDirectory) {
                            Modifier.fileDropTarget(
                                appState = appState,
                                destPath = file.fileRef,
                                destSafUri = file.documentFileRef?.uri
                            )
                        } else Modifier
                    )
                    .trackPosition(file, itemPositions, containerCoordinates)
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
                Box(
                    modifier = Modifier.selectionBackground(isSelected, isHovered, isLead, shape)
                ) {
                    when (viewMode) {
                        ViewMode.GRID -> FileGridView(file, isSelected, icon, appState)
                        ViewMode.CONTENT -> FileContentView(file, isSelected, appState)
                        ViewMode.DETAILS -> FileDetailsView(file, isSelected, appState) { isOverflowing = it }
                    }
                }
            }
        }

        // 4. Context Menu
        Box(modifier = Modifier.offset(menuOffset.x, menuOffset.y)) {
            ItemContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                appState = appState
            )
        }
    }
}

// --- Specific View Components ---

@Composable
private fun FileGridView(
    file: UniversalFile,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    appState: FileExplorerState
) {
    Column(
        modifier = Modifier.padding(8.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            FileThumbnail(
                file = file,
                modifier = Modifier.size((appState.folderConfigs.gridItemSize * 0.6f).dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )

            if (isMimeTypePreviewable(file)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).padding(3.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }
        }
        Text(
            text = file.name,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FileContentView(
    file: UniversalFile,
    isSelected: Boolean,
    appState: FileExplorerState
) {
    Column {
        val formattedSize = remember(file) { appState.formatSize(file.length) }
        ListItem(
            headlineContent = { Text(file.name) },
            supportingContent = { Text(if (file.isDirectory) "Folder" else formattedSize) },
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

@Composable
private fun FileDetailsView(
    file: UniversalFile,
    isSelected: Boolean,
    appState: FileExplorerState,
    onOverflowChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnail(
                file = file,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = file.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    onOverflowChange(textLayoutResult.hasVisualOverflow)
                }
            )

            appState.folderConfigs.extraColumns.forEach { column ->
                val width = appState.folderConfigs.columnWidths[column.type] ?: column.minWidth
                val text = when (column.type) {
                    FileColumnType.DATE -> remember(file) { appState.formatDate(file.lastModified) }
                    FileColumnType.SIZE -> if (file.isDirectory) "--" else remember(file) { appState.formatSize(file.length) }
                    else -> ""
                }

                Text(
                    text = text,
                    modifier = Modifier.width(width).padding(start = 32.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
        HorizontalDivider()
    }
}
