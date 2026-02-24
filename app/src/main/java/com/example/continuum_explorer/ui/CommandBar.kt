package com.example.continuum_explorer.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.model.FileColumnType
import com.example.continuum_explorer.model.ScreenSize
import com.example.continuum_explorer.model.ViewMode
import com.example.continuum_explorer.utils.UndoManager
import com.example.continuum_explorer.utils.ZipUtils
import com.example.continuum_explorer.utils.openWith
import com.example.continuum_explorer.utils.shareFiles
import kotlinx.coroutines.launch

/**
 * A horizontal toolbar containing file operations, sorting, and view options.
 */
@Composable
fun CommandBar(
    appState: FileExplorerState
) {
    val selectionManager = appState.selectionManager
    val isSelectionMode = selectionManager.isInSelectionMode()
    val isInRecycleBin = appState.isInRecycleBin
    val canUndo by UndoManager.canUndo
    val canRedo by UndoManager.canRedo

    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    var hasClipboardItems by remember { mutableStateOf(clipboard.hasPrimaryClip()) }

    // Update clipboard status listener
    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            hasClipboardItems = clipboard.hasPrimaryClip()
        }
        clipboard.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboard.removePrimaryClipChangedListener(listener)
        }
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .height(40.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.first().scrollDelta
                                coroutineScope.launch {
                                    // Map vertical scroll (delta.y) to horizontal scroll
                                    scrollState.scrollBy(delta.y * 60f)
                                }
                            }
                        }
                    }
                }
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isInRecycleBin) {
                // Recycle Bin specific UI
                if (isSelectionMode) {
                    CommandButton(
                        text = "Restore",
                        icon = Icons.Default.Restore,
                        onClick = { isTouch -> 
                            appState.restoreSelection()
                            if (isTouch) selectionManager.clear()
                        }
                    )

                    CommandButton(
                        text = "Delete Permanently",
                        icon = Icons.Default.Delete,
                        onClick = { isTouch -> 
                            appState.deleteSelection(forcePermanent = true)
                            if (isTouch) {selectionManager.clear()} 
                        },
                    )

                    CommandButton(
                        text = "Properties",
                        icon = Icons.Default.Info,
                        onClick = { isTouch -> if (isTouch) {selectionManager.clear()} },
                    )
                } else {
                    // Sort Menu
                    val sortIcon = when (appState.folderConfigs.sortParams.columnType) {
                        FileColumnType.NAME -> Icons.Default.TextFormat
                        FileColumnType.DATE -> Icons.Default.DateRange
                        FileColumnType.SIZE -> Icons.AutoMirrored.Filled.List
                    }

                    CommandDropDown(text = "Sort", icon = sortIcon) { onDismiss ->
                        DropdownMenuItem(
                            text = { Text("By Name") },
                            leadingIcon = { Icon(Icons.Default.TextFormat, null) },
                            trailingIcon = { appState.folderConfigs.SortArrow(FileColumnType.NAME) },
                            onClick = { appState.folderConfigs.toggleSort(FileColumnType.NAME, appState.getCurrentStorageKey()) { appState.refresh() }; onDismiss() }
                        )
                        DropdownMenuItem(
                            text = { Text("By Date") },
                            leadingIcon = { Icon(Icons.Default.DateRange, null) },
                            trailingIcon = { appState.folderConfigs.SortArrow(FileColumnType.DATE) },
                            onClick = { appState.folderConfigs.toggleSort(FileColumnType.DATE, appState.getCurrentStorageKey()) { appState.refresh() }; onDismiss() }
                        )
                        DropdownMenuItem(
                            text = { Text("By Size") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                            trailingIcon = { appState.folderConfigs.SortArrow(FileColumnType.SIZE) },
                            onClick = { appState.folderConfigs.toggleSort(FileColumnType.SIZE, appState.getCurrentStorageKey()) { appState.refresh() }; onDismiss() }
                        )
                    }

                    // View Menu
                    val viewIcon = when (appState.activeViewMode) {
                        ViewMode.DETAILS -> Icons.AutoMirrored.Filled.ListAlt
                        ViewMode.CONTENT -> Icons.AutoMirrored.Filled.List
                        ViewMode.GRID -> Icons.Default.ViewModule
                    }

                    CommandDropDown(text = "View", icon = viewIcon) { onDismiss ->
                        if (appState.getScreenSize() != ScreenSize.SMALL) {
                            DropdownMenuItem(
                                text = { Text("Details") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ListAlt, null) },
                                trailingIcon = { if (appState.activeViewMode == ViewMode.DETAILS) { Icon(Icons.Default.Done, null) }},
                                onClick = { appState.folderConfigs.updateViewMode(ViewMode.DETAILS, appState.getCurrentStorageKey()); onDismiss() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Content") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                            trailingIcon = { if (appState.activeViewMode == ViewMode.CONTENT) { Icon(Icons.Default.Done, null) }},
                            onClick = { appState.folderConfigs.updateViewMode(ViewMode.CONTENT, appState.getCurrentStorageKey()); onDismiss() }
                        )
                        DropdownMenuItem(
                            text = { Text("Grid") },
                            leadingIcon = { Icon(Icons.Default.ViewModule, null) },
                            trailingIcon = { if (appState.activeViewMode == ViewMode.GRID) { Icon(Icons.Default.Done, null) }},
                            onClick = { appState.folderConfigs.updateViewMode(ViewMode.GRID, appState.getCurrentStorageKey()); onDismiss() }
                        )
                    }

                    VerticalDivider()

                    CommandButton(
                        text = "Empty Recycle Bin",
                        icon = Icons.Default.DeleteForever,
                        onClick = { appState.emptyRecycleBin() },
                    )
                    
                    if (canUndo) {
                        CommandButton(
                            text = "Undo",
                            icon = Icons.AutoMirrored.Filled.Undo,
                            onClick = { appState.undo() }
                        )
                    }
                    
                    if (canRedo) {
                        CommandButton(
                            text = "Redo",
                            icon = Icons.AutoMirrored.Filled.Redo,
                            onClick = { appState.redo() }
                        )
                    }
                }
            } else {
                // Normal Folder UI
                if (!isSelectionMode) {
                    // New Menu
                    CommandDropDown(text = "New", icon = Icons.Default.Add) { onDismiss ->
                        DropdownMenuItem(
                            text = { Text("New Folder") },
                            leadingIcon = { Icon(Icons.Default.Folder, null) },
                            onClick = { appState.createNewFolder(); onDismiss() })
                        DropdownMenuItem(
                            text = { Text("New File") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                            onClick = { appState.createNewFile(); onDismiss() })
                    }

                    // Sort Menu
                    val sortIcon = when (appState.folderConfigs.sortParams.columnType) {
                        FileColumnType.NAME -> Icons.Default.TextFormat
                        FileColumnType.DATE -> Icons.Default.DateRange
                        FileColumnType.SIZE -> Icons.AutoMirrored.Filled.List
                    }

                    CommandDropDown(text = "Sort", icon = sortIcon) { onDismiss ->
                        DropdownMenuItem(
                            text = { Text("By Name") },
                            leadingIcon = { Icon(Icons.Default.TextFormat, null) },
                            trailingIcon = { appState.folderConfigs.SortArrow(FileColumnType.NAME) },
                            onClick = { appState.folderConfigs.toggleSort(FileColumnType.NAME, appState.getCurrentStorageKey()) { appState.refresh() }; onDismiss() }
                        )
                        DropdownMenuItem(
                            text = { Text("By Date") },
                            leadingIcon = { Icon(Icons.Default.DateRange, null) },
                            trailingIcon = { appState.folderConfigs.SortArrow(FileColumnType.DATE) },
                            onClick = { appState.folderConfigs.toggleSort(FileColumnType.DATE, appState.getCurrentStorageKey()) { appState.refresh() }; onDismiss() }
                        )
                        DropdownMenuItem(
                            text = { Text("By Size") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                            trailingIcon = { appState.folderConfigs.SortArrow(FileColumnType.SIZE) },
                            onClick = { appState.folderConfigs.toggleSort(FileColumnType.SIZE, appState.getCurrentStorageKey()) { appState.refresh() }; onDismiss() }
                        )
                    }

                    // View Menu
                    val viewIcon = when (appState.activeViewMode) {
                        ViewMode.DETAILS -> Icons.AutoMirrored.Filled.ListAlt
                        ViewMode.CONTENT -> Icons.AutoMirrored.Filled.List
                        ViewMode.GRID -> Icons.Default.ViewModule
                    }

                    CommandDropDown(text = "View", icon = viewIcon) { onDismiss ->
                        if (appState.getScreenSize() != ScreenSize.SMALL) {
                            DropdownMenuItem(
                                text = { Text("Details") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ListAlt, null) },
                                trailingIcon = {
                                    if (appState.activeViewMode == ViewMode.DETAILS) {
                                        Icon(Icons.Default.Done, null)
                                    }
                                },
                                onClick = { appState.folderConfigs.updateViewMode(ViewMode.DETAILS, appState.getCurrentStorageKey()); onDismiss() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Content") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                            trailingIcon = {
                                if (appState.activeViewMode == ViewMode.CONTENT) {
                                    Icon(Icons.Default.Done, null)
                                }
                            },
                            onClick = { appState.folderConfigs.updateViewMode(ViewMode.CONTENT, appState.getCurrentStorageKey()); onDismiss() }
                        )
                        DropdownMenuItem(
                            text = { Text("Grid") },
                            leadingIcon = { Icon(Icons.Default.ViewModule, null) },
                            trailingIcon = {
                                if (appState.activeViewMode == ViewMode.GRID) {
                                    Icon(Icons.Default.Done, null)
                                }
                            },
                            onClick = { appState.folderConfigs.updateViewMode(ViewMode.GRID, appState.getCurrentStorageKey()); onDismiss() }
                        )
                    }

                    VerticalDivider()

                    CommandButton(
                        text = "Select All",
                        icon = Icons.Default.SelectAll,
                        onClick = { selectionManager.selectAll() },
                    )

                    if (hasClipboardItems) {
                        CommandButton(
                            text = "Paste",
                            icon = Icons.Default.ContentPaste,
                            onClick = { appState.paste() }
                        )
                    }
                    
                    if (canUndo) {
                        CommandButton(
                            text = "Undo",
                            icon = Icons.AutoMirrored.Filled.Undo,
                            onClick = { appState.undo() }
                        )
                    }
                    
                    if (canRedo) {
                        CommandButton(
                            text = "Redo",
                            icon = Icons.AutoMirrored.Filled.Redo,
                            onClick = { appState.redo() }
                        )
                    }

                } else {
                    // Selection Mode Actions
                    val selectedList = selectionManager.selectedItems.toList()
                    val hasDirectories = selectedList.any { it.isDirectory }
                    val onlyOneSelected = selectedList.size == 1
                    val hasArchive = selectedList.any { ZipUtils.isArchive(it) }

                    if (onlyOneSelected && !hasDirectories) {
                        CommandButton(
                            text = "Open With",
                            icon = Icons.Default.FolderOpen,
                            onClick = { isTouch ->
                                openWith(context, selectedList[0])
                                if (isTouch) {selectionManager.clear()}
                            },
                        )
                    }

                    if (hasDirectories || hasArchive) {
                        CommandButton(
                            text = "Open in New Tab",
                            icon = Icons.Default.Tab,
                            onClick = { isTouch -> appState.openInNewTab(selectionManager.selectedItems.toList()); if (isTouch) {selectionManager.clear()} },
                        )

                        CommandButton(
                            text = "Open in New Window",
                            icon = Icons.Default.Splitscreen,
                            onClick = { isTouch -> appState.openInNewWindow(selectionManager.selectedItems.toList()); if (isTouch) {selectionManager.clear()} },
                        )
                    }

                    if (onlyOneSelected || hasArchive || hasDirectories) {
                         VerticalDivider()
                    }


                    // Favorites logic
                    if (onlyOneSelected && selectedList.first().isDirectory && selectedList.first().fileRef != null) {
                        val path = selectedList.first().fileRef!!.absolutePath
                        val isFav = appState.appConfigs.isFavorite(path)

                        CommandButton(
                            text = if (isFav) "Remove from Favorites" else "Add to Favorites",
                            onClick = { isTouch ->
                                if (isFav) appState.appConfigs.removeFavorite(path)
                                else appState.appConfigs.addFavorite(path)
                                if (isTouch) selectionManager.clear()
                            },
                            icon = if (isFav) Icons.Default.StarOutline else Icons.Default.Star
                        )
                        VerticalDivider()
                    }


                    
                    if (hasArchive) {
                        CommandButton(
                            text = "Extract",
                            icon = Icons.Default.Unarchive,
                            onClick = { isTouch -> 
                                appState.extractSelection()
                                if (isTouch) selectionManager.clear()
                            }
                        )
                    }

                    CommandButton(
                        text = "Compress",
                        icon = Icons.Default.Archive,
                        onClick = { isTouch ->
                            appState.compressSelection()
                            if (isTouch) selectionManager.clear()
                        }
                    )

                    VerticalDivider()


                    CommandDropDown(text = "Select", icon = Icons.Default.SelectAll) { onDismiss ->

                        val allSelected = selectionManager.selectedItems.size == selectionManager.allFiles.size

                        if (!allSelected) {
                            DropdownMenuItem(
                                text = { Text("Select All") },
                                leadingIcon = { Icon(Icons.Default.Deselect, null) },
                                onClick = { selectionManager.selectAll(); onDismiss() }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Clear Selection") },
                            leadingIcon = { Icon(Icons.Default.Deselect, null) },
                            onClick = { selectionManager.clear(); onDismiss() }
                        )

                        DropdownMenuItem(
                            text = { Text("Select Inverse") },
                            leadingIcon = { Icon(Icons.Default.Deselect, null) },
                            onClick = { selectionManager.selectInverse(); onDismiss() }
                        )

                    }

                    CommandButton(
                        text = "Cut",
                        icon = Icons.Default.ContentCut,
                        onClick = { isTouch -> appState.cutSelection(); if (isTouch) {selectionManager.clear()} },
                    )
                    CommandButton(
                        text = "Copy",
                        icon = Icons.Default.CopyAll,
                        onClick = { isTouch -> appState.copySelection(); if (isTouch) {selectionManager.clear()} },
                    )
                    
                    if (hasClipboardItems) {
                        CommandButton(
                            text = "Paste",
                            icon = Icons.Default.ContentPaste,
                            onClick = { isTouch -> appState.paste(); if (isTouch) {selectionManager.clear()} }
                        )
                    }
                    
                    CommandButton(
                        text = "Rename",
                        icon = Icons.Default.DriveFileRenameOutline,
                        onClick = { isTouch -> appState.renameSelection(); if (isTouch) {selectionManager.clear()} }
                    )
                    if ( !selectionManager.selectedItems.any{it.isDirectory}  ) {
                        CommandButton(
                            text = "Share",
                            icon = Icons.Default.Share,
                            onClick = { isTouch -> shareFiles(context, selectionManager.selectedItems.toList()); if (isTouch) {selectionManager.clear()} }
                        )
                    }
                    CommandButton(
                        text = "Delete",
                        icon = Icons.Default.Delete,
                        onClick = { isTouch -> appState.deleteSelection(); if (isTouch) {selectionManager.clear()} },
                    )

                    CommandButton(
                        text = "Properties",
                        icon = Icons.Default.Info,
                        onClick = { isTouch -> if (isTouch) {selectionManager.clear()} },
                    )

                }
            }
        }
    }
}



@Composable
fun CommandDropDown(
    text: String,
    icon: ImageVector,
    menuItems: @Composable ColumnScope.(onDismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        CommandButton(
            text = text,
            icon = icon,
            onClick = { expanded = true },
            trailingIcon = Icons.Default.KeyboardArrowDown
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuItems { expanded = false }
        }
    }
}

@Composable
fun CommandButton(
    text: String,
    icon: ImageVector,
    onClick: (isTouch: Boolean) -> Unit,
    trailingIcon: ImageVector? = null
) {
    var lastInputWasTouch by remember { mutableStateOf(false) }

    InputChip(
        selected = false,
        onClick = {
            onClick(lastInputWasTouch)
        },
        label = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
        shape = SuggestionChipDefaults.shape,
        colors = InputChipDefaults.inputChipColors(),
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    lastInputWasTouch = event.changes.any { it.type == PointerType.Touch }
                }
            }
        }
    )
}
