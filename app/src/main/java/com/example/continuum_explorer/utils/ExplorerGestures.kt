package com.example.continuum_explorer.utils

import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.os.Environment
import android.view.PointerIcon
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.PopUpActivity
import com.example.continuum_explorer.model.UniversalFile
import com.example.continuum_explorer.model.ViewMode
import com.example.continuum_explorer.ui.FileExplorerState
import kotlinx.coroutines.launch
import java.io.File

/**
 * Extension to check if a pointer event is a context menu trigger (Right Click).
 */
fun PointerEvent.isContextMenuTrigger(): Boolean {
    return type == PointerEventType.Press && buttons.isSecondaryPressed
}

/**
 * Centralized modifier to detect context menu triggers.
 * Supports Mouse (Right Click) and Touch (Long Press).
 */
fun Modifier.contextMenuDetector(
    enableLongPress: Boolean = true,
    aggressive: Boolean = false,
    onContextMenu: (Offset) -> Unit
): Modifier = composed {
    val currentOnContextMenu by rememberUpdatedState(onContextMenu)
    val pass = if (aggressive) PointerEventPass.Initial else PointerEventPass.Main

    this.pointerInput(enableLongPress, aggressive) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(pass = pass)
                val firstChange = event.changes.firstOrNull() ?: continue
                
                // If the event is already consumed in this pass, and we aren't being "aggressive" (intercepting), skip it.
                // This prevents the background menu from opening if an item menu was already triggered.
                if (firstChange.isConsumed && !aggressive) continue

                if (event.isContextMenuTrigger()) {
                    currentOnContextMenu(firstChange.position)
                    event.changes.forEach { it.consume() }
                } else if (enableLongPress && event.type == PointerEventType.Press && firstChange.type == PointerType.Touch) {
                    val pointerId = firstChange.id
                    val startTime = System.currentTimeMillis()
                    val timeout = viewConfiguration.longPressTimeoutMillis
                    var triggered = false
                    
                    // Manual long press detection to support the specific 'pass' and prevent interference
                    // from child components that might consume the event in the Main pass.
                    while (true) {
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed >= timeout) {
                            triggered = true
                            break
                        }
                        
                        val nextEvent = withTimeoutOrNull(timeout - elapsed) {
                            awaitPointerEvent(pass = pass)
                        }
                        if (nextEvent == null) {
                            triggered = true
                            break
                        }
                        
                        val change = nextEvent.changes.find { it.id == pointerId }
                        // If the change is null, finger was lifted.
                        // If it's no longer pressed, gesture ended.
                        // If it's consumed AND we aren't aggressive, someone else handled it.
                        // If it moved too far, cancel.
                        if (change == null || !change.pressed || (!aggressive && change.isConsumed) ||
                            (change.position - firstChange.position).getDistance() > viewConfiguration.touchSlop) {
                            break
                        }
                    }
                    
                    if (triggered) {
                        currentOnContextMenu(firstChange.position)
                        // Consume the entire gesture to prevent child components (like navigation) 
                        // from reacting to the release event.
                        var e = currentEvent
                        while (true) {
                            e.changes.forEach { if (it.id == pointerId) it.consume() }
                            if (!e.changes.any { it.pressed }) break
                            e = awaitPointerEvent(pass = pass)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Handles gestures for the main file list container.
 */
fun Modifier.containerGestures(
    selectionManager: SelectionManager,
    focusRequester: FocusRequester,
    viewMode: ViewMode,
    gridItemSize: Int,
    onZoom: (Float) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    mousePosition: (Offset?) -> Unit,
    appState: FileExplorerState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
): Modifier = composed {
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sidebarWidth = if (isLandscape) 240 else 0

    val columnCount = when (viewMode) {
        ViewMode.GRID -> {
            val totalPadding = 64
            val availableWidth = configuration.screenWidthDp - sidebarWidth - totalPadding
            (availableWidth / gridItemSize).coerceAtLeast(1)
        }
        else -> 1
    }

    this
        .focusRequester(focusRequester)
        .focusProperties {
            left = FocusRequester.Cancel
            right = FocusRequester.Cancel
            up = FocusRequester.Cancel
            down = FocusRequester.Cancel
        }
        .focusable()
        .pointerInput(onZoom) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                        val delta = event.changes.first().scrollDelta.y
                        val zoomFactor = if (delta < 0) 1.1f else 0.9f
                        onZoom(zoomFactor)
                    }
                }
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val firstChange = event.changes.firstOrNull() ?: continue
                    if (firstChange.type == PointerType.Mouse) {
                        if (event.type == PointerEventType.Move || event.type == PointerEventType.Enter) {
                            mousePosition(firstChange.position)
                        } else if (event.type == PointerEventType.Exit) {
                            mousePosition(null)
                        }
                    }
                }
            }
        }
        .pointerInput(onZoom) {
            var zoomAccumulator = 1f
            detectTransformGestures { _, _, zoom, _ ->
                zoomAccumulator *= zoom
                if (zoomAccumulator > 1.1f) {
                    onZoom(1.1f)
                    zoomAccumulator = 1f
                } else if (zoomAccumulator < 0.9f) {
                    onZoom(0.9f)
                    zoomAccumulator = 1f
                }
            }
        }
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                val shift = keyEvent.isShiftPressed
                val ctrl = keyEvent.isCtrlPressed

                when (keyEvent.key) {
                    Key.DirectionUp -> {
                        selectionManager.moveSelection(-columnCount, shift, ctrl); true
                    }

                    Key.DirectionDown -> {
                        selectionManager.moveSelection(columnCount, shift, ctrl); true
                    }


                    Key.DirectionLeft -> {
                        if (viewMode == ViewMode.GRID) {
                            selectionManager.moveSelection(-1, shift, ctrl); true
                        } else false
                    }

                    Key.DirectionRight -> {
                        if (viewMode == ViewMode.GRID) {
                            selectionManager.moveSelection(1, shift, ctrl); true
                        } else false
                    }


                    Key.PageUp -> {
                        // 1. Get the number of items currently on screen
                        val pageSize = gridState.layoutInfo.visibleItemsInfo.size
                        // 2. Move selection back by one full page
                        selectionManager.moveSelection(-pageSize, shift, ctrl)
                        true
                    }

                    Key.PageDown -> {
                        // 1. Get the number of items currently on screen
                        val pageSize = gridState.layoutInfo.visibleItemsInfo.size
                        // 2. Move selection forward by one full page
                        selectionManager.moveSelection(pageSize, shift, ctrl)
                        true
                    }

                    Key.MoveHome -> {
                        // 1. Find where we are currently (the lead item)
                        val currentIndex = selectionManager.leadItem?.let { appState.files.indexOf(it) } ?: 0
                        // 2. To get to index 0, move back by exactly the current index
                        selectionManager.moveSelection(-currentIndex, shift, ctrl)
                        true
                    }

                    Key.MoveEnd -> {
                        // 1. Find where we are currently
                        val currentIndex = selectionManager.leadItem?.let { appState.files.indexOf(it) } ?: 0
                        // 2. Find the index of the very last file
                        val lastIndex = (appState.files.size - 1).coerceAtLeast(0)
                        // 3. Move forward by the difference
                        selectionManager.moveSelection(lastIndex - currentIndex, shift, ctrl)
                        true
                    }

                    Key.Enter, Key.NumPadEnter -> {
                        if (ctrl) {
                            appState.openInNewTab(selectionManager.selectedItems.toList())
                        } else if (shift) {
                            appState.openInNewWindow(selectionManager.selectedItems.toList())
                        } else {
                            selectionManager.selectedItems.forEach { appState.open(it) }
                        }
                        true
                    }

                    Key.A -> if (ctrl) {
                        selectionManager.selectAll(); true
                    } else false

                    Key.Backspace -> {
                        appState.goBack(); true
                    }

                    Key.Delete -> {
                        appState.deleteSelection(forcePermanent = shift); true
                    }

                    Key.F2 -> {
                        appState.renameSelection(); true
                    }

                    Key.F5 -> {
                        appState.refresh(); true
                    }

                    Key.N -> if (ctrl) {
                        appState.openInNewWindow(emptyList()); true
                    } else false

                    Key.W -> if (ctrl) {
                        (context as? android.app.Activity)?.finish(); true
                    } else false

                    Key.C -> if (ctrl) {
                        appState.copySelection(); focusRequester.requestFocus(); true
                    } else false

                    Key.X -> if (ctrl) {
                        appState.cutSelection(); focusRequester.requestFocus(); true
                    } else false

                    Key.V -> if (ctrl) {
                        appState.paste(); focusRequester.requestFocus(); true
                    } else false

                    Key.Z -> if (ctrl) {
                        appState.undo(); focusRequester.requestFocus(); true
                    } else false

                    Key.Y -> if (ctrl) {
                        appState.redo(); focusRequester.requestFocus(); true
                    } else false

                    Key.Slash -> if (ctrl) {
                        FileOperationsManager.showShortcuts()
                        val intent = Intent(context, PopUpActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        true
                    } else false

                    else -> false
                }
            } else false
        }
        .pointerInput(selectionManager) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                if (down.type == PointerType.Mouse && currentEvent.buttons.isPrimaryPressed) {
                    var change = down
                    var dragStarted = false
                    val dragThreshold = 10f
                    while (true) {
                        val event = awaitPointerEvent()
                        val newChange = event.changes.firstOrNull { it.id == change.id }
                        if (newChange == null || !newChange.pressed) break
                        val totalDistance = (newChange.position - down.position).getDistance()
                        if (!dragStarted && totalDistance > dragThreshold) {
                            dragStarted = true
                            onDragStart(down.position)
                        }
                        if (dragStarted) {
                            onDrag(newChange.position)
                            newChange.consume()
                        }
                        change = newChange
                    }
                    onDragEnd()
                    focusRequester.requestFocus()
                }
            }
        }
        .pointerInput(selectionManager) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val isMouse = event.changes.any { it.type == PointerType.Mouse }
                    val wasHandled = event.changes.any { it.isConsumed }
                    if (isMouse && event.type == PointerEventType.Press && !wasHandled && event.buttons.isPrimaryPressed) {
                        selectionManager.clear(true)
                        focusRequester.requestFocus()
                    }
                }
            }
        }
}

/**
 * Handles gestures for individual file items.
 */
fun Modifier.itemGestures(
    file: UniversalFile,
    selectionManager: SelectionManager,
    focusRequester: FocusRequester,
    appState: FileExplorerState
): Modifier = composed {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var isPrimaryClick by remember { mutableStateOf(false) }

    this.pointerInput(file, selectionManager) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val isMouse = event.changes.any { it.type == PointerType.Mouse }
                val isTouch = event.changes.any { it.type == PointerType.Touch }

                if (isTouch && event.type == PointerEventType.Press) {
                    val pointerId = event.changes[0].id
                    val longPress = awaitLongPressOrCancellation(pointerId)
                    if (longPress != null && !selectionManager.isInSelectionMode()) {
                        selectionManager.touchToggle(file)
                        longPress.consume()
                    } else {
                        val up = currentEvent.changes.find { it.id == pointerId }
                        if (up != null && !up.pressed && !up.isConsumed) {
                            if (selectionManager.isInSelectionMode()) selectionManager.touchToggle(file) else appState.open(file)
                            up.consume()
                        }
                    }
                }

                if (isMouse) {
                    val isShift = event.keyboardModifiers.isShiftPressed
                    val isCtrl = event.keyboardModifiers.isCtrlPressed

                    if (event.type == PointerEventType.Press) {
                        val isPrimary = event.buttons.isPrimaryPressed
                        val isTertiary = event.buttons.isTertiaryPressed
                        val currentTime = System.currentTimeMillis()

                        isPrimaryClick = isPrimary

                        if (isPrimary) {
                            focusRequester.requestFocus()
                            if (currentTime - lastClickTime < 300) {
                                appState.open(file)
                            } else {
                                // Smart Selection for dragging:
                                // Don't clear selection on PRESS if item is already selected.
                                // This allows the drag gesture to capture the whole selected set.
                                val isAlreadySelected = selectionManager.isSelected(file)
                                if (isShift || isCtrl || !isAlreadySelected) {
                                    selectionManager.handleRowClick(file, isShift, isCtrl)
                                }
                            }
                            lastClickTime = currentTime
                            // CONSUME the press to prevent the background clearing logic from running.
                            event.changes.forEach { it.consume() }
                        } else if (isTertiary && (file.isDirectory || ZipUtils.isArchive(file))) {
                            if (isShift) appState.openInNewWindow(listOf(file)) else appState.openInNewTab(listOf(file))
                            event.changes.forEach { it.consume() }
                        }
                    } else if (event.type == PointerEventType.Release) {
                        // If we release the mouse and NO DRAG occurred (no consumption)
                        // and it was a simple click on an existing group, NOW we select just this one.
                        val wasConsumed = event.changes.any { it.isConsumed }
                        if (isPrimaryClick && !wasConsumed && !isShift && !isCtrl && selectionManager.isSelected(file)) {
                            selectionManager.selectSingle(file)
                        }
                        isPrimaryClick = false
                    }
                }
            }
        }
    }
}

/**
 * Modifier that enables dragging files to other apps.
 * This version is restricted to Mouse input only.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.fileDragSource(
    file: UniversalFile,
    selectionManager: SelectionManager,
    appState: FileExplorerState
): Modifier = composed {
    val context = LocalContext.current
    // Ensures we only start the transfer once per drag session
    var hasStartedTransfer by remember { mutableStateOf(false) }

    // variable to track the state at the start of the touch
    var wasAlreadySelectedAtPress by remember { mutableStateOf(false) }

    // prevent dragging if the first long-press just selected the file
    var canDragThisSession by remember { mutableStateOf(false) }


    this

        .pointerInput(file) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial) // Check BEFORE selection logic
                    if (event.type == PointerEventType.Press) {
                        // Capture the state at the exact moment of impact
                        wasAlreadySelectedAtPress = selectionManager.isSelected(file)
                        // Safety: Reset the gate every time a new finger touches
                        canDragThisSession = false
                    }
                }
            }
        }

        .dragAndDropSource(block = {

        if (appState.isMouseInteraction) {

            detectDragGestures(
                onDragStart = { _ ->
                    hasStartedTransfer = false
                    prepareInternalDragState(file, selectionManager, appState)
                },
                onDrag = { _, _ ->
                    // Once the user moves the mouse, we try to start the system drag operation
                    if (!hasStartedTransfer) {
                        val transferData =
                            createDragTransferData(context, selectionManager.selectedItems)
                        if (transferData != null) {
                            hasStartedTransfer = true
                            startTransfer(transferData)
                        }
                    }
                },
                onDragEnd = { hasStartedTransfer = false },
                onDragCancel = { hasStartedTransfer = false }
            )
        } else {

            detectDragGesturesAfterLongPress(
                onDragStart = { _ ->
                    // 3. ONLY allow drag if it was already selected WHEN PRESSED.
                    // If the long-press JUST selected it now, this will be false.
                    if (wasAlreadySelectedAtPress) {
                        canDragThisSession = true
                        hasStartedTransfer = false
                        prepareInternalDragState(file, selectionManager, appState)
                    } else {
                        canDragThisSession = false
                    }
                },
                onDrag = { _, _ ->
                    // 4. Check the gate before starting system transfer
                    if (canDragThisSession && !hasStartedTransfer) {
                        val transferData = createDragTransferData(context, selectionManager.selectedItems)
                        if (transferData != null) {
                            hasStartedTransfer = true
                            startTransfer(transferData)
                        }
                    }
                },
                onDragEnd = {
                    hasStartedTransfer = false
                    canDragThisSession = false
                },
                onDragCancel = {
                    hasStartedTransfer = false
                    canDragThisSession = false
                }
            )

        }


    })
}

/**
 * Prepares the file selection and internal state before a drag begins.
 */
private fun prepareInternalDragState(
    file: UniversalFile,
    selectionManager: SelectionManager,
    appState: FileExplorerState
) {
    // 1. If the user grabs a file that isn't selected yet, select it first
    if (!selectionManager.isSelected(file)) {
        selectionManager.handleRowClick(file, isShiftPressed = false, isCtrlPressed = false)
    }

    // 2. Save the list of files being dragged for internal "Move" operations (using Shift)
    PendingCut.files = selectionManager.selectedItems.toList()
    PendingCut.isActive = appState.isShiftPressed
}

/**
 * Packages the selected files into a format that the Android system understands for dragging.
 */
private fun createDragTransferData(
    context: android.content.Context,
    selectedItems: Set<UniversalFile>
): DragAndDropTransferData? {
    // Convert our internal file objects into System Uris (links the system can use)
    val uris = selectedItems.mapNotNull { getUriForUniversalFile(context, it) }
    if (uris.isEmpty()) return null

    // Create the ClipData which holds the list of files
    val clipData = ClipData.newUri(context.contentResolver, "Files", uris[0])
    for (i in 1 until uris.size) {
        clipData.addItem(ClipData.Item(uris[i]))
    }

    // Wrap it in TransferData with global permissions so other apps can read the files
    return DragAndDropTransferData(
        clipData = clipData,
        flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
    )
}

/**
 * Reusable logic for dropping files into a specific destination.
 * If destPath and destSafUri are null, it uses the current directory from appState.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.fileDropTarget(
    appState: FileExplorerState,
    destPath: File? = null,
    destSafUri: android.net.Uri? = null
): Modifier = composed {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val target = remember(appState, destPath, destSafUri) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val androidEvent = event.toAndroidDragEvent()
                val clipData = androidEvent.clipData
                if (clipData != null) {
                    // Determine actual destination: 
                    // If target is explicitly provided, only use that and ignore defaults
                    val isTargeted = destPath != null || destSafUri != null
                    val actualPath = if (isTargeted) destPath else appState.currentPath
                    val actualSafUri = if (isTargeted) destSafUri else appState.currentSafUri

                    val trashPath = File(Environment.getExternalStorageDirectory(), ".Trash").absolutePath
                    val isTrash = actualPath?.absolutePath == trashPath

                    scope.launch {
                        if (isTrash && PendingCut.files.isNotEmpty()) {
                            // OPTIMIZATION: Use direct Move to Trash logic for internal drags
                            moveToRecycleBin(context, PendingCut.files)
                            appState.refresh()
                            GlobalEvents.triggerRefresh()
                            return@launch
                        }

                        FileOperationsManager.start()
                        val intent = Intent(context, PopUpActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)

                        val isMove = (PendingCut.files.isNotEmpty() && appState.isShiftPressed)
                        
                        // Ensure PendingCut.isActive matches the determined operation mode
                        if (PendingCut.files.isNotEmpty()) {
                            PendingCut.isActive = isMove
                        }
                        
                        val pastedNames = pasteFromClipboard(
                            context = context,
                            currentPath = actualPath,
                            currentSafUri = actualSafUri,
                            preloadedClipData = clipData
                        )
                        appState.refresh()
                        
                        // If it was a Move operation, notify other windows (like the source) to refresh
                        if (isMove) {
                            GlobalEvents.triggerRefresh()
                        }
                        
                        // Optionally select the newly dropped files if we are currently viewing the destination
                        if (pastedNames.isNotEmpty() && actualPath == appState.currentPath && actualSafUri == appState.currentSafUri) {
                            val pastedFiles = appState.files.filter { pastedNames.contains(it.name) }
                            if (pastedFiles.isNotEmpty()) {
                                appState.selectionManager.clear()
                                pastedFiles.forEach { appState.selectionManager.select(it) }
                            }
                        }
                    }
                    return true
                }
                return false
            }
        }
    }

    this.dragAndDropTarget(
        shouldStartDragAndDrop = { _ ->
            // Always accept drag sessions to avoid strict mime-type filtering issues
            true
        },
        target = target
    )
}


@Composable
fun VerticalResizeHandle(
    onResize: (Float) -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center
) {
    val context = LocalContext.current
    val resizeIcon = remember(context) {
        androidx.compose.ui.input.pointer.PointerIcon(
            PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
        )
    }

    Box(
        modifier = modifier
            .width(16.dp)
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
        contentAlignment = contentAlignment
    ) {
        VerticalDivider(
            modifier = Modifier
                .width(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * Tracks the bounding box of a file item.
 */
fun Modifier.trackPosition(
    item: UniversalFile,
    itemPositions: MutableMap<UniversalFile, Rect>,
    containerCoordinates: LayoutCoordinates?
): Modifier = this.onGloballyPositioned { coordinates ->
    if (containerCoordinates != null && containerCoordinates.isAttached) {
        itemPositions[item] = containerCoordinates.localBoundingBoxOf(coordinates)
    }
}
