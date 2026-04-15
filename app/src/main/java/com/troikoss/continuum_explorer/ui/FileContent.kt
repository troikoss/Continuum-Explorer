package com.troikoss.continuum_explorer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.model.FileColumnType
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.model.ViewMode
import com.troikoss.continuum_explorer.ui.components.BackgroundContextMenu
import com.troikoss.continuum_explorer.ui.components.DetailsHeader
import com.troikoss.continuum_explorer.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val headerOffsetPx = gridContainerTopPx - (containerCoordinates?.positionInRoot()?.y ?: 0f)
    val horizontalPaddingPx = with(density) { if (viewMode == ViewMode.DETAILS) 16.dp.toPx() else 32.dp.toPx() }

    // Recreate grid state when the path changes to keep scroll fresh
    val gridState = key(appState.loadedPathKey) {
        rememberLazyGridState(initialFirstVisibleItemIndex = appState.scrollToItemIndex ?: 0)
    }

    // Dynamic column count based on view mode and actual grid layout
    val columnCount by remember(gridState, viewMode, containerCoordinates?.size?.width, appState.folderConfigs.gridItemSize, density) {
        derivedStateOf {
            if (viewMode == ViewMode.DETAILS || viewMode == ViewMode.CONTENT) {
                1
            } else {
                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                if (visibleItems.isNotEmpty()) {
                    // Extract exact columns generated by Compose Adaptive Grid
                    visibleItems.count { it.offset.y == visibleItems.first().offset.y }.coerceAtLeast(1)
                } else {
                    // Fallback to mathematical estimation before first layout pass
                    val width = containerCoordinates?.size?.width ?: 0
                    if (width > 0) {
                        val totalPaddingPx = with(density) { 64.dp.toPx() } // 32.dp * 2 padding
                        val gridItemSizePx = with(density) { appState.folderConfigs.gridItemSize.dp.toPx() }
                        val availableGridWidthPx = (width - totalPaddingPx).coerceAtLeast(0f)
                        (availableGridWidthPx / gridItemSizePx).toInt().coerceAtLeast(1)
                    } else 1
                }
            }
        }
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
    LaunchedEffect(appState.activeDragY.value != null) {
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
                gridState = gridState,
                allFiles = appState.files,
                columnCount = columnCount,
                xOffset = horizontalPaddingPx,
                yOffset = headerOffsetPx,
                onSelectionChange = { selectionManager.updateSelectionFromDrag(it) }
            )
        }
    )

    // Re-evaluate selection strictly when the physical layout fully settles after asynchronous autoscroll
    LaunchedEffect(dragStart, dragEnd, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        if (dragStart != null && dragEnd != null) {
            marquee.updateSelection(
                start = dragStart,
                end = dragEnd,
                gridState = gridState,
                allFiles = appState.files,
                columnCount = columnCount,
                xOffset = horizontalPaddingPx,
                yOffset = headerOffsetPx,
                onSelectionChange = { selectionManager.updateSelectionFromDrag(it) }
            )
        }
    }

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
                        gridState = gridState,
                        allFiles = appState.files,
                        columnCount = columnCount,
                        xOffset = horizontalPaddingPx,
                        yOffset = headerOffsetPx,
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
            marquee = marquee,
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
    marquee: Marquee,
    onGridPositioned: (Float, Float) -> Unit
) {
    val viewMode = appState.activeViewMode
    val hScrollState = rememberScrollState()
    val nameColumnWidth: Dp = if (viewMode == ViewMode.DETAILS)
        appState.folderConfigs.columnWidths.getOrElse(FileColumnType.NAME) { 200.dp }
    else 0.dp

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val edgeThresholdPx = with(density) { 48.dp.toPx() }
    var contentBoxSize by remember { mutableStateOf(IntSize.Zero) }
    var contentLocalMousePos by remember { mutableStateOf<Offset?>(null) }

    val showVertical by remember { derivedStateOf {
        val pos = contentLocalMousePos ?: return@derivedStateOf false
        pos.x > contentBoxSize.width - edgeThresholdPx
    } }
    val showHorizontal by remember { derivedStateOf {
        val pos = contentLocalMousePos ?: return@derivedStateOf false
        pos.y > contentBoxSize.height - edgeThresholdPx
    } }
    var isHScrollActive by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        var headerHeightPx by remember { mutableFloatStateOf(0f) }

        if (viewMode == ViewMode.DETAILS) {
            Box(modifier = Modifier
                .padding(horizontal = 16.dp)
                .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }
            ) {
                DetailsHeader(appState = appState, scrollState = hScrollState, nameColumnWidth = nameColumnWidth)
            }
        } else {
            headerHeightPx = 0f
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .onSizeChanged { contentBoxSize = it }
                .onGloballyPositioned { coords ->
                    onGridPositioned(coords.positionInRoot().y, coords.size.height.toFloat())
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Move, PointerEventType.Enter ->
                                    contentLocalMousePos = event.changes.firstOrNull()?.position
                                PointerEventType.Exit -> contentLocalMousePos = null
                                else -> {}
                            }
                        }
                    }
                }
                .pointerInput(hScrollState, viewMode) {
                    if (viewMode != ViewMode.DETAILS) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isShiftPressed) {
                                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                coroutineScope.launch { hScrollState.scrollBy(delta * 120f) }
                                coroutineScope.launch {
                                    isHScrollActive = true
                                    delay(800)
                                    isHScrollActive = false
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
        ) {
            FileGrid(
                appState = appState,
                gridState = gridState,
                itemPositions = itemPositions,
                containerCoordinates = containerCoordinates,
                mousePosition = mousePosition,
                focusRequester = focusRequester,
                dragActive = dragStart != null,
                hScrollState = hScrollState,
                nameColumnWidth = nameColumnWidth
            )

            MarqueeRenderer(
                dragStart = dragStart?.let { Offset(it.x, it.y - headerHeightPx) },
                dragEnd = dragEnd?.let { Offset(it.x, it.y - headerHeightPx) },
                marquee = marquee
            )

            VerticalScrollbar(
                gridState = gridState,
                isNearEdge = showVertical,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
            )

            if (viewMode == ViewMode.DETAILS) {
                HorizontalScrollbar(
                    scrollState = hScrollState,
                    isNearEdge = showHorizontal,
                    isRecentlyScrolled = isHScrollActive,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
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
    dragActive: Boolean,
    hScrollState: ScrollState? = null,
    nameColumnWidth: Dp = 0.dp
) {
    val viewMode = appState.activeViewMode
    val density = LocalDensity.current

    // Compute the intrinsic width of a details row (icon + spacer + name + columns + padding)
    // so hover is only triggered when the mouse is over the actual content, not blank space to the right.
    // Uses remember with no key so the State object is stable — all reads inside are state-tracked via
    // mutableStateMapOf, so any column resize triggers an automatic recompute.
    val detailsContentWidthPx = remember {
        derivedStateOf {
            if (appState.folderConfigs.viewMode != ViewMode.DETAILS) Float.MAX_VALUE
            else with(density) {
                val nameWidth = appState.folderConfigs.columnWidths.getOrElse(FileColumnType.NAME) { 200.dp }
                val columnsWidth = appState.folderConfigs.visibleColumns.fold(0.dp) { acc, col ->
                    val colWidth = appState.folderConfigs.columnWidths[col.type] ?: col.minWidth
                    acc + 1.dp + colWidth
                }
                (52.dp + nameWidth + columnsWidth).toPx()  // 52 = 8(pad) + 24(icon) + 12(spacer) + 8(pad)
            }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = when (viewMode) {
            ViewMode.GRID, ViewMode.GALLERY -> GridCells.Adaptive(minSize = appState.folderConfigs.gridItemSize.dp)
            else -> GridCells.Fixed(1)
        },
        modifier = if (viewMode == ViewMode.DETAILS) Modifier.fillMaxSize().padding(horizontal = 16.dp)
                   else Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentPadding = if (viewMode == ViewMode.DETAILS) PaddingValues(0.dp)
                         else PaddingValues(16.dp)
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
                        if (pos == null || currentRect == null || !currentRect.contains(pos)) false
                        else if (viewMode == ViewMode.DETAILS) (pos.x - currentRect.left) < detailsContentWidthPx.value
                        else true
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
                isHovered = isHovered,
                hScrollState = hScrollState,
                nameColumnWidth = nameColumnWidth
            )
        }
    }
}

private data class GridScrollMetrics(
    val viewportHeight: Float,
    val estimatedContentHeight: Float,
    val columnsCount: Int,
    val avgItemHeight: Float
)

private fun LazyGridLayoutInfo.computeScrollMetrics(): GridScrollMetrics? {
    val visibleItems = visibleItemsInfo
    if (visibleItems.isEmpty() || totalItemsCount == 0) return null
    val viewportHeight = (viewportEndOffset - viewportStartOffset).toFloat()
    if (viewportHeight == 0f) return null
    val avgItemHeight = visibleItems.sumOf { it.size.height }.toFloat() / visibleItems.size
    val columnsCount = visibleItems.count { it.offset.y == visibleItems.first().offset.y }.coerceAtLeast(1)
    val totalRows = (totalItemsCount + columnsCount - 1) / columnsCount
    return GridScrollMetrics(
        viewportHeight = viewportHeight,
        estimatedContentHeight = totalRows * avgItemHeight,
        columnsCount = columnsCount,
        avgItemHeight = avgItemHeight
    )
}

@Composable
private fun VerticalScrollbar(
    gridState: LazyGridState,
    isNearEdge: Boolean,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val isScrollable by remember(gridState) {
        derivedStateOf {
            val m = gridState.layoutInfo.computeScrollMetrics() ?: return@derivedStateOf false
            m.estimatedContentHeight > m.viewportHeight + 1f
        }
    }

    val isScrolling = gridState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if ((isNearEdge || isScrolling) && isScrollable) 1f else 0f,
        animationSpec = if ((isNearEdge || isScrolling) && isScrollable) tween(150) else tween(durationMillis = 300, delayMillis = 500),
        label = "v_scrollbar_alpha"
    )

    val thumbColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp)
            .pointerInput(gridState) {
                awaitEachGesture {
                    // Consume the down immediately so containerGestures won't start a marquee
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var prevY = down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dragDeltaY = change.position.y - prevY
                        if (dragDeltaY != 0f) {
                            change.consume()
                            val m = gridState.layoutInfo.computeScrollMetrics()
                            if (m != null && size.height > 0f) {
                                coroutineScope.launch { gridState.scrollBy(dragDeltaY * (m.estimatedContentHeight / size.height)) }
                            }
                        }
                        prevY = change.position.y
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(6.dp)
                .padding(vertical = 4.dp)
                .align(Alignment.CenterEnd)
        ) {
            if (alpha == 0f) return@Canvas
            val m = gridState.layoutInfo.computeScrollMetrics() ?: return@Canvas

            val thumbFraction = (m.viewportHeight / m.estimatedContentHeight).coerceIn(0.08f, 1f)
            val thumbHeight = size.height * thumbFraction
            val scrolledAmount = (gridState.firstVisibleItemIndex.toFloat() / m.columnsCount) * m.avgItemHeight +
                    gridState.firstVisibleItemScrollOffset
            val scrollFraction = (scrolledAmount / (m.estimatedContentHeight - m.viewportHeight)).coerceIn(0f, 1f)
            val thumbTop = (size.height - thumbHeight) * scrollFraction

            // Track
            drawRoundRect(
                color = thumbColor.copy(alpha = alpha * 0.15f),
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(size.width / 2f)
            )
            // Thumb
            drawRoundRect(
                color = thumbColor.copy(alpha = alpha * 0.55f),
                topLeft = Offset(0f, thumbTop),
                size = Size(size.width, thumbHeight),
                cornerRadius = CornerRadius(size.width / 2f)
            )
        }
    }
}

@Composable
private fun HorizontalScrollbar(
    scrollState: ScrollState,
    isNearEdge: Boolean,
    isRecentlyScrolled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isScrollable by remember { derivedStateOf { scrollState.maxValue > 0 } }

    val isScrolling = scrollState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if ((isNearEdge || isScrolling || isRecentlyScrolled) && isScrollable) 1f else 0f,
        animationSpec = if ((isNearEdge || isScrolling || isRecentlyScrolled) && isScrollable) tween(150) else tween(durationMillis = 300, delayMillis = 500),
        label = "h_scrollbar_alpha"
    )

    val thumbColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .pointerInput(scrollState) {
                awaitEachGesture {
                    // Consume the down immediately so containerGestures won't start a marquee
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var prevX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dragDeltaX = change.position.x - prevX
                        if (dragDeltaX != 0f) {
                            change.consume()
                            val maxValue = scrollState.maxValue
                            if (maxValue > 0 && size.width > 0) {
                                val contentWidth = size.width + maxValue.toFloat()
                                val scrollRatio = contentWidth / size.width
                                coroutineScope.launch { scrollState.scrollBy(dragDeltaX * scrollRatio) }
                            }
                        }
                        prevX = change.position.x
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .padding(horizontal = 4.dp)
                .align(Alignment.BottomCenter)
        ) {
            if (alpha == 0f) return@Canvas
            val maxValue = scrollState.maxValue
            if (maxValue <= 0) return@Canvas

            val contentWidth = size.width + maxValue
            val thumbFraction = (size.width / contentWidth).coerceIn(0.08f, 1f)
            val thumbWidth = size.width * thumbFraction
            val scrollFraction = (scrollState.value.toFloat() / maxValue).coerceIn(0f, 1f)
            val thumbLeft = (size.width - thumbWidth) * scrollFraction

            // Track
            drawRoundRect(
                color = thumbColor.copy(alpha = alpha * 0.15f),
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(size.height / 2f)
            )
            // Thumb
            drawRoundRect(
                color = thumbColor.copy(alpha = alpha * 0.55f),
                topLeft = Offset(thumbLeft, 0f),
                size = Size(thumbWidth, size.height),
                cornerRadius = CornerRadius(size.height / 2f)
            )
        }
    }
}
