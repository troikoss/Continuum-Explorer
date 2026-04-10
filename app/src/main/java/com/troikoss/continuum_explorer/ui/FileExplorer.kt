package com.troikoss.continuum_explorer.ui

import android.content.Context
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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.managers.DetailsMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.ScreenSize
import com.troikoss.continuum_explorer.ui.components.*
import com.troikoss.continuum_explorer.utils.*
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
    val scope = rememberCoroutineScope()

    // --- Tab Management ---
    val tabs = remember { mutableStateListOf<FileExplorerState>() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

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

    // Initialize first tab if empty
    LaunchedEffect(Unit) {
        if (tabs.isEmpty()) {
            val firstState = createNewTabState(context, scope)
            when {
                initialArchive != null -> firstState.navigateTo(null, null, addToHistory = false, archiveFile = initialArchive, archivePath = "")
                initialArchiveUri != null -> firstState.navigateTo(null, null, addToHistory = false, archiveUri = initialArchiveUri, archivePath = "")
                initialPath != null -> firstState.navigateTo(File(initialPath), null, addToHistory = false)
                initialUri != null -> firstState.navigateTo(null, Uri.parse(initialUri), addToHistory = false)
            }
            tabs.add(firstState)
        }
    }

    if (tabs.isEmpty()) return // Wait for initialization

    val safeIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val appState = tabs[safeIndex]
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // --- Storage Access Framework Launcher ---
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        appState.handleSafResult(uri)
    }

    // --- Side Effects ---
    LaunchedEffect(appState, appState.currentPath, appState.currentSafUri, appState.folderConfigs.sortParams, 
                   appState.currentArchiveFile, appState.currentArchiveUri, appState.currentArchivePath, appState.isRecentMode) {
        appState.triggerLoad()
    }

    BackHandler(enabled = appState.canGoUp || appState.selectionManager.selectedItems.isNotEmpty()) {
        if (appState.selectionManager.selectedItems.isNotEmpty()) {
            appState.selectionManager.clear()
        } else appState.goUp()
    }

    // --- Main Layout ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        appState.isShiftPressed = event.keyboardModifiers.isShiftPressed
                        val activeChange = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull()
                        if (activeChange != null) {
                            appState.isMouseInteraction = (activeChange.type == PointerType.Mouse)
                        }
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
                    NavigationContent(
                        appState = appState,
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                        onAddStorage = { safLauncher.launch(null) }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    ExplorerTopBar(
                        tabs = tabs,
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
                        onMenuClick = { scope.launch { drawerState.open() } },
                        appState = appState
                    )
                }
            ) { innerPadding ->
                ExplorerBody(
                    modifier = Modifier.padding(innerPadding),
                    appState = appState,
                    onAddStorage = { safLauncher.launch(null) }
                )
            }
        }
    }
}

@Composable
private fun NavigationContent(
    appState: FileExplorerState,
    onCloseDrawer: () -> Unit,
    onAddStorage: () -> Unit
) {
    val context = LocalContext.current
    NavigationPane(
        appState = appState,
        onItemSelected = { sectionIndex ->
            navigateToSection(appState, context, sectionIndex)
            onCloseDrawer()
        },
        onSafItemSelected = { uri ->
            appState.navigateTo(null, uri)
            onCloseDrawer()
        },
        onAddStorageClick = onAddStorage,
        onNavigate = onCloseDrawer
    )
}

@Composable
private fun ExplorerTopBar(
    tabs: List<FileExplorerState>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Int) -> Unit,
    onMenuClick: () -> Unit,
    appState: FileExplorerState
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        TabBar(
            tabs = tabs.map { it.currentName },
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            onAddTab = onAddTab,
            onCloseTab = onCloseTab,
            appState = appState
        )

        TopBar(
            onMenuClick = onMenuClick,
            appState = appState
        )

        if (SettingsManager.isCommandBarVisible.value) {
            CommandBar(appState = appState)
        }
    }
}

@Composable
private fun ExplorerBody(
    modifier: Modifier = Modifier,
    appState: FileExplorerState,
    onAddStorage: () -> Unit
) {
    val context = LocalContext.current
    val screenSize = appState.getScreenSize()
    val navPaneWidth = appState.appConfigs.navPaneWidth
    val detailsPaneWidth = appState.appConfigs.detailsPaneWidth

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            // Navigation Pane (Side)
            if (screenSize != ScreenSize.SMALL) {
                PermanentDrawerSheet(
                    modifier = Modifier.width(navPaneWidth),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    NavigationPane(
                        appState = appState,
                        onItemSelected = { navigateToSection(appState, context, it) },
                        onSafItemSelected = { appState.navigateTo(null, it) },
                        onAddStorageClick = onAddStorage
                    )
                }

                VerticalResizeHandle(
                    onResize = { delta ->
                        appState.appConfigs.navPaneWidth = (appState.appConfigs.navPaneWidth + delta).coerceIn(200.dp, 300.dp)
                        appState.appConfigs.savePaneWidths()
                    }
                )
            }

            // Main Content Area
            Box(modifier = Modifier.weight(1f)) {
                FileContent(appState = appState)
            }

            // Details Pane
            if (screenSize == ScreenSize.LARGE && SettingsManager.detailsMode.value == DetailsMode.PANE) {
                VerticalResizeHandle(
                    onResize = { delta ->
                        appState.appConfigs.detailsPaneWidth = (appState.appConfigs.detailsPaneWidth - delta).coerceIn(200.dp, 300.dp)
                        appState.appConfigs.savePaneWidths()
                    }
                )

                DetailsPane(
                    appState = appState,
                    modifier = Modifier.width(detailsPaneWidth)
                )
            }
        }

        // Details Bar (Bottom)
        if (screenSize == ScreenSize.LARGE && SettingsManager.detailsMode.value == DetailsMode.BAR) {
            HorizontalDivider()
            DetailsBar(appState = appState)
        }
    }
}

private fun navigateToSection(appState: FileExplorerState, context: Context, index: Int) {
    val internalRoot = Environment.getExternalStorageDirectory()
    when {
        index == 6 -> appState.navigateTo(internalRoot, null, newRoot = internalRoot)
        index == 7 -> {
            val trashDir = File(internalRoot, ".Trash")
            if (!trashDir.exists()) trashDir.mkdirs()
            appState.navigateTo(trashDir, null, newRoot = internalRoot)
        }
        index == 8 -> appState.navigateTo(null, null, isRecent = true)
        index >= 100 -> {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volumes = storageManager.storageVolumes
            val volumeIndex = index - 100

            if (volumeIndex < volumes.size) {
                val volume = volumes[volumeIndex]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory?.let { appState.navigateTo(it, null, newRoot = it) }
                } else {
                    val externalDirs = context.getExternalFilesDirs(null)
                    if (volumeIndex < externalDirs.size) {
                        externalDirs[volumeIndex]?.let { dir ->
                            val root = File(dir.absolutePath.split("/Android")[0])
                            appState.navigateTo(root, null, newRoot = root)
                        }
                    }
                }
            }
        }
    }
}
