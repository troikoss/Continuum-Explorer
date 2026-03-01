package com.example.continuum_explorer.ui

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.xr.compose.testing.toDp
import com.example.continuum_explorer.model.ScreenSize
import com.example.continuum_explorer.utils.DetailsMode
import com.example.continuum_explorer.utils.SettingsManager
import com.example.continuum_explorer.utils.VerticalResizeHandle
import com.example.continuum_explorer.utils.ZipUtils
import com.example.continuum_explorer.utils.fileDropTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * The main layout container for the File Explorer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorer(
    initialPath: String? = null, 
    initialUri: String? = null,
    initialArchive: File? = null,
    initialArchiveUri: Uri? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val tabs = remember { mutableStateListOf<FileExplorerState>() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Helper to create a new state with the proper callback
    fun createNewTabState(ctx: Context, scp: CoroutineScope): FileExplorerState {
        return FileExplorerState(ctx, scp).apply {
            onOpenInNewTab = { item ->
                val newState = createNewTabState(ctx, scp)
                if (ZipUtils.isArchive(item) && item.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) {
                    newState.navigateTo(
                        newPath = null,
                        newUri = null,
                        addToHistory = false,
                        archiveFile = item.fileRef,
                        archivePath = ""
                    )
                } else if (item.fileRef != null) {
                    newState.navigateTo(item.fileRef, null, addToHistory = false)
                } else if (item.documentFileRef != null) {
                    newState.navigateTo(null, item.documentFileRef.uri, addToHistory = false)
                }
                tabs.add(newState)
                selectedTabIndex = tabs.size - 1
            }
        }
    }

    if (tabs.isEmpty()) {
        val firstState = createNewTabState(context, scope)
        if (initialArchive != null) {
            // Deprecated path: Archive Mode via File
            firstState.navigateTo(
                newPath = null, 
                newUri = null, 
                addToHistory = false,
                archiveFile = initialArchive,
                archivePath = ""
            )
        } else if (initialArchiveUri != null) {
            // New Archive Mode via Uri
            firstState.navigateTo(
                newPath = null, 
                newUri = null, 
                addToHistory = false,
                archiveUri = initialArchiveUri,
                archivePath = ""
            )
        } else if (initialPath != null) {
            firstState.navigateTo(File(initialPath), null, addToHistory = false)
        } else if (initialUri != null) {
            firstState.navigateTo(null, Uri.parse(initialUri), addToHistory = false)
        }
        tabs.add(firstState)
    }

    val safeIndex = selectedTabIndex.coerceIn(0, tabs.size - 1)
    val appState = tabs[safeIndex]

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        appState.handleSafResult(uri)
    }

    LaunchedEffect(appState, appState.currentPath, appState.currentSafUri, appState.folderConfigs.sortParams, appState.currentArchiveFile, appState.currentArchiveUri, appState.currentArchivePath, appState.isRecentMode) {
        appState.triggerLoad()
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    BackHandler(enabled = appState.canGoUp) {
        appState.goUp()
    }

    // Helper to handle navigation selection
    val navigateToSection: (Int) -> Unit = { index ->
        val internalRoot = Environment.getExternalStorageDirectory()
        when {
//            index == 1 -> appState.navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), null, newRoot = internalRoot)
//            index == 2 -> appState.navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), null, newRoot = internalRoot)
//            index == 3 -> appState.navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), null, newRoot = internalRoot)
//            index == 4 -> appState.navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), null, newRoot = internalRoot)
//            index == 5 -> appState.navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), null, newRoot = internalRoot)
            index == 6 -> appState.navigateTo(internalRoot, null, newRoot = internalRoot)
            index == 7 -> {
                val trashDir = File(internalRoot, ".Trash")
                if (!trashDir.exists()) trashDir.mkdirs()
                appState.navigateTo(trashDir, null, newRoot = internalRoot)
            }
            index == 8 -> {
                appState.navigateTo(null, null, isRecent = true)
            }
            index >= 100 -> {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val volumes = storageManager.storageVolumes
                val volumeIndex = index - 100

                // Look at the original full list, just like NavigationPane does
                if (volumeIndex < volumes.size) {
                    val volume = volumes[volumeIndex]
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val directory = volume.directory
                        if (directory != null) {
                            // Set the SD card root as both the current path AND the new root
                            appState.navigateTo(directory, null, newRoot = directory)
                        }
                    } else {
                        // Fallback for older versions
                        val externalDirs = context.getExternalFilesDirs(null)
                        if (volumeIndex < externalDirs.size) {
                            val dir = externalDirs[volumeIndex]
                            if (dir != null) {
                                val root = File(dir.absolutePath.split("/Android")[0])
                                appState.navigateTo(root, null, newRoot = root)
                            }
                        }
                    }
                }
            }
        }
    }

    val navPaneWidthPx = appState.appConfigs.navPaneWidthPx
    val detailsPaneWidthPx = appState.appConfigs.detailsPaneWidthPx

    val navPaneWidthDp = with(density) { navPaneWidthPx.toDp() }
    val detailsPaneWidthDp = with(density) { detailsPaneWidthPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        appState.isShiftPressed = event.keyboardModifiers.isShiftPressed
                        appState.isMouseInteraction =
                            event.changes.any { it.type == PointerType.Mouse }
                    }
                }
            }
            .fileDropTarget(appState) 
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = appState.getScreenSize() == ScreenSize.SMALL,
            drawerContent = {
                ModalDrawerSheet {
                    NavigationPane(
                        appState = appState,
                        onItemSelected = {
                            navigateToSection(it)
                            scope.launch { drawerState.close() }
                        },
                        onSafItemSelected = { uri ->
                            appState.navigateTo(null, uri)
                            scope.launch { drawerState.close() }
                        },
                        onAddStorageClick = {
                            safLauncher.launch(null)
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    val backgroundColor = MaterialTheme.colorScheme.surface
                    Column(
                        modifier = Modifier
                            .background(backgroundColor)
                            .statusBarsPadding()
                    ) {
                        TabBar(
                            tabs = tabs.map { it.currentName },
                            selectedTabIndex = safeIndex,
                            onTabSelected = { selectedTabIndex = it },
                            onAddTab = {
                                tabs.add(createNewTabState(context, scope))
                                selectedTabIndex = tabs.size - 1
                            },
                            onCloseTab = { index ->
                                if (tabs.size > 1) {
                                    tabs.removeAt(index)
                                    if (selectedTabIndex >= tabs.size) {
                                        selectedTabIndex = (tabs.size - 1).coerceAtLeast(0)
                                    }
                                }
                            },
                            appState = appState
                        )

                        TopBar(
                            isLandscape = isLandscape,
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            },
                            onAddStorageClick = {
                                safLauncher.launch(null)
                            },
                            appState = appState
                        )

                        CommandBar(
                            appState = appState
                        )
                    }
                }
            ) { innerPadding ->
                Column (
                    modifier = Modifier
                        .fillMaxSize()

                ){
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(innerPadding)
                    ) {
                        if (appState.getScreenSize() != ScreenSize.SMALL) {
                            PermanentDrawerSheet(
                                modifier = Modifier.width(navPaneWidthDp)
                            ) {
                                NavigationPane(
                                    appState = appState,
                                    onItemSelected = {
                                        navigateToSection(it)
                                    },
                                    onSafItemSelected = { uri ->
                                        appState.navigateTo(null, uri)
                                    },
                                    onAddStorageClick = {
                                        safLauncher.launch(null)
                                    }
                                )
                            }
                            VerticalResizeHandle(onResize = { delta ->
                                appState.appConfigs.navPaneWidthPx = (appState.appConfigs.navPaneWidthPx + delta).coerceIn(350f, 700f)
                                appState.appConfigs.savePaneWidths()
                            },
                                contentAlignment = Alignment.CenterStart
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            FileContent(
                                appState = appState
                            )
                        }

                        if (appState.getScreenSize() == ScreenSize.LARGE && SettingsManager.detailsMode.value == DetailsMode.PANE) {
                            VerticalResizeHandle(onResize = { delta ->
                                appState.appConfigs.detailsPaneWidthPx = (appState.appConfigs.detailsPaneWidthPx - delta).coerceIn(200f, 700f)
                                appState.appConfigs.savePaneWidths()
                            },
                                contentAlignment = Alignment.CenterEnd
                            )

                            DetailsPane(
                                appState = appState,
                                modifier = Modifier.width(detailsPaneWidthDp)
                            )
                        }
                    }

                    if (appState.getScreenSize() == ScreenSize.LARGE && SettingsManager.detailsMode.value == DetailsMode.BAR) {
                        HorizontalDivider()

                        DetailsBar(
                            appState = appState
                        )
                    }

                }
            }
        }
    }
}