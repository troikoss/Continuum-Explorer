package com.example.continuum_explorer.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.text.format.Formatter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.documentfile.provider.DocumentFile
import com.example.continuum_explorer.utils.SettingsManager
import com.example.continuum_explorer.utils.contextMenuDetector
import com.example.continuum_explorer.utils.fileDropTarget
import com.example.continuum_explorer.utils.toUniversal
import java.io.File
import kotlin.math.roundToInt

/**
 * Data class to hold storage information
 */
private data class StorageVolumeInfo(
    val label: String,
    val path: File?,
    val uri: Uri?,
    val totalSpace: Long,
    val freeSpace: Long,
    val icon: ImageVector,
    val id: Int
)

/**
 * A revamped navigation sidebar with section headers and organized locations.
 */
@Composable
fun NavigationPane(
    appState: FileExplorerState,
    onItemSelected: (Int) -> Unit,
    onSafItemSelected: (Uri) -> Unit,
    onAddStorageClick: () -> Unit
) {
    val context = LocalContext.current
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val isRecycleBinEnabled by SettingsManager.isRecycleBinEnabled

    // Gather all storage volumes
    val storageVolumes = remember(context) {
        val volumes = mutableListOf<StorageVolumeInfo>()
        
        // Add Internal Storage (ID 6)
        val internalRoot = Environment.getExternalStorageDirectory()
        volumes.add(
            StorageVolumeInfo(
                label = "Internal Storage",
                path = internalRoot,
                uri = null,
                totalSpace = internalRoot.totalSpace,
                freeSpace = internalRoot.usableSpace,
                icon = Icons.Default.Storage,
                id = 6
            )
        )

        // Add Removable volumes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageManager.storageVolumes.forEachIndexed { index, volume ->
                if (volume.isRemovable) {
                    val directory = volume.directory
                    if (directory != null) {
                        val description = volume.getDescription(context) ?: ""
                        val isSdCard = description.contains("SD", ignoreCase = true)

                        volumes.add(
                            StorageVolumeInfo(
                                label = if (description.isEmpty()) "External Drive" else description,
                                path = directory,
                                uri = null,
                                totalSpace = directory.totalSpace,
                                freeSpace = directory.usableSpace,
                                icon = if (isSdCard) Icons.Default.SdCard else Icons.Default.Usb,
                                id = 100 + index
                            )
                        )
                    }
                }
            }
        }
        volumes
    }

    val lazyListState = rememberLazyListState()

    // Drag-to-reorder state
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    // Background context menu state
    var showBgMenu by remember { mutableStateOf(false) }
    var bgMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current

    val handleWidthPx = with(density) { 56.dp.toPx() }

    // Calculate visible library items directly without 'remember' based on list reference
    val visibleLibraryItems = appState.appConfigs.libraryOrder.filter { id ->
        when (id) {
            "trash" -> isRecycleBinEnabled
            "recent" -> appState.appConfigs.isRecentVisible
            else -> true
        }
    }

    Box(modifier = Modifier.fillMaxHeight()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxHeight()
                .contextMenuDetector(enableLongPress = true, aggressive = false) { offset ->
                    bgMenuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                    showBgMenu = true
                }
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Section: Favorites
            item { NavSectionHeader("Favorites") }

            if (appState.appConfigs.favoritePaths.isEmpty()) {
                item {
                    Text(
                        text = "No favorites added",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                itemsIndexed(
                    items = appState.appConfigs.favoritePaths,
                    key = { _, path -> path }
                ) { index, path ->
                    val file = File(path)
                    val isDragging = draggedItemId == path
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "FavoriteElevation")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, if (isDragging) draggingOffset.roundToInt() else 0) }
                            .shadow(elevation)
                            .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                            .pointerInput(path) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)

                                    // REORDER LOGIC:
                                    // - Mouse: Drag from anywhere on the item
                                    // - Touch: Drag only from the icon area (handleWidthPx)
                                    if (down.type == PointerType.Mouse || down.position.x <= handleWidthPx) {
                                        var pointerId = down.id
                                        var triggerDrag = false
                                        var distance = 0f

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val move = event.changes.firstOrNull { it.id == pointerId } ?: break
                                            if (!move.pressed) break
                                            distance += (move.position - down.position).getDistance()
                                            if (distance > 5f) {
                                                triggerDrag = true
                                                break
                                            }
                                        }

                                        if (triggerDrag) {
                                            draggedItemId = path
                                            draggingOffset = 0f

                                            drag(down.id) { change ->
                                                val changeOffset = change.positionChange()
                                                draggingOffset += changeOffset.y

                                                val currentIndex = appState.appConfigs.favoritePaths.indexOf(path)
                                                if (currentIndex != -1) {
                                                    val itemHeight = 40.dp.toPx()
                                                    val threshold = itemHeight * 0.6f

                                                    if (draggingOffset > threshold && currentIndex < appState.appConfigs.favoritePaths.size - 1) {
                                                        appState.appConfigs.moveFavorite(currentIndex, currentIndex + 1)
                                                        draggingOffset -= itemHeight
                                                    } else if (draggingOffset < -threshold && currentIndex > 0) {
                                                        appState.appConfigs.moveFavorite(currentIndex, currentIndex - 1)
                                                        draggingOffset += itemHeight
                                                    }
                                                }
                                                change.consume()
                                            }
                                            draggedItemId = null
                                            draggingOffset = 0f
                                        }
                                    }
                                }
                            }
                            .fileDropTarget(appState, destPath = file)
                    ) {
                        NavFavoriteItem(
                            label = file.name,
                            path = path,
                            onClick = { appState.navigateTo(file, null) },
                            onRemove = { appState.appConfigs.removeFavorite(path) },
                            appState = appState
                        )
                    }
                }
            }

            if (visibleLibraryItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    NavSectionHeader("Library")
                }

                itemsIndexed(
                    items = visibleLibraryItems,
                    key = { _, id -> id }
                ) { index, id ->
                    val isDragging = draggedItemId == id
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "LibraryElevation")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, if (isDragging) draggingOffset.roundToInt() else 0) }
                            .shadow(elevation)
                            .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                            .pointerInput(id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)

                                    // REORDER LOGIC:
                                    // - Mouse: Drag from anywhere on the item
                                    // - Touch: Drag only from the icon area (handleWidthPx)
                                    if (down.type == PointerType.Mouse || down.position.x <= handleWidthPx) {
                                        var pointerId = down.id
                                        var triggerDrag = false
                                        var distance = 0f

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val move = event.changes.firstOrNull { it.id == pointerId } ?: break
                                            if (!move.pressed) break
                                            distance += (move.position - down.position).getDistance()
                                            if (distance > 5f) {
                                                triggerDrag = true
                                                break
                                            }
                                        }

                                        if (triggerDrag) {
                                            draggedItemId = id
                                            draggingOffset = 0f

                                            drag(down.id) { change ->
                                                val changeOffset = change.positionChange()
                                                draggingOffset += changeOffset.y

                                                val currentIndex = appState.appConfigs.libraryOrder.indexOf(id)
                                                if (currentIndex != -1) {
                                                    val itemHeight = 40.dp.toPx()
                                                    val threshold = itemHeight * 0.6f

                                                    if (draggingOffset > threshold && currentIndex < appState.appConfigs.libraryOrder.size - 1) {
                                                        appState.appConfigs.moveLibraryItem(currentIndex, currentIndex + 1)
                                                        draggingOffset -= itemHeight
                                                    } else if (draggingOffset < -threshold && currentIndex > 0) {
                                                        appState.appConfigs.moveLibraryItem(currentIndex, currentIndex - 1)
                                                        draggingOffset += itemHeight
                                                    }
                                                }
                                                change.consume()
                                            }
                                            draggedItemId = null
                                            draggingOffset = 0f
                                        }
                                    }
                                }
                            }
                    ) {
                        if (id == "recent") {
                            NavItem(
                                label = "Recent",
                                icon = Icons.Default.History,
                                onClick = { onItemSelected(8) },
                                appState = appState,
                                isRecent = true
                            )
                        } else if (id == "trash") {
                            val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                            NavItem(
                                label = "Recycle Bin",
                                icon = Icons.Default.Delete,
                                onClick = { onItemSelected(7) },
                                modifier = Modifier.fileDropTarget(appState, destPath = trashDir),
                                appState = appState,
                                isRecycleBin = true
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Section: Storage
            item { NavSectionHeader("Storage") }
            
            itemsIndexed(storageVolumes) { _, volume ->
                NavStorageItem(
                    label = volume.label,
                    icon = volume.icon,
                    totalSpace = volume.totalSpace,
                    freeSpace = volume.freeSpace,
                    onClick = { onItemSelected(volume.id) },
                    modifier = Modifier.fileDropTarget(appState, destPath = volume.path),
                    appState = appState,
                    path = volume.path
                )
            }

            // Section: Added Locations (SAF)
            if (appState.appConfigs.addedSafUris.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    NavSectionHeader("Added Locations")
                }

                itemsIndexed(appState.appConfigs.addedSafUris) { _, uri ->
                    val label = appState.getSafDisplayName(uri)

                    NavSafItem(
                        label = label,
                        uri = uri,
                        onClick = { onSafItemSelected(uri) },
                        onRemove = { appState.removeSafUri(uri) },
                        modifier = Modifier.fileDropTarget(appState, destSafUri = uri),
                        appState = appState
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Background context menu
        Box(modifier = Modifier.offset(bgMenuOffset.x, bgMenuOffset.y)) {
            NavBackgroundContextMenu(
                expanded = showBgMenu,
                onDismissRequest = { showBgMenu = false },
                appState = appState,
                onAddStorageClick = onAddStorageClick
            )
        }
    }
}

@Composable
private fun NavBackgroundContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    appState: FileExplorerState,
    onAddStorageClick: () -> Unit
) {
    var currentScreen by remember { mutableStateOf("MAIN") }

    DropdownMenu(expanded = expanded, onDismissRequest = {
        onDismissRequest()
        currentScreen = "MAIN"
    }) {
        when (currentScreen) {
            "MAIN" -> {
                DropdownMenuItem(
                    text = { Text("Library items") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    onClick = { currentScreen = "LIBRARY" }
                )
                DropdownMenuItem(
                    text = { Text("Add Storage") },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    onClick = {
                        onDismissRequest()
                        onAddStorageClick()
                    }
                )
            }
            "LIBRARY" -> {
                DropdownMenuItem(
                    text = { Text("Back", color = MaterialTheme.colorScheme.primary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { currentScreen = "MAIN" }
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("Recent") },
                    leadingIcon = { Icon(Icons.Default.History, null) },
                    trailingIcon = { if (appState.appConfigs.isRecentVisible) Icon(Icons.Default.Check, null) },
                    onClick = {
                        appState.appConfigs.toggleRecentVisibility()
                        onDismissRequest()
                        currentScreen = "MAIN"
                    }
                )
            }
        }
    }
}

@Composable
private fun NavSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun NavContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    appState: FileExplorerState,
    label: String,
    path: String? = null,
    uri: Uri? = null,
    isRecycleBin: Boolean = false,
    isRecent: Boolean = false,
    onRemove: (() -> Unit)? = null
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text("Open in New Tab") },
            onClick = {
                onDismissRequest()
                if (isRecycleBin) {
                    val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                    appState.onOpenInNewTab?.invoke(trashDir.toUniversal())
                } else if (isRecent) {
                    appState.onOpenInNewTab?.invoke(com.example.continuum_explorer.model.UniversalFile(
                        name = "Recent",
                        isDirectory = true,
                        lastModified = 0,
                        length = 0,
                        absolutePath = "recent://"
                    ))
                } else if (path != null) {
                    appState.onOpenInNewTab?.invoke(File(path).toUniversal())
                } else if (uri != null) {
                    val doc = DocumentFile.fromTreeUri(appState.context, uri)
                    if (doc != null) appState.onOpenInNewTab?.invoke(doc.toUniversal())
                }
            },
            leadingIcon = { Icon(Icons.Default.Tab, null) }
        )
        DropdownMenuItem(
            text = { Text("Open in New Window") },
            onClick = {
                onDismissRequest()
                val universalList = when {
                    isRecycleBin -> listOf(File(Environment.getExternalStorageDirectory(), ".Trash").toUniversal())
                    isRecent -> emptyList() // Not supported directly yet
                    path != null -> listOf(File(path).toUniversal())
                    uri != null -> DocumentFile.fromTreeUri(appState.context, uri)?.let { listOf(it.toUniversal()) } ?: emptyList()
                    else -> emptyList()
                }
                if (isRecent) {
                    // Logic to open Recent in new window if needed
                } else {
                    appState.openInNewWindow(universalList)
                }
            },
            leadingIcon = { Icon(Icons.Default.Splitscreen, null) }
        )
        if (isRecycleBin) {
            DropdownMenuItem(
                text = { Text("Empty Recycle Bin") },
                onClick = {
                    onDismissRequest()
                    appState.emptyRecycleBin()
                },
                leadingIcon = { Icon(Icons.Default.DeleteForever, null) }
            )
        }
        if (onRemove != null) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (path != null) "Remove from Favorites" else "Remove") },
                onClick = {
                    onDismissRequest()
                    onRemove()
                },
                leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp)) }
            )
        }
        if (!isRecent) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Properties") },
                onClick = { onDismissRequest() },
                leadingIcon = { Icon(Icons.Default.Info, null) }
            )
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    appState: FileExplorerState,
    modifier: Modifier = Modifier,
    isRecycleBin: Boolean = false,
    isRecent: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero)}
    val density = LocalDensity.current

    Box(modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
        NavigationDrawerItem(
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            selected = false,
            onClick = onClick,
            icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            modifier = Modifier.height(40.dp).contextMenuDetector(enableLongPress = true, aggressive = true) { offset ->
                menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                expanded = true
            },
            shape = MaterialTheme.shapes.small
        )

        Box(modifier = Modifier.offset(menuOffset.x, menuOffset.y)) {
            NavContextMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                appState = appState,
                label = label,
                isRecycleBin = isRecycleBin,
                isRecent = isRecent
            )
        }
    }
}

/**
 * Item for Favorite locations with a removal context menu.
 */
@Composable
private fun NavFavoriteItem(
    label: String,
    path: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    appState: FileExplorerState
) {
    var expanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero)}
    val density = LocalDensity.current

    Box(modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
        NavigationDrawerItem(
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            selected = false,
            onClick = onClick,
            icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .height(40.dp)
                .contextMenuDetector(enableLongPress = true, aggressive = true) { offset ->
                    menuOffset = with(density) {
                        DpOffset(offset.x.toDp(), offset.y.toDp())
                    }
                    expanded = true
                }
        )

        Box (modifier = Modifier.offset(menuOffset.x, menuOffset.y)) {
            NavContextMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                appState = appState,
                label = label,
                path = path,
                onRemove = onRemove
            )
        }
    }
}

/**
 * Item for SAF-added locations with a removal context menu.
 */
@Composable
private fun NavSafItem(
    label: String,
    uri: Uri,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    appState: FileExplorerState,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero)}
    val density = LocalDensity.current

    Box(modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
        NavigationDrawerItem(
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            selected = false,
            onClick = onClick,
            icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .height(40.dp)
                .contextMenuDetector(enableLongPress = true, aggressive = true) { offset ->
                    menuOffset = with(density) {
                        DpOffset(offset.x.toDp(), offset.y.toDp())
                    }
                    expanded = true
                }
        )

        Box (modifier = Modifier.offset(menuOffset.x, menuOffset.y)) {
            NavContextMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                appState = appState,
                label = label,
                uri = uri,
                onRemove = onRemove
            )
        }
    }
}

/**
 * Custom navigation item for Storage that shows a progress bar and usage details.
 */
@Composable
private fun NavStorageItem(
    label: String,
    icon: ImageVector,
    totalSpace: Long,
    freeSpace: Long,
    onClick: () -> Unit,
    appState: FileExplorerState,
    path: File?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero)}
    val density = LocalDensity.current

    val totalFormatted = Formatter.formatFileSize(context, totalSpace)
    val freeFormatted = Formatter.formatFileSize(context, freeSpace)

    val usedSpace = totalSpace - freeSpace
    val progress = if (totalSpace > 0) usedSpace.toFloat() / totalSpace.toFloat() else 0f

    Box(modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
        NavigationDrawerItem(
            label = {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$freeFormatted free of $totalFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            selected = false,
            onClick = onClick,
            icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            modifier = Modifier.contextMenuDetector(enableLongPress = true, aggressive = true) { offset ->
                menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                expanded = true
            },
            shape = MaterialTheme.shapes.medium
        )

        Box(modifier = Modifier.offset(menuOffset.x, menuOffset.y)) {
            NavContextMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                appState = appState,
                label = label,
                path = path?.absolutePath
            )
        }
    }
}
