package com.example.continuum_explorer.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.model.UniversalFile
import com.example.continuum_explorer.model.ViewMode
import com.example.continuum_explorer.utils.MarqueeAutoScroller
import com.example.continuum_explorer.utils.MarqueeRenderer
import com.example.continuum_explorer.utils.containerGestures
import com.example.continuum_explorer.utils.contextMenuDetector
import com.example.continuum_explorer.utils.updateMarqueeSelection
import kotlinx.coroutines.delay

/**
 * The primary view for displaying files. 
 * Handles layout (Grid vs List), selection marquee, and auto-scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileContent(
    appState: FileExplorerState
) {
    val selectionManager = appState.selectionManager
    val focusRequester = remember { FocusRequester() }

    // Request focus on launch so keyboard shortcuts work immediately
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Tracks the bounding box of every visible file item to support marquee selection
    val itemPositions = remember { mutableStateMapOf<UniversalFile, Rect>() }

    // Clear positions when selection manager changes to ensure fresh tracking
    LaunchedEffect(selectionManager) {
        itemPositions.clear()
    }

    // Marquee (drag-to-select) state
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
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


    /**
     * Re-calculates which items are inside the marquee rectangle.
     */
    fun doMarqueeUpdate(start: Offset?, end: Offset?) {
        updateMarqueeSelection(start, end, itemPositions, selectionManager)
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
        onSelectionChange = { start, end -> doMarqueeUpdate(start, end) }
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
        .onGloballyPositioned { containerCoordinates = it }
        .containerGestures(
            selectionManager = selectionManager,
            focusRequester = focusRequester,
            viewMode = appState.activeViewMode,
            gridItemSize = appState.folderConfigs.gridItemSize,
            onZoom = { factor ->
                val newSize = (appState.folderConfigs.gridItemSize * factor).toInt()
                // Call the new function that saves the size to memory
                appState.folderConfigs.updateGridSize(newSize.coerceIn(60, 300), appState.getCurrentStorageKey())
            },
            onDragStart = {
                dragStart = it
                selectionManager.clear(true)
            },
            onDrag = { offset ->
                dragEnd = offset
                doMarqueeUpdate(dragStart, dragEnd)
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
            // Header for Details mode providing column sorting and resizing
            if (appState.activeViewMode == ViewMode.DETAILS) {
                DetailsHeader(appState = appState)
            }

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
                        appState = appState,
                        focusRequester = focusRequester
                    )
                }
            }
        }

        // The visual selection rectangle
        MarqueeRenderer(
            dragStart = dragStart,
            dragEnd = dragEnd,
            containerCoordinates = containerCoordinates
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