package com.troikoss.continuum_explorer.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.model.ViewMode
import com.troikoss.continuum_explorer.ui.components.BackgroundContextMenu
import com.troikoss.continuum_explorer.ui.components.DetailsHeader
import com.troikoss.continuum_explorer.utils.*
import kotlinx.coroutines.delay

/**
 * The primary view for displaying files.
 * Handles layout (Grid vs List), selection marquee, and auto-scrolling.
 */
@Composable
fun FileContent(appState: FileExplorerState) {
    val selectionManager = appState.selectionManager
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val viewMode = appState.activeViewMode

    // --- State Management ---
    val itemPositions = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<UniversalFile, Rect>() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val marquee = rememberMarquee()
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var mousePosition by remember { mutableStateOf<Offset?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var showSpinner by remember { mutableStateOf(false) }
    var gridContainerTopPx by remember { mutableFloatStateOf(0f) }
    var gridContainerHeightPx by remember { mutableFloatStateOf(0f) }

    // Recreate grid state when the path changes to keep scroll fresh
    val gridState = key(appState.loadedPathKey) {
        rememberLazyGridState(initialFirstVisibleItemIndex = appState.scrollToItemIndex ?: 0)
    }

    // Dynamic column count based on view mode and container width
    val columnCount = remember(
        viewMode,
        containerCoordinates?.size?.width,
        appState.folderConfigs.gridItemSize,
        density
    ) {
        val width = containerCoordinates?.size?.width ?: 0
        if (viewMode == ViewMode.GRID && width > 0) {
            val totalPaddingPx = with(density) { 80.dp.toPx() }
            val gridItemSizePx = with(density) { appState.folderConfigs.gridItemSize.dp.toPx() }
            val availableGridWidthPx = (width - totalPaddingPx).coerceAtLeast(0f)
            (availableGridWidthPx / gridItemSizePx).toInt().coerceAtLeast(1)
        } else 1
    }

    // --- Side Effects ---

    // Focus management
    LaunchedEffect(appState.isSearchUIActive) {
        if (!appState.isSearchUIActive) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Position tracking cleanup
    LaunchedEffect(selectionManager) {
        itemPositions.clear()
    }

    // Scroll initialization cleanup
    LaunchedEffect(gridState) {
        if (appState.scrollToItemIndex != null) {
            appState.onScrollToItemCompleted()
        }
    }

    // Focused item auto-scroll (keyboard navigation)
    LaunchedEffect(selectionManager.leadItem) {
        val leadFile = selectionManager.leadItem ?: return@LaunchedEffect
        val index = selectionManager.allFiles.indexOf(leadFile)
        if (index != -1) {
            val layoutInfo = gridState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@LaunchedEffect

            val itemInfo = visibleItems.find { it.index == index }
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

            if (itemInfo != null) {
                val itemTop = itemInfo.offset.y
                val itemHeight = itemInfo.size.height
                val itemBottom = itemTop + itemHeight

                if (itemTop < layoutInfo.viewportStartOffset) {
                    gridState.animateScrollToItem(index, 0)
                } else if (itemBottom > layoutInfo.viewportEndOffset) {
                    gridState.animateScrollToItem(index, -(viewportHeight - itemHeight))
                }
            } else {
                val firstVisibleIndex = visibleItems.first().index
                if (index < firstVisibleIndex) {
                    gridState.animateScrollToItem(index, 0)
                } else {
                    val estimatedHeight = visibleItems.lastOrNull()?.size?.height ?: 100
                    gridState.animateScrollToItem(index, -(viewportHeight - estimatedHeight))
                }
            }
        }
    }

    // Debounced loading spinner
    LaunchedEffect(appState.isLoading) {
        if (appState.isLoading) {
            delay(400)
            showSpinner = true
        } else {
            showSpinner = false
        }
    }

    // System drag auto-scroll
    LaunchedEffect(appState.activeDragY.value) {
        if (appState.activeDragY.value == null) return@LaunchedEffect
        val threshold = 120f
        val maxSpeed = 60f
        while (true) {
            val dragY = appState.activeDragY.value ?: break
            val relY = dragY - gridContainerTopPx
            val h = gridContainerHeightPx
            val delta = when {
                relY < threshold && relY > 0f ->
                    -(maxSpeed * ((threshold - relY) / threshold).coerceIn(0f, 1f))
                h > 0f && relY > h - threshold ->
                    maxSpeed * ((relY - (h - threshold)) / threshold).coerceIn(0f, 1f)
                else -> 0f
            }
            if (delta != 0f) gridState.scrollBy(delta)
            delay(16L)
        }
    }

    // Marquee auto-scroller
    MarqueeAutoScroller(
        dragStart = dragStart,
        dragEnd = dragEnd,
        containerCoordinates = containerCoordinates,
        gridState = gridState,
        onDragStartChange = { dragStart = it },
        onSelectionChange = { start, end ->
            marquee.updateSelection(
                start = start,
                end = end,
                itemPositions = itemPositions,
                allFiles = appState.files,
                columnCount = columnCount,
                onSelectionChange = { selectionManager.updateSelectionFromDrag(it) }
            )
        }
    )

    // --- UI Layout ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onGloballyPositioned { containerCoordinates = it }
            .containerGestures(
                selectionManager = selectionManager,
                focusRequester = focusRequester,
                viewMode = viewMode,
                columns = columnCount,
                onZoom = { factor ->
                    val newSize = (appState.folderConfigs.gridItemSize * factor).toInt()
                    appState.folderConfigs.updateGridSize(
                        newSize.coerceIn(60, 300),
                        appState.getCurrentStorageKey()
                    )
                },
                onDragStart = { offset ->
                    dragStart = offset
                    marquee.reset()
                    selectionManager.clear(true)
                },
                onDrag = { offset ->
                    dragEnd = offset
                    marquee.updateSelection(
                        start = dragStart,
                        end = dragEnd,
                        itemPositions = itemPositions,
                        allFiles = appState.files,
                        columnCount = columnCount,
                        onSelectionChange = { selectionManager.updateSelectionFromDrag(it) }
                    )
                },
                onDragEnd = { dragStart = null; dragEnd = null },
                mousePosition = { mousePosition = it },
                appState = appState,
                gridState = gridState
            )
            .contextMenuDetector(enableLongPress = true, aggressive = false) { offset ->
                val isOverItem = itemPositions.values.any { rect -> rect.contains(offset) }
                if (!isOverItem) {
                    menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                    showMenu = true
                }
            }
    ) {
        if (showSpinner) {
            LoadingOverlay()
        }

        FileLayout(
            appState = appState,
            gridState = gridState,
            itemPositions = itemPositions,
            containerCoordinates = containerCoordinates,
            mousePosition = { mousePosition },
            focusRequester = focusRequester,
            dragStart = dragStart,
            dragEnd = dragEnd,
            onGridPositioned = { top, height ->
                gridContainerTopPx = top
                gridContainerHeightPx = height
            }
        )

        // Floating context menu
        Box(modifier = Modifier.offset(menuOffset.x, menuOffset.y)) {
            BackgroundContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                appState = appState
            )
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun FileLayout(
    appState: FileExplorerState,
    gridState: LazyGridState,
    itemPositions: MutableMap<UniversalFile, Rect>,
    containerCoordinates: LayoutCoordinates?,
    mousePosition: () -> Offset?,
    focusRequester: FocusRequester,
    dragStart: Offset?,
    dragEnd: Offset?,
    onGridPositioned: (Float, Float) -> Unit
) {
    val viewMode = appState.activeViewMode

    Column {
        var headerHeightPx by remember { mutableFloatStateOf(0f) }

        if (viewMode == ViewMode.DETAILS) {
            Box(modifier = Modifier.onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }) {
                DetailsHeader(appState = appState)
            }
        } else {
            headerHeightPx = 0f
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .onGloballyPositioned { coords ->
                    onGridPositioned(coords.positionInRoot().y, coords.size.height.toFloat())
                }
        ) {
            FileGrid(
                appState = appState,
                gridState = gridState,
                itemPositions = itemPositions,
                containerCoordinates = containerCoordinates,
                mousePosition = mousePosition,
                focusRequester = focusRequester,
                dragActive = dragStart != null
            )

            MarqueeRenderer(
                dragStart = dragStart?.let { Offset(it.x, it.y - headerHeightPx) },
                dragEnd = dragEnd?.let { Offset(it.x, it.y - headerHeightPx) }
            )
        }
    }
}

@Composable
private fun FileGrid(
    appState: FileExplorerState,
    gridState: LazyGridState,
    itemPositions: MutableMap<UniversalFile, Rect>,
    containerCoordinates: LayoutCoordinates?,
    mousePosition: () -> Offset?,
    focusRequester: FocusRequester,
    dragActive: Boolean
) {
    val viewMode = appState.activeViewMode

    LazyVerticalGrid(
        state = gridState,
        columns = when (viewMode) {
            ViewMode.GRID -> GridCells.Adaptive(minSize = appState.folderConfigs.gridItemSize.dp)
            else -> GridCells.Fixed(1)
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(
            items = appState.files,
            key = { it.absolutePath },
            contentType = { file ->
                when {
                    file.isDirectory -> "folder"
                    file.isArchiveEntry -> "archive_entry"
                    else -> "file"
                }
            }
        ) { file ->
            val isHovered by remember(file, dragActive) {
                derivedStateOf {
                    // Suppress hover during any drag operation
                    if (appState.activeDragY.value != null || appState.isSystemDragActive.value || dragActive) {
                        false
                    } else {
                        val currentRect = itemPositions[file]
                        val pos = mousePosition()
                        pos != null && currentRect != null && currentRect.contains(pos)
                    }
                }
            }

            FileView(
                file = file,
                itemPositions = itemPositions,
                containerCoordinates = containerCoordinates,
                mousePosition = mousePosition,
                appState = appState,
                focusRequester = focusRequester,
                isHovered = isHovered
            )
        }
    }
}
