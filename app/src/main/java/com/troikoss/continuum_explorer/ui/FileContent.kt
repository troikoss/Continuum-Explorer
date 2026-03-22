package com.troikoss.continuum_explorer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.model.ViewMode
import com.troikoss.continuum_explorer.ui.components.BackgroundContextMenu
import com.troikoss.continuum_explorer.ui.components.DetailsHeader
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.MarqueeAutoScroller
import com.troikoss.continuum_explorer.utils.MarqueeRenderer
import com.troikoss.continuum_explorer.utils.containerGestures
import com.troikoss.continuum_explorer.utils.contextMenuDetector
import com.troikoss.continuum_explorer.utils.rememberMarquee
import kotlinx.coroutines.delay

/**
 * The primary view for displaying files.
 * Handles layout (Grid vs List), selection marquee, and auto-scrolling.
 */
@Composable
fun FileContent(
    appState: FileExplorerState
) {
    val selectionManager = appState.selectionManager
    val focusRequester = remember { FocusRequester() }

    // Request focus logic:
    // We only take focus if the search UI is NOT active.
    // This prevents the file list from stealing focus while the user is typing or searching.
    LaunchedEffect(appState.isSearchUIActive) {
        if (!appState.isSearchUIActive) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // PERFORMANCE OPTIMIZATION: We use a regular HashMap instead of mutableStateMapOf.
    // This prevents the entire file list from recomposing on every single frame of a scroll.
    // The hover and marquee logic still work because they read from this map when other 
    // states (like mouse position or drag offsets) change.
    val itemPositions = remember { HashMap<UniversalFile, Rect>() }

    // Clear positions when selection manager changes to ensure fresh tracking
    LaunchedEffect(selectionManager) {
        itemPositions.clear()
    }

    // Marquee (drag-to-select) state
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    val marquee = rememberMarquee()

    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var mousePosition by remember { mutableStateOf<Offset?>(null) }

    // Context menu state
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero)}

    // We recreate the grid state only when the files for a new path are actually loaded.
    // Basing the key on loadedPathKey instead of currentPath prevents the scroll position
    // from resetting (jumping to top) while the old files are still being displayed during loading.
    val gridState = key(appState.loadedPathKey) {
        rememberLazyGridState(
            initialFirstVisibleItemIndex = appState.scrollToItemIndex ?: 0
        )
    }

    // Clear the scroll target after we've used it for initialization
    LaunchedEffect(gridState) {
        if (appState.scrollToItemIndex != null) {
            appState.onScrollToItemCompleted()
        }
    }

    val density = LocalDensity.current
    val viewMode = appState.activeViewMode

    val columnCount = remember (
        appState.activeViewMode,
        containerCoordinates?.size?.width,
        appState.folderConfigs.gridItemSize,
        density
    ){
        val width = containerCoordinates?.size?.width ?: 0
        if (viewMode == ViewMode.GRID && width > 0) {
            // Calculate usable width by subtracting horizontal padding:
            // 1. Modifier padding (32dp left + 32dp right = 64dp)
            // 2. Content padding (8dp left + 8dp right = 16dp)
            // Total = 80dp

            val gridItemSize = appState.folderConfigs.gridItemSize

            val totalPaddingPx = with(density) { 80.dp.toPx() }
            val gridItemSizePx = with(density) { gridItemSize.dp.toPx() }
            val availableGridWidthPx = (width - totalPaddingPx).coerceAtLeast(0f)
            (availableGridWidthPx / gridItemSizePx).toInt().coerceAtLeast(1)
        }  else 1
    }

    // Auto-Scroll Logic: Keeps the focused (lead) item visible when using arrow keys
    LaunchedEffect(selectionManager.leadItem) {
        val leadFile = selectionManager.leadItem
        if (leadFile != null) {
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
    }

    // Handles scrolling the view if the user drags the marquee selection off-screen
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
                onSelectionChange = { selectedFiles ->
                    selectionManager.updateSelectionFromDrag(selectedFiles)
                }
            )
        }
    )

    // Debounced Loading state: only show spinner if loading takes more than 400ms
    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(appState.isLoading) {
        if (appState.isLoading) {
            delay(400) // Wait 400ms before showing spinner
            showSpinner = true
        } else {
            showSpinner = false
        }
    }


    Box(modifier = Modifier
        .fillMaxSize()
        .clipToBounds()
        .onGloballyPositioned { containerCoordinates = it }
        .containerGestures(
            selectionManager = selectionManager,
            focusRequester = focusRequester,
            viewMode = appState.activeViewMode,
            columns = columnCount,
            onZoom = { factor ->
                val newSize = (appState.folderConfigs.gridItemSize * factor).toInt()
                // Call the new function that saves the size to memory
                appState.folderConfigs.updateGridSize(
                    newSize.coerceIn(60, 300),
                    appState.getCurrentStorageKey()
                )
            },
            onDragStart = { offset ->
                dragStart = offset

                // Reset the math variables for the new drag
                marquee.reset()

                selectionManager.clear(true)
            },
            onDrag = { offset ->
                dragEnd = offset

                // Run the math and update the selection
                marquee.updateSelection(
                    start = dragStart,
                    end = dragEnd,
                    itemPositions = itemPositions,
                    allFiles = appState.files,
                    columnCount = columnCount,
                    onSelectionChange = { selectedFiles ->
                        selectionManager.updateSelectionFromDrag(selectedFiles)
                    }
                )
            },
            onDragEnd = { dragStart = null; dragEnd = null },
            mousePosition = { position -> mousePosition = position },
            appState = appState,
            gridState = gridState
        )
        .contextMenuDetector(enableLongPress = false, aggressive = false) { offset ->
            menuOffset = with(density) {
                DpOffset(offset.x.toDp(), offset.y.toDp())
            }
            showMenu = true
        }
    ) {
        if (showSpinner) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column {
            // 1. Track the exact height of the header in pixels
            var headerHeightPx by remember { mutableFloatStateOf(0f) }

            if (appState.activeViewMode == ViewMode.DETAILS) {
                // Wrap the header in a Box to silently measure its height
                Box(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        headerHeightPx = coordinates.size.height.toFloat()
                    }
                ) {
                    DetailsHeader(appState = appState)
                }
            } else {
                headerHeightPx = 0f
            }

            // 2. Wrap the Grid and Renderer in a Box that fills the rest of the screen
            Box(
                modifier = Modifier
                    .weight(1f) // Takes up all space BELOW the header
                    .clipToBounds() // CUTS OFF the blue box so it doesn't draw over the header!
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = when (appState.activeViewMode) {
                        ViewMode.GRID  -> GridCells.Adaptive(minSize = appState.folderConfigs.gridItemSize.dp)
                        else -> GridCells.Fixed(1)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(
                        items = appState.files,
                        key = { it.absolutePath }
                    ) { file ->
                        FileView(
                            file = file,
                            itemPositions = itemPositions,
                            containerCoordinates = containerCoordinates,
                            mousePosition = mousePosition,
                            scrollOffset = { gridState.firstVisibleItemScrollOffset },
                            appState = appState,
                            focusRequester = focusRequester
                        )
                    }
                }

                // 3. Shift the visual drawing coordinates up by the header height!
                MarqueeRenderer(
                    dragStart = dragStart?.let { Offset(it.x, it.y - headerHeightPx) },
                    dragEnd = dragEnd?.let { Offset(it.x, it.y - headerHeightPx) }
                )
            }
        }

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