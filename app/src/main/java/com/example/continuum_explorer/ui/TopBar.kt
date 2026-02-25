package com.example.continuum_explorer.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.continuum_explorer.SettingsActivity
import com.example.continuum_explorer.model.NavLocation
import com.example.continuum_explorer.model.ScreenSize
import com.example.continuum_explorer.utils.fileDropTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Standardized Top Bar for the explorer. 
 * Shows navigation controls, breadcrumbs, and a toggleable address bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    isLandscape: Boolean,
    onMenuClick: () -> Unit,
    onAddStorageClick: () -> Unit,
    appState: FileExplorerState
) {

    val context = LocalContext.current

    var optionsMenuExpanded by remember { mutableStateOf(false) }
    var addressBar by remember { mutableStateOf(false) }
    var historyMenuExpanded by remember { mutableStateOf(false) }

    // Search related state
    var searchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var searchOptionsMenuExpanded by remember { mutableStateOf(false) }
    var searchSubfolders by remember { mutableStateOf(false) }
    var searchKindMenuExpanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Sync UI searchBar state to global state
    LaunchedEffect(searchBar) {
        appState.isSearchUIActive = searchBar
    }

    // Logic to determine what to show in the address bar text field
    val currentPathString = remember(appState.currentPath, appState.currentSafUri, appState.currentArchiveFile, appState.currentArchiveUri, appState.currentArchivePath) {
        if (appState.currentArchiveFile != null) {
            val base = appState.currentArchiveFile?.absolutePath ?: ""
            if (appState.currentArchivePath.isEmpty()) base else "$base/${appState.currentArchivePath}"
        } else if (appState.currentArchiveUri != null) {
            val base = appState.currentArchiveUri.toString()
            if (appState.currentArchivePath.isEmpty()) base else "$base/${appState.currentArchivePath}"
        } else if (appState.currentPath != null) {
            appState.currentPath?.absolutePath ?: ""
        } else if (appState.currentSafUri != null) {
            appState.currentSafUri.toString()
        } else {
            ""
        }
    }

    // Breadcrumbs logic for File paths (including local Archives)
    val allSegments = remember(appState.currentPath, appState.currentArchiveFile, appState.currentArchivePath) {
        val fullPath = if (appState.currentArchiveFile != null) {
            val base = appState.currentArchiveFile?.absolutePath ?: ""
            val inner = appState.currentArchivePath
            if (inner.isEmpty()) base else "$base/$inner"
        } else {
            appState.currentPath?.absolutePath ?: ""
        }
        fullPath.split("/").filter { it.isNotEmpty() }
    }
    
    val zeroIndex = allSegments.indexOf("0")
    val visibleSegments = if (zeroIndex != -1) {
        allSegments.subList(zeroIndex, allSegments.size)
    } else {
        allSegments
    }

    var textPathValue by remember(currentPathString) {
        mutableStateOf(TextFieldValue(currentPathString))
    }

    LaunchedEffect(addressBar) {
        if (addressBar) {
            focusRequester.requestFocus()
            textPathValue = textPathValue.copy(
                selection = TextRange(0, textPathValue.text.length)
            )
        }
    }

    LaunchedEffect(searchBar) {
        if (searchBar) {
            focusRequester.requestFocus()
            searchQuery = searchQuery.copy(
                selection = TextRange(0, searchQuery.text.length)
            )
        }
    }

    val breadcrumbScrollState = rememberScrollState()

    // Auto-scroll to end when path changes
    LaunchedEffect(currentPathString) {
        breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
    }

    // Debounced Cancel button logic
    var showCancelButton by remember { mutableStateOf(false) }
    LaunchedEffect(appState.isLoading) {
        if (appState.isLoading) {
            delay(400) // Match the 400ms delay of the spinner
            showCancelButton = true
        } else {
            showCancelButton = false
        }
    }

    Surface( modifier = Modifier
        .fillMaxWidth()
        .height(56.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row (modifier = Modifier.padding(8.dp), verticalAlignment = CenterVertically) {
            if (appState.getScreenSize() == ScreenSize.SMALL) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            }
            
            // NAVIGATION GROUP (Back/Forward/History)
            Row(verticalAlignment = CenterVertically) {
                IconButton(
                    onClick = { appState.goBack() },
                    enabled = appState.backStack.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (appState.backStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                IconButton(
                    onClick = { appState.goForward() },
                    enabled = appState.forwardStack.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (appState.forwardStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // UNIFIED HISTORY DROPDOWN (MOVE TO RIGHT OF FORWARD)
                if (appState.getScreenSize() != ScreenSize.SMALL){
                    Box {
                        IconButton(
                            onClick = { historyMenuExpanded = true },
                            enabled = appState.backStack.isNotEmpty() || appState.forwardStack.isNotEmpty(),
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "History",
                                modifier = Modifier.size(16.dp),
                                tint = if (appState.backStack.isNotEmpty() || appState.forwardStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        DropdownMenu(
                            expanded = historyMenuExpanded,
                            onDismissRequest = { historyMenuExpanded = false }
                        ) {
                            // Forward History (Latest at top)
                            appState.forwardStack.asReversed()
                                .forEachIndexed { reversedIndex, location ->
                                    val unifiedIndex =
                                        appState.backStack.size + 1 + (appState.forwardStack.size - 1 - reversedIndex)
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                appState.getLocationName(location),
                                                maxLines = 1
                                            )
                                        },
                                        onClick = {
                                            appState.jumpToHistory(unifiedIndex)
                                            historyMenuExpanded = false
                                        }
                                    )
                                }

                            // Current Location (with Checkmark)
                            val currentNav = NavLocation(
                                path = appState.currentPath,
                                uri = appState.currentSafUri,
                                archiveFile = appState.currentArchiveFile,
                                archiveUri = appState.currentArchiveUri,
                                archivePath = appState.currentArchivePath,
                                safStack = ArrayList(appState.safStack)
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        appState.getLocationName(currentNav),
                                        maxLines = 1,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { historyMenuExpanded = false }
                            )

                            // Back History (Most recent just below current)
                            appState.backStack.asReversed()
                                .forEachIndexed { reversedIndex, location ->
                                    val originalIndex = appState.backStack.size - 1 - reversedIndex
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                appState.getLocationName(location),
                                                maxLines = 1
                                            )
                                        },
                                        onClick = {
                                            appState.jumpToHistory(originalIndex)
                                            historyMenuExpanded = false
                                        }
                                    )
                                }
                        }
                    }
                }
            }

            // UP BUTTON

            if (appState.getScreenSize() != ScreenSize.SMALL){
                IconButton(
                    onClick = { appState.goUp() },
                    enabled = appState.canGoUp
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Up",
                        tint = if (appState.canGoUp) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // REFRESH / CANCEL BUTTON
                if (showCancelButton) {
                    IconButton(onClick = { appState.cancelLoading() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Loading")
                    }
                } else {
                    // Show Refresh button if not loading, or if loading hasn't reached the 400ms mark yet
                    IconButton(onClick = { if (!appState.isLoading) appState.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            // Breadcrumbs / Address Bar Area
            Surface(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .weight(1f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        if (!addressBar && !searchBar) addressBar = true
                    },
                color = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small
            ) {
                if (searchBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        verticalAlignment = CenterVertically
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .focusProperties {
                                    down = FocusRequester.Cancel
                                    up = FocusRequester.Cancel
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) {
                                        true // Consume Up/Down keys to prevent focus from leaving the search bar
                                    } else {
                                        false
                                    }
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    appState.performSearch(searchQuery.text, searchSubfolders)
                                }
                            ),
                            decorationBox = { innerTextField ->
                                if (searchQuery.text.isEmpty()) {
                                    Text(
                                        text = "Search...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Box {
                            IconButton(onClick = { searchOptionsMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Search Options")
                            }

                            DropdownMenu(
                                expanded = searchOptionsMenuExpanded,
                                onDismissRequest = { 
                                    searchOptionsMenuExpanded = false
                                    searchKindMenuExpanded = false 
                                }
                            ) {
                                if (searchKindMenuExpanded) {
                                    DropdownMenuItem(
                                        text = { Text("Back") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                        onClick = { searchKindMenuExpanded = false }
                                    )
                                    HorizontalDivider()
                                    val kinds = listOf("folder", "document", "image", "video", "audio", "archive")
                                    kinds.forEach { kind ->
                                        DropdownMenuItem(
                                            text = { Text("kind:$kind") },
                                            onClick = {
                                                searchQuery = TextFieldValue(searchQuery.text + "kind:$kind ")
                                                searchOptionsMenuExpanded = false
                                                searchKindMenuExpanded = false
                                                focusRequester.requestFocus()
                                            }
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Search all subfolders") },
                                        trailingIcon = {
                                            Checkbox(checked = searchSubfolders, onCheckedChange = null)
                                        },
                                        onClick = { searchSubfolders = !searchSubfolders }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("kind: (File type)") },
                                        onClick = { searchKindMenuExpanded = true },
                                        trailingIcon = { Icon(Icons.Default.ChevronRight, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("AND") },
                                        onClick = {
                                            searchQuery = TextFieldValue(searchQuery.text + " AND ")
                                            searchOptionsMenuExpanded = false
                                            focusRequester.requestFocus()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("OR") },
                                        onClick = {
                                            searchQuery = TextFieldValue(searchQuery.text + " OR ")
                                            searchOptionsMenuExpanded = false
                                            focusRequester.requestFocus()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("NOT") },
                                        onClick = {
                                            searchQuery = TextFieldValue(searchQuery.text + " NOT ")
                                            searchOptionsMenuExpanded = false
                                            focusRequester.requestFocus()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("*.ext (Extension)") },
                                        onClick = {
                                            searchQuery = TextFieldValue(searchQuery.text + "*.zip ")
                                            searchOptionsMenuExpanded = false
                                            focusRequester.requestFocus()
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            searchBar = false
                            focusManager.clearFocus()
                            if (appState.isSearchMode) {
                                appState.refresh() // Reset to normal view if search was closed
                            }
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (!addressBar) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Scroll) {
                                            val delta = event.changes.first().scrollDelta
                                            coroutineScope.launch {
                                                breadcrumbScrollState.scrollBy(delta.y * 60f)
                                            }
                                        }
                                    }
                                }
                            }
                            .horizontalScroll(breadcrumbScrollState),
                        verticalAlignment = CenterVertically
                    ) {
                        if (appState.currentPath != null || appState.currentArchiveFile != null) {
                            visibleSegments.forEachIndexed { index, folderName ->
                                val displayName = if (folderName == "0") "Internal Storage" else folderName
                                
                                val realIndex = if (zeroIndex != -1) zeroIndex + index else index
                                val targetPathSegments = allSegments.take(realIndex + 1)
                                val targetPathString = "/" + targetPathSegments.joinToString("/")
                                val targetFile = File(targetPathString)

                                TextButton(
                                    onClick = {
                                        if (appState.currentArchiveFile != null) {
                                            val archiveFile = appState.currentArchiveFile!!
                                            val archivePath = archiveFile.absolutePath
                                            if (targetPathString == archivePath) {
                                                // Navigating to the root of the archive
                                                appState.navigateTo(null, null, archiveFile = archiveFile, archivePath = "")
                                            } else if (targetPathString.startsWith("$archivePath/")) {
                                                // Navigating to a folder inside the archive
                                                val innerPath = targetPathString.removePrefix("$archivePath/")
                                                appState.navigateTo(null, null, archiveFile = archiveFile, archivePath = "$innerPath/")
                                            } else {
                                                // Navigating outside the archive
                                                appState.navigateTo(targetFile, null)
                                            }
                                        } else {
                                            appState.navigateTo(targetFile, null)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = if (targetFile.isDirectory) Modifier.fileDropTarget(appState, destPath = targetFile) else Modifier
                                ) {
                                    Text(text = displayName, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (index < visibleSegments.size - 1) {
                                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else if (appState.currentArchiveUri != null) {
                            // Breadcrumbs for URI-based archives
                            val archiveName = appState.currentName // This should be the archive file name
                            val innerSegments = appState.currentArchivePath.split("/").filter { it.isNotEmpty() }
                            val segments = listOf(archiveName) + innerSegments

                            segments.forEachIndexed { index, segment ->
                                TextButton(
                                    onClick = {
                                        if (index == 0) {
                                            appState.navigateTo(null, null, archiveUri = appState.currentArchiveUri, archivePath = "")
                                        } else {
                                            val targetInnerPath = innerSegments.take(index).joinToString("/") + "/"
                                            appState.navigateTo(null, null, archiveUri = appState.currentArchiveUri, archivePath = targetInnerPath)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                    // Drop target not supported for inside URI-based archives yet
                                ) {
                                    Text(text = segment, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (index < segments.size - 1) {
                                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else if (appState.currentSafUri != null) {
                            // Full breadcrumbs for SAF
                            val safItems = appState.safStack.toList() + listOfNotNull(appState.currentSafUri)
                            safItems.forEachIndexed { index, uri ->
                                val doc = DocumentFile.fromTreeUri(context, uri)
                                val displayName = doc?.name ?: "Unknown"

                                TextButton(
                                    onClick = {
                                        // Navigation in SAF breadcrumbs
                                        if (index < appState.safStack.size) {
                                            val targetUri = appState.safStack[index]
                                            // We need to truncate the stack to this point
                                            repeat(appState.safStack.size - index) { 
                                                appState.safStack.removeAt(appState.safStack.lastIndex)
                                            }
                                            appState.navigateTo(null, targetUri)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.fileDropTarget(appState, destSafUri = uri)
                                ) {
                                    Text(text = displayName, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (index < safItems.size - 1) {
                                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            Text(
                                text = "Home",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        verticalAlignment = CenterVertically
                    ) {
                        BasicTextField(
                            value = textPathValue,
                            onValueChange = { textPathValue = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val input = textPathValue.text
                                    if (input.startsWith("/") || input.startsWith("content://")) {
                                        if (input.startsWith("/")) {
                                            val newFile = File(input)
                                            if (newFile.exists()) {
                                                if (newFile.isDirectory) {
                                                    appState.navigateTo(newFile, null)
                                                    addressBar = false
                                                    focusManager.clearFocus()
                                                } else if (com.example.continuum_explorer.utils.ZipUtils.isArchive(newFile.toUniversal())) {
                                                    appState.navigateTo(null, null, archiveFile = newFile, archivePath = "")
                                                    addressBar = false
                                                    focusManager.clearFocus()
                                                } else {
                                                    Toast.makeText(context, "Not a directory or archive", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "File or directory not found", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            addressBar = false
                                            focusManager.clearFocus()
                                        }
                                    } else {
                                        Toast.makeText(context, "Invalid path", Toast.LENGTH_SHORT).show()
                                        textPathValue = TextFieldValue(currentPathString)
                                    }
                                }
                            )
                        )

                        IconButton(onClick = {
                            addressBar = false
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            IconButton(onClick = {
                if (searchBar) {
                    appState.performSearch(searchQuery.text, searchSubfolders)
                } else {
                    searchBar = true
                    addressBar = false
                }
            }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            Box {
                IconButton(onClick = { optionsMenuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }

                DropdownMenu(
                    expanded = optionsMenuExpanded,
                    onDismissRequest = { optionsMenuExpanded = false }
                ) {

                    if (appState.getScreenSize() == ScreenSize.LARGE) {
                        DropdownMenuItem(
                            text = { Text(if (appState.appConfigs.isDetailsPaneVisible) "Hide Details Pane" else "Show Details Pane") },
                            onClick = {
                                optionsMenuExpanded = false
                                appState.appConfigs.toggleDetailsPaneVisibility()
                            },
                            leadingIcon = { Icon(Icons.Default.Info, null) }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            optionsMenuExpanded = false
                            val intent = Intent(context, SettingsActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, "Settings") }
                    )
                }
            }
        }
    }
}

private fun File.toUniversal() = com.example.continuum_explorer.model.UniversalFile(
    name = name,
    isDirectory = isDirectory,
    lastModified = lastModified(),
    length = length(),
    fileRef = this,
    absolutePath = absolutePath
)