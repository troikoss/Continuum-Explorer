package com.troikoss.continuum_explorer.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.troikoss.continuum_explorer.ui.activities.MainActivity
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.text.format.Formatter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.model.NavSection
import com.troikoss.continuum_explorer.model.LibraryItem
import com.troikoss.continuum_explorer.model.NetworkConnection
import com.troikoss.continuum_explorer.model.NetworkProtocol
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import com.troikoss.continuum_explorer.providers.StorageProviders
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.utils.contextMenuDetector
import com.troikoss.continuum_explorer.utils.emptyRecycleBin
import com.troikoss.continuum_explorer.utils.fileDropTarget
import com.troikoss.continuum_explorer.utils.navigateTo
import com.troikoss.continuum_explorer.utils.openInNewWindow
import com.troikoss.continuum_explorer.utils.showProperties
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val section: NavSection
)

/**
 * A revamped navigation sidebar with section headers and organized locations.
 */
@Composable
fun NavigationPane(
    appState: FileExplorerState,
    onItemSelected: (NavSection) -> Unit,
    onSafItemSelected: (Uri) -> Unit,
    onAddStorageClick: () -> Unit,
    onAddNetworkClick: () -> Unit = {},
    onEditNetworkClick: (NetworkConnection) -> Unit = {},
    onNavigate: () -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val isRecycleBinEnabled by SettingsManager.isRecycleBinEnabled

    // Gather all storage volumes
    val storageVolumes = remember(context) {
        val volumes = mutableListOf<StorageVolumeInfo>()

        // Add Internal Storage
        val internalRoot = Environment.getExternalStorageDirectory()
        volumes.add(
            StorageVolumeInfo(
                label = resources.getString(R.string.nav_internal_storage),
                path = internalRoot,
                uri = null,
                totalSpace = internalRoot.totalSpace,
                freeSpace = internalRoot.usableSpace,
                icon = Icons.Default.Storage,
                section = NavSection.InternalStorage
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
                                label = description.ifEmpty { resources.getString(R.string.nav_external_drive) },
                                path = directory,
                                uri = null,
                                totalSpace = directory.totalSpace,
                                freeSpace = directory.usableSpace,
                                icon = if (isSdCard) Icons.Default.SdCard else Icons.Default.Usb,
                                section = NavSection.RemovableVolume(index)
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
            "gallery" -> appState.appConfigs.isGalleryVisible
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
            item { NavSectionHeader(stringResource(R.string.nav_favorites)) }

            if (appState.appConfigs.favoritePaths.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.nav_no_favorites),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                itemsIndexed(
                    items = appState.appConfigs.favoritePaths,
                    key = { _, path -> path }
                ) { _, path ->
                    val file = File(path)
                    val isDragging = draggedItemId == path
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "FavoriteElevation")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset {
                                IntOffset(
                                    0,
                                    if (isDragging) draggingOffset.roundToInt() else 0
                                )
                            }
                            .shadow(elevation)
                            .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .pointerInput(path) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)

                                    // REORDER LOGIC:
                                    // - Mouse: Drag from anywhere on the item
                                    // - Touch: Drag only from the icon area (handleWidthPx)
                                    if (down.type == PointerType.Mouse || down.position.x <= handleWidthPx) {
                                        val pointerId = down.id
                                        var triggerDrag = false
                                        var distance = 0f

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val move =
                                                event.changes.firstOrNull { it.id == pointerId }
                                                    ?: break
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

                                                val currentIndex =
                                                    appState.appConfigs.favoritePaths.indexOf(path)
                                                if (currentIndex != -1) {
                                                    val itemHeight = 40.dp.toPx()
                                                    val threshold = itemHeight * 0.6f

                                                    if (draggingOffset > threshold && currentIndex < appState.appConfigs.favoritePaths.size - 1) {
                                                        appState.appConfigs.moveFavorite(
                                                            currentIndex,
                                                            currentIndex + 1
                                                        )
                                                        draggingOffset -= itemHeight
                                                    } else if (draggingOffset < -threshold && currentIndex > 0) {
                                                        appState.appConfigs.moveFavorite(
                                                            currentIndex,
                                                            currentIndex - 1
                                                        )
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
                            onClick = { appState.navigateTo(file, null); onNavigate() },
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
                    NavSectionHeader(stringResource(R.string.nav_library))
                }

                itemsIndexed(
                    items = visibleLibraryItems,
                    key = { _, id -> id }
                ) { _, id ->
                    val isDragging = draggedItemId == id
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "LibraryElevation")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset {
                                IntOffset(
                                    0,
                                    if (isDragging) draggingOffset.roundToInt() else 0
                                )
                            }
                            .shadow(elevation)
                            .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .pointerInput(id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)

                                    // REORDER LOGIC:
                                    // - Mouse: Drag from anywhere on the item
                                    // - Touch: Drag only from the icon area (handleWidthPx)
                                    if (down.type == PointerType.Mouse || down.position.x <= handleWidthPx) {
                                        val pointerId = down.id
                                        var triggerDrag = false
                                        var distance = 0f

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val move =
                                                event.changes.firstOrNull { it.id == pointerId }
                                                    ?: break
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

                                                val currentIndex =
                                                    appState.appConfigs.libraryOrder.indexOf(id)
                                                if (currentIndex != -1) {
                                                    val itemHeight = 40.dp.toPx()
                                                    val threshold = itemHeight * 0.6f

                                                    if (draggingOffset > threshold && currentIndex < appState.appConfigs.libraryOrder.size - 1) {
                                                        appState.appConfigs.moveLibraryItem(
                                                            currentIndex,
                                                            currentIndex + 1
                                                        )
                                                        draggingOffset -= itemHeight
                                                    } else if (draggingOffset < -threshold && currentIndex > 0) {
                                                        appState.appConfigs.moveLibraryItem(
                                                            currentIndex,
                                                            currentIndex - 1
                                                        )
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
                        if (id == "gallery") {
                            NavItem(
                                label = stringResource(R.string.nav_gallery),
                                icon = Icons.Default.Image,
                                onClick = { onItemSelected(NavSection.Gallery) },
                                appState = appState,
                                section = NavSection.Gallery
                            )
                        } else if (id == "recent") {
                            NavItem(
                                label = stringResource(R.string.nav_recent),
                                icon = Icons.Default.History,
                                onClick = { onItemSelected(NavSection.Recent) },
                                appState = appState,
                                section = NavSection.Recent
                            )
                        } else if (id == "trash") {
                            val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                            NavItem(
                                label = stringResource(R.string.nav_trash),
                                icon = Icons.Default.Delete,
                                onClick = { onItemSelected(NavSection.RecycleBin) },
                                modifier = Modifier.fileDropTarget(appState, destPath = trashDir),
                                appState = appState,
                                section = NavSection.RecycleBin
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
            item { NavSectionHeader(stringResource(R.string.nav_storage)) }
            
            itemsIndexed(storageVolumes) { _, volume ->
                NavStorageItem(
                    label = volume.label,
                    icon = volume.icon,
                    totalSpace = volume.totalSpace,
                    freeSpace = volume.freeSpace,
                    onClick = { onItemSelected(volume.section) },
                    modifier = Modifier.fileDropTarget(appState, destPath = volume.path),
                    appState = appState,
                    path = volume.path,
                    onNavigate = onNavigate
                )
            }

            // Section: Added Locations (SAF)
            if (appState.appConfigs.addedSafUris.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    NavSectionHeader(stringResource(R.string.nav_added_locations))
                }

                itemsIndexed(
                    items = appState.appConfigs.addedSafUris,
                    key = { _, uri -> uri.toString() }
                ) { _, uri ->
                    val label = appState.getSafDisplayName(uri)
                    val uriKey = uri.toString()
                    val isDragging = draggedItemId == uriKey
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "SafElevation")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, if (isDragging) draggingOffset.roundToInt() else 0) }
                            .shadow(elevation)
                            .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .pointerInput(uriKey) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (down.type == PointerType.Mouse || down.position.x <= handleWidthPx) {
                                        val pointerId = down.id
                                        var triggerDrag = false
                                        var distance = 0f
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val move = event.changes.firstOrNull { it.id == pointerId } ?: break
                                            if (!move.pressed) break
                                            distance += (move.position - down.position).getDistance()
                                            if (distance > 5f) { triggerDrag = true; break }
                                        }
                                        if (triggerDrag) {
                                            draggedItemId = uriKey
                                            draggingOffset = 0f
                                            drag(down.id) { change ->
                                                draggingOffset += change.positionChange().y
                                                val currentIndex = appState.appConfigs.addedSafUris.indexOfFirst { it.toString() == uriKey }
                                                if (currentIndex != -1) {
                                                    val itemHeight = 40.dp.toPx()
                                                    val threshold = itemHeight * 0.6f
                                                    if (draggingOffset > threshold && currentIndex < appState.appConfigs.addedSafUris.size - 1) {
                                                        appState.appConfigs.moveSafUri(currentIndex, currentIndex + 1)
                                                        draggingOffset -= itemHeight
                                                    } else if (draggingOffset < -threshold && currentIndex > 0) {
                                                        appState.appConfigs.moveSafUri(currentIndex, currentIndex - 1)
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
            }

            // Section: Network
            if (appState.appConfigs.networkConnections.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    NavSectionHeader(stringResource(R.string.nav_network))
                }

                itemsIndexed(
                    items = appState.appConfigs.networkConnections,
                    key = { _, conn -> conn.id }
                ) { _, connection ->
                    val isDragging = draggedItemId == connection.id
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "NetworkElevation")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, if (isDragging) draggingOffset.roundToInt() else 0) }
                            .shadow(elevation)
                            .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .pointerInput(connection.id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (down.type == PointerType.Mouse || down.position.x <= handleWidthPx) {
                                        val pointerId = down.id
                                        var triggerDrag = false
                                        var distance = 0f
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val move = event.changes.firstOrNull { it.id == pointerId } ?: break
                                            if (!move.pressed) break
                                            distance += (move.position - down.position).getDistance()
                                            if (distance > 5f) { triggerDrag = true; break }
                                        }
                                        if (triggerDrag) {
                                            draggedItemId = connection.id
                                            draggingOffset = 0f
                                            drag(down.id) { change ->
                                                draggingOffset += change.positionChange().y
                                                val currentIndex = appState.appConfigs.networkConnections.indexOfFirst { it.id == connection.id }
                                                if (currentIndex != -1) {
                                                    val itemHeight = 40.dp.toPx()
                                                    val threshold = itemHeight * 0.6f
                                                    if (draggingOffset > threshold && currentIndex < appState.appConfigs.networkConnections.size - 1) {
                                                        appState.appConfigs.moveNetworkConnection(currentIndex, currentIndex + 1)
                                                        draggingOffset -= itemHeight
                                                    } else if (draggingOffset < -threshold && currentIndex > 0) {
                                                        appState.appConfigs.moveNetworkConnection(currentIndex, currentIndex - 1)
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
                        NavNetworkItem(
                            connection = connection,
                            onClick = { onItemSelected(NavSection.NetworkStorage(connection.id)) },
                            onRemove = { appState.appConfigs.removeNetworkConnection(connection.id) },
                            onEdit = { onEditNetworkClick(connection) },
                            appState = appState
                        )
                    }
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
                onAddStorageClick = onAddStorageClick,
                onAddNetworkClick = onAddNetworkClick
            )
        }
    }
}

@Composable
private fun NavBackgroundContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    appState: FileExplorerState,
    onAddStorageClick: () -> Unit,
    onAddNetworkClick: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf("MAIN") }

    DropdownMenu(expanded = expanded, onDismissRequest = {
        onDismissRequest()
        currentScreen = "MAIN"
    }) {
        when (currentScreen) {
            "MAIN" -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_library_items)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    onClick = { currentScreen = "LIBRARY" }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_add_storage)) },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    onClick = { currentScreen = "ADD_STORAGE" }
                )
            }
            "ADD_STORAGE" -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.back), color = MaterialTheme.colorScheme.primary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { currentScreen = "MAIN" }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_add_local_storage)) },
                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                    onClick = {
                        onDismissRequest()
                        onAddStorageClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_add_network_storage)) },
                    leadingIcon = { Icon(Icons.Default.Cloud, null) },
                    onClick = {
                        onDismissRequest()
                        onAddNetworkClick()
                    }
                )
            }
            "LIBRARY" -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.back), color = MaterialTheme.colorScheme.primary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { currentScreen = "MAIN" }
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_gallery)) },
                    leadingIcon = { Icon(Icons.Default.Image, null) },
                    trailingIcon = { if (appState.appConfigs.isGalleryVisible) Icon(Icons.Default.Check, null) },
                    onClick = {
                        appState.appConfigs.toggleGalleryVisibility()
                        onDismissRequest()
                        currentScreen = "MAIN"
                    }
                )

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_recent)) },
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
    section: NavSection? = null,
    onRemove: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_open_new_tab)) },
            onClick = {
                onDismissRequest()
                when (section) {
                    is NavSection.RecycleBin -> appState.onOpenInNewTab?.invoke(
                        UniversalFile(name = ".Trash", isDirectory = true, lastModified = 0, length = 0, provider = LocalProvider, providerId = "trash://")
                    )
                    is NavSection.Recent -> appState.onOpenInNewTab?.invoke(
                        UniversalFile(name = "Recent", isDirectory = true, lastModified = 0, length = 0, provider = LocalProvider, providerId = "recent://")
                    )
                    is NavSection.Gallery -> appState.onOpenInNewTab?.invoke(
                        UniversalFile(name = "Gallery", isDirectory = true, lastModified = 0, length = 0, provider = LocalProvider, providerId = "gallery://")
                    )
                    is NavSection.NetworkStorage -> {
                        val conn = appState.appConfigs.networkConnections.find { it.id == section.connectionId }
                        if (conn != null) {
                            val provider = StorageProviders.network(conn)
                            appState.onOpenInNewTab?.invoke(
                                UniversalFile(name = conn.displayName, isDirectory = true, lastModified = 0, length = 0, provider = provider, providerId = provider.rootId())
                            )
                        }
                    }
                    else -> if (path != null) {
                        appState.onOpenInNewTab?.invoke(File(path).toUniversal())
                    } else if (uri != null) {
                        val doc = DocumentFile.fromTreeUri(appState.context, uri)
                        if (doc != null) appState.onOpenInNewTab?.invoke(doc.toUniversal())
                    }
                }
            },
            leadingIcon = { Icon(Icons.Default.Tab, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_open_new_window)) },
            onClick = {
                onDismissRequest()
                when (section) {
                    is NavSection.Recent, is NavSection.Gallery, is NavSection.RecycleBin -> {
                        val intent = Intent(appState.context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                            if (section is NavSection.Recent) putExtra("isRecent", true)
                            if (section is NavSection.Gallery) putExtra("isGallery", true)
                            if (section is NavSection.RecycleBin) putExtra("isRecycleBin", true)
                        }
                        appState.context.startActivity(intent)
                    }
                    is NavSection.NetworkStorage -> {
                        val intent = Intent(appState.context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                            putExtra("networkConnectionId", section.connectionId)
                        }
                        appState.context.startActivity(intent)
                    }
                    else -> when {
                        path != null -> appState.openInNewWindow(listOf(File(path).toUniversal()))
                        uri != null -> {
                            val doc = DocumentFile.fromTreeUri(appState.context, uri)
                            if (doc != null) appState.openInNewWindow(listOf(doc.toUniversal()))
                        }
                        else -> appState.openInNewWindow(emptyList())
                    }
                }
            },
            leadingIcon = { Icon(Icons.Default.Splitscreen, null) }
        )
        if (section is NavSection.RecycleBin) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_empty_recycle_bin)) },
                onClick = {
                    onDismissRequest()
                    appState.emptyRecycleBin()
                },
                leadingIcon = { Icon(Icons.Default.DeleteForever, null) }
            )
        }
        if (onEdit != null || onRemove != null) {
            HorizontalDivider()
        }
        if (onEdit != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nav_network_edit)) },
                onClick = {
                    onDismissRequest()
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
        }
        if (onRemove != null) {
            DropdownMenuItem(
                text = { Text(if (path != null) stringResource(R.string.menu_remove_favorites) else stringResource(R.string.menu_remove)) },
                onClick = {
                    onDismissRequest()
                    onRemove()
                },
                leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp)) }
            )
        }
        if (section is NavSection.Gallery) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_gallery_albums)) },
                onClick = {
                    onDismissRequest()
                    appState.appConfigs.toggleGalleryAlbums()
                    if (appState.libraryItem == LibraryItem.Gallery) {
                        appState.triggerLoad(forceRefresh = true)
                    }
                },
                leadingIcon = { if (appState.appConfigs.isGalleryAlbumsEnabled) Icon(Icons.Default.Check, null) }
            )
        }
        if (section !is NavSection.Recent && section !is NavSection.Gallery && section !is NavSection.NetworkStorage) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_properties)) },
                onClick = {
                    onDismissRequest()
                    when (section) {
                        is NavSection.RecycleBin -> {
                            val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                            appState.showProperties(listOf(trashDir.toUniversal()))
                        }
                        else -> if (path != null) {
                            appState.showProperties(listOf(File(path).toUniversal()))
                        } else if (uri != null) {
                            val doc = DocumentFile.fromTreeUri(appState.context, uri)
                            if (doc != null) appState.showProperties(listOf(doc.toUniversal()))
                        }
                    }
                },
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
    section: NavSection? = null
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
            modifier = Modifier
                .height(40.dp)
                .contextMenuDetector(enableLongPress = true, aggressive = true) { offset ->
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
                section = section
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

    Box(modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
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

@Composable
private fun NavNetworkItem(
    connection: NetworkConnection,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    appState: FileExplorerState
) {
    var expanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    val context = LocalContext.current

    val icon = when (connection.protocol) {
        NetworkProtocol.FTP -> Icons.Default.Lan
        NetworkProtocol.SFTP -> Icons.Default.Lan
        NetworkProtocol.WEBDAV -> Icons.Default.Lan
        NetworkProtocol.SMB -> Icons.Default.Lan
    }

    // Fetch disk info for SMB connections only
    var diskInfo by remember(connection.id) { mutableStateOf<Pair<Long, Long>?>(null) }
    val isSmb = connection.protocol == NetworkProtocol.SMB
    LaunchedEffect(connection.id) {
        if (isSmb) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val provider = StorageProviders.network(connection)
                    provider.getDiskInfo()
                }.getOrNull()?.let { diskInfo = it }
            }
        }
    }

    Box(modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
        NavigationDrawerItem(
            label = {
                if (isSmb && diskInfo != null) {
                    val (totalSpace, freeSpace) = diskInfo!!
                    val usedSpace = totalSpace - freeSpace
                    val progress = if (totalSpace > 0) usedSpace.toFloat() / totalSpace.toFloat() else 0f
                    val totalFormatted = Formatter.formatFileSize(context, totalSpace)
                    val freeFormatted = Formatter.formatFileSize(context, freeSpace)
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = connection.displayName,
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
                            text = stringResource(R.string.nav_storage_usage_label, freeFormatted, totalFormatted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(connection.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            selected = false,
            onClick = onClick,
            icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .then(if (!isSmb || diskInfo == null) Modifier.height(40.dp) else Modifier)
                .contextMenuDetector(enableLongPress = true, aggressive = true) { offset ->
                    menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                    expanded = true
                }
        )

        Box(modifier = Modifier.offset(menuOffset.x, menuOffset.y)) {
            NavContextMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                appState = appState,
                label = connection.displayName,
                section = NavSection.NetworkStorage(connection.id),
                onRemove = onRemove,
                onEdit = onEdit
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
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit = {}
) {
    val context = LocalContext.current
    var expandedMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero)}
    val density = LocalDensity.current

    var expandedTree by remember { mutableStateOf(false) }
    var subDirs by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(expandedTree) {
        if (expandedTree && path != null) {
            withContext(Dispatchers.IO) {
                val dirs = path.listFiles()?.filter { it.isDirectory && !it.isHidden }?.sortedBy { it.name }
                if (dirs != null) {
                    withContext(Dispatchers.Main) {
                        subDirs = dirs
                    }
                }
            }
        }
    }

    val totalFormatted = Formatter.formatFileSize(context, totalSpace)
    val freeFormatted = Formatter.formatFileSize(context, freeSpace)

    val usedSpace = totalSpace - freeSpace
    val progress = if (totalSpace > 0) usedSpace.toFloat() / totalSpace.toFloat() else 0f

    Column {
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
                            text = stringResource(R.string.nav_storage_usage_label, freeFormatted, totalFormatted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                selected = false,
                onClick = onClick,
                icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                badge = {
                    if (path != null) {
                        IconButton(
                            onClick = { expandedTree = !expandedTree },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (expandedTree) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (expandedTree) stringResource(R.string.nav_collapse) else stringResource(R.string.nav_expand),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier.contextMenuDetector(enableLongPress = true, aggressive = true) { offset ->
                    menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                    expandedMenu = true
                },
                shape = MaterialTheme.shapes.medium
            )

            Box(modifier = Modifier.offset(menuOffset.x, menuOffset.y)) {
                NavContextMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false },
                    appState = appState,
                    label = label,
                    path = path?.absolutePath
                )
            }
        }

        if (expandedTree) {
            subDirs.forEach { childFolder ->
                StorageFolderTreeItem(
                    folder = childFolder,
                    level = 1,
                    appState = appState,
                    onNavigate = onNavigate
                )
            }
        }
    }
}

@Composable
private fun StorageFolderTreeItem(
    folder: File,
    level: Int,
    appState: FileExplorerState,
    onNavigate: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var subDirs by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(expanded) {
        if (expanded) {
            withContext(Dispatchers.IO) {
                val dirs = folder.listFiles()?.filter { it.isDirectory && !it.isHidden }?.sortedBy { it.name }
                if (dirs != null) {
                    withContext(Dispatchers.Main) {
                        subDirs = dirs
                    }
                }
            }
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (16 + level * 16).dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { appState.navigateTo(folder, null); onNavigate() }
                .fileDropTarget(appState, destPath = folder),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) stringResource(R.string.nav_collapse) else stringResource(R.string.nav_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (expanded) {
            subDirs.forEach { childFolder ->
                StorageFolderTreeItem(
                    folder = childFolder,
                    level = level + 1,
                    appState = appState,
                    onNavigate = onNavigate
                )
            }
        }
    }
}