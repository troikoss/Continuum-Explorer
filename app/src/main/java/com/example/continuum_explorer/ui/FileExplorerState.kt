package com.example.continuum_explorer.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.example.continuum_explorer.MainActivity
import com.example.continuum_explorer.PopUpActivity
import com.example.continuum_explorer.model.*
import com.example.continuum_explorer.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Manages the core state and logic for the File Explorer.
 * It handles file navigation, sorting, and selection state.
 */
class FileExplorerState(
    val context: Context,
    private val scope: CoroutineScope
) {
    // Configuration managers
    val folderConfigs = FolderConfigurations(context)
    val appConfigs = AppConfigurations(context)

    val appSettings = AppConfigurations(context)

    // Reference to the main storage root to prevent navigating into system files
    var storageRoot by mutableStateOf<File>(Environment.getExternalStorageDirectory())

    // Current directory or SAF URI being viewed
    var currentPath by mutableStateOf<File?>(Environment.getExternalStorageDirectory())
    var currentSafUri by mutableStateOf<Uri?>(null)
    
    // Flag for special virtual locations
    var isRecentMode by mutableStateOf(false)

    // Archive Navigation State
    var currentArchiveFile by mutableStateOf<File?>(null)
    var currentArchiveUri by mutableStateOf<Uri?>(null)
    var currentArchivePath by mutableStateOf("")
    
    // Cache for current archive structure: Path -> List of Files
    var archiveCache: Map<String, List<UniversalFile>>? = null
    
    // The processed and sorted list of files to display
    var files by mutableStateOf(emptyList<UniversalFile>())
    
    // Loading state
    var isLoading by mutableStateOf(false)
    private var loadingJob: Job? = null

    // A stable key that only updates once the files for a directory have actually loaded.
    var loadedPathKey by mutableStateOf<String>("initial")
        private set

    // Tracks if shift key is currently pressed (used for drag and drop logic)
    var isShiftPressed by mutableStateOf(false)
    
    // Tracks the last pointer type for drag logic
    var isMouseInteraction by mutableStateOf(false)

    // Centralized selection manager
    val selectionManager = SelectionManager()

    // Date formatter for consistent date display
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Stack to track navigation hierarchy for SAF (Storage Access Framework)
    val safStack = mutableStateListOf<Uri>()

    var scrollToItemIndex by mutableStateOf<Int?>(null)
        private set

    val backStack = mutableStateListOf<NavLocation>()
    val forwardStack = mutableStateListOf<NavLocation>()

    // Callback to open a new tab
    var onOpenInNewTab: ((UniversalFile) -> Unit)? = null

    var isSearchMode by mutableStateOf(false)
    var isSearchUIActive by mutableStateOf(false)

    val activeViewMode: ViewMode
        @Composable
        get() {
            val isSmallScreen = getScreenSize() == ScreenSize.SMALL

            return if (isSmallScreen && folderConfigs.viewMode == ViewMode.DETAILS) {
                ViewMode.CONTENT // Disable Details on small screens
            } else {
                folderConfigs.viewMode // Otherwise, use the saved mode
            }
        }

    init {
        val key = getCurrentStorageKey()
        folderConfigs.resolveViewMode(key)
        folderConfigs.resolveSortParams(key)
        folderConfigs.resolveGridSize(key)
        
        // Listen for global refresh events from other windows
        scope.launch {
            GlobalEvents.refreshEvent.collect {
                refresh()
            }
        }

        // Listen for config sync events (like favorite updates)
        scope.launch {
            GlobalEvents.configChangeEvent.collect {
                appConfigs.reload()
            }
        }
    }


    fun getSafDisplayName(uri: Uri): String {
        if (uri.authority == "com.android.externalstorage.documents") {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val rootId = split[0]
            
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                storageManager.storageVolumes.forEach { volume ->
                    val volumeUuid = volume.uuid ?: "primary"
                    if (volumeUuid == rootId) {
                        return volume.getDescription(context)
                    }
                }
            }
            return if (rootId == "primary") "Internal Storage" else rootId
        }

        val pm = context.packageManager
        val providerInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveContentProvider(uri.authority!!, PackageManager.ComponentInfoFlags.of(0))
            } else {
                pm.resolveContentProvider(uri.authority!!, 0)
            }
        } catch (e: Exception) {
            null
        }
        
        val appName = providerInfo?.loadLabel(pm)?.toString() ?: ""
        if (appName.isNotEmpty()) return appName

        val doc = DocumentFile.fromTreeUri(context, uri)
        return doc?.name ?: "External Location"
    }

    val currentName: String
        get() {
            return if (isInRecycleBin) {
                "Recycle Bin"
            } else if (isRecentMode) {
                "Recent"
            } else if (currentArchiveFile != null) {
                currentArchiveFile?.name ?: "Archive"
            } else if (currentArchiveUri != null) {
                "Archive"
            } else if (currentPath != null) {
                if (currentPath?.absolutePath == storageRoot.absolutePath) {
                     if (storageRoot.absolutePath == Environment.getExternalStorageDirectory().absolutePath) "Internal Storage"
                     else "SD Card"
                }
                else currentPath?.name ?: "Unknown"
            } else if (currentSafUri != null) {
                getSafDisplayName(currentSafUri!!)
            } else {
                "New Tab"
            }
        }

    val currentUniversalPath: UniversalFile?
        get() = when {
            currentArchiveFile != null -> currentArchiveFile?.toUniversal()

            currentSafUri != null -> {
                val doc = DocumentFile.fromTreeUri(context, currentSafUri!!)
                doc?.toUniversal()
            }

            currentPath != null -> currentPath?.toUniversal()

            else -> null
        }

    val isInRecycleBin: Boolean
        get() = !isRecentMode && currentPath?.absolutePath == File(Environment.getExternalStorageDirectory(), ".Trash").absolutePath

    val canGoUp: Boolean
        get() = if (currentArchiveFile != null || currentArchiveUri != null) {
            true
        } else if (isRecentMode) {
            false
        } else if (currentPath != null) {
            currentPath?.absolutePath != storageRoot.absolutePath
        } else if (currentSafUri != null) {
            safStack.isNotEmpty()
        } else {
            false
        }

    fun onScrollToItemCompleted() {
        scrollToItemIndex = null
    }

    fun refresh() {
        if (isSearchMode) {
            isSearchMode = false
        }
        triggerLoad(forceRefresh = true)
    }
    
    fun performSearch(query: String, searchSubfolders: Boolean) {
        if (query.isBlank()) {
            if (isSearchMode) {
                isSearchMode = false
                triggerLoad()
            }
            return
        }
        
        loadingJob?.cancel()
        loadingJob = scope.launch {
            withContext(Dispatchers.Main) {
                isSearchMode = true
                isLoading = true
                files = emptyList() // Clear previous results immediately
                val key = getCurrentStorageKey()
                folderConfigs.resolveViewMode(key)
                folderConfigs.resolveSortParams(key)
                folderConfigs.resolveGridSize(key)
                loadedPathKey = key ?: "search_results"
            }
            try {
                // Parse if we need to load archives
                if ((currentArchiveFile != null || currentArchiveUri != null) && archiveCache == null) {
                    val source: Any = currentArchiveFile ?: currentArchiveUri!!
                    archiveCache = withContext(Dispatchers.IO) { ZipUtils.parseArchive(context, source) }
                }
            
                val results = SearchManager.search(
                    context = context,
                    query = query,
                    currentPath = currentPath,
                    currentSafUri = currentSafUri,
                    searchSubfolders = searchSubfolders,
                    archiveCache = archiveCache,
                    currentArchivePath = currentArchivePath
                )
                
                withContext(Dispatchers.Main) {
                    files = sortFiles(results)
                    selectionManager.allFiles = files
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, "Search failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleSafResult(uri: Uri?) {
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)

            if (!appConfigs.addedSafUris.contains(uri)) {
                appConfigs.addedSafUris.add(uri)
                appConfigs.saveAddedSafUris()
            }

            safStack.clear()
            navigateTo(null, uri)
        }
    }

    fun removeSafUri(uri: Uri) {
        if (appConfigs.addedSafUris.contains(uri)) {
            appConfigs.addedSafUris.remove(uri)
            appConfigs.saveAddedSafUris()
            
            if (currentSafUri == uri || (currentSafUri != null && currentSafUri.toString().startsWith(uri.toString()))) {
                navigateTo(Environment.getExternalStorageDirectory(), null)
            }

            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            } catch (e: Exception) {}
        }
    }

    fun triggerLoad(forceRefresh: Boolean = false) {
        if (isSearchMode) return
        loadingJob?.cancel()
        loadingJob = scope.launch {
            loadFiles(forceRefresh)
        }
    }

    fun cancelLoading() {
        loadingJob?.cancel()
        isLoading = false
    }

    private var pendingFocusPath: File? = null
    private var pendingFocusUri: Uri? = null

    private suspend fun loadFiles(forceRefresh: Boolean = false) {
        isLoading = true
        if (currentArchiveFile == null && currentArchiveUri == null) {
            archiveCache = null
        }
        if (forceRefresh) {
            archiveCache = null
        }

        try {
            val universalList: List<UniversalFile> = withContext(Dispatchers.IO) {
                if (isRecentMode) {
                    RecentFilesManager.getRecentFiles(context)
                } else if (currentArchiveFile != null || currentArchiveUri != null) {
                    if (archiveCache == null) {
                        val source: Any = currentArchiveFile ?: currentArchiveUri!!
                        archiveCache = ZipUtils.parseArchive(context, source)
                    }
                    archiveCache?.get(currentArchivePath) ?: emptyList()
                } else if (currentPath != null) {
                    val rawFiles = currentPath!!.listFiles()?.toList() ?: emptyList()
                    rawFiles.filter { it.name != ".metadata" }.map { it.toUniversal() }
                } else if (currentSafUri != null) {
                    val docFile = DocumentFile.fromTreeUri(context, currentSafUri!!)
                    val rawDocs = docFile?.listFiles()?.toList() ?: emptyList()
                    rawDocs.map { it.toUniversal() }
                } else {
                    emptyList()
                }
            }

            withContext(Dispatchers.Main) {
                val key = getCurrentStorageKey()
                folderConfigs.resolveViewMode(key)
                folderConfigs.resolveSortParams(key)
                folderConfigs.resolveGridSize(key)
                
                val sorted = if (isRecentMode) universalList else sortFiles(universalList)
                
                files = sorted
                
                if (pendingFocusPath != null || pendingFocusUri != null) {
                    val itemToFind = files.find { item ->
                        if (pendingFocusPath != null) {
                            item.fileRef?.absolutePath == pendingFocusPath?.absolutePath
                        } else {
                            item.documentFileRef?.uri == pendingFocusUri
                        }
                    }

                    if (itemToFind != null) {
                        selectionManager.setFocus(itemToFind)
                        val index = files.indexOf(itemToFind)
                        if (index != -1) {
                            scrollToItemIndex = index
                        }
                    }
                    pendingFocusPath = null
                    pendingFocusUri = null
                }
                
                loadedPathKey = getCurrentStorageKey() ?: if (isRecentMode) "recent" else "root"
                selectionManager.allFiles = files
                isLoading = false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    private fun sortFiles(rawList: List<UniversalFile>): List<UniversalFile> {
        val params = folderConfigs.sortParams
        return rawList.sortedWith { f1, f2 ->
            if (f1.isDirectory && !f2.isDirectory) return@sortedWith -1
            if (!f1.isDirectory && f2.isDirectory) return@sortedWith 1

            val result = when (params.columnType) {
                FileColumnType.NAME -> f1.name.lowercase().compareTo(f2.name.lowercase())
                FileColumnType.DATE -> f1.lastModified.compareTo(f2.lastModified)
                FileColumnType.SIZE -> f1.length.compareTo(f2.length)
            }
            if (params.order == SortOrder.Ascending) result else -result
        }
    }

    fun formatDate(timestamp: Long): String = dateFormatter.format(timestamp)
    fun formatSize(size: Long): String = Formatter.formatFileSize(context, size)

    fun getCurrentStorageKey(): String? {
        if (isSearchMode) return "search_results"
        return if (currentArchiveFile != null || currentArchiveUri != null) {
            val base = currentArchiveFile?.absolutePath ?: currentArchiveUri.toString()
            "archive:$base:${currentArchivePath.removeSuffix("/")}"
        } else if (currentPath != null) {
            currentPath?.absolutePath
        } else if (currentSafUri != null) {
            currentSafUri.toString()
        } else {
            null
        }
    }

    fun navigateTo(
        newPath: File?, 
        newUri: Uri?, 
        newRoot: File? = null, 
        addToHistory: Boolean = true,
        archiveFile: File? = null,
        archiveUri: Uri? = null,
        archivePath: String? = null,
        isRecent: Boolean = false
    ) {
        if (addToHistory) {
            backStack.add(NavLocation(currentPath, currentSafUri, currentArchiveFile, currentArchiveUri, currentArchivePath, ArrayList(safStack)))
            forwardStack.clear()
        }
        
        val isNewArchive = (archiveFile != null && archiveFile != currentArchiveFile) || 
                           (archiveUri != null && archiveUri != currentArchiveUri)
        if (isNewArchive) {
            archiveCache = null
        }

        if (newRoot != null) {
            storageRoot = newRoot
        }
        currentPath = newPath
        currentSafUri = newUri
        currentArchiveFile = archiveFile
        currentArchiveUri = archiveUri
        currentArchivePath = archivePath ?: ""
        isRecentMode = isRecent
        isSearchMode = false
        
        scrollToItemIndex = null
        selectionManager.reset()
        triggerLoad()
    }

    fun goBack() {
        if (backStack.isNotEmpty()) {
            val leavingPath = currentPath
            val leavingUri = currentSafUri

            val lastLocation = backStack.removeAt(backStack.size - 1)
            forwardStack.add(NavLocation(currentPath, currentSafUri, currentArchiveFile, currentArchiveUri, currentArchivePath, ArrayList(safStack)))

            navigateTo(
                newPath = lastLocation.path, 
                newUri = lastLocation.uri, 
                addToHistory = false,
                archiveFile = lastLocation.archiveFile,
                archiveUri = lastLocation.archiveUri,
                archivePath = lastLocation.archivePath
            )
            
            if (lastLocation.safStack != null) {
                safStack.clear()
                safStack.addAll(lastLocation.safStack)
            }
            
            focusItemInList(leavingPath, leavingUri)
        }
    }

    fun goForward() {
        if (forwardStack.isNotEmpty()) {
            val nextLocation = forwardStack.removeAt(forwardStack.size - 1)
            backStack.add(NavLocation(currentPath, currentSafUri, currentArchiveFile, currentArchiveUri, currentArchivePath, ArrayList(safStack)))

            navigateTo(
                newPath = nextLocation.path, 
                newUri = nextLocation.uri, 
                addToHistory = false,
                archiveFile = nextLocation.archiveFile,
                archiveUri = nextLocation.archiveUri,
                archivePath = nextLocation.archivePath
            )

            if (nextLocation.safStack != null) {
                safStack.clear()
                safStack.addAll(nextLocation.safStack)
            }
        }
    }

    fun getLocationName(location: NavLocation): String {
        return if (location.archiveFile != null) {
            val base = location.archiveFile.name
            val inner = location.archivePath ?: ""
            if (inner.isEmpty()) base else "$base/${inner.removeSuffix("/")}"
        } else if (location.archiveUri != null) {
            val base = "Archive"
            val inner = location.archivePath ?: ""
            if (inner.isEmpty()) base else "$base/${inner.removeSuffix("/")}"
        } else if (location.path != null) {
            if (location.path.absolutePath == storageRoot.absolutePath) {
                if (storageRoot.absolutePath == Environment.getExternalStorageDirectory().absolutePath) "Internal Storage"
                else "SD Card"
            } else {
                location.path.name
            }
        } else if (location.uri != null) {
            getSafDisplayName(location.uri)
        } else {
            "New Tab"
        }
    }

    fun jumpToHistory(index: Int) {
        val allLocations = backStack.toList() + 
            listOf(NavLocation(currentPath, currentSafUri, currentArchiveFile, currentArchiveUri, currentArchivePath, ArrayList(safStack))) +
            forwardStack.asReversed()
        
        if (index < 0 || index >= allLocations.size) return
        
        val target = allLocations[index]
        val currentIndex = backStack.size
        
        if (index == currentIndex) return
        
        if (index < currentIndex) {
            val toForward = allLocations.subList(index + 1, currentIndex + 1)
            forwardStack.addAll(toForward.asReversed())
            repeat(currentIndex - index) {
                backStack.removeAt(backStack.size - 1)
            }
        } else {
            val toBack = allLocations.subList(currentIndex, index)
            backStack.addAll(toBack)
            repeat(index - currentIndex) {
                forwardStack.removeAt(forwardStack.size - 1)
            }
        }

        navigateTo(
            newPath = target.path,
            newUri = target.uri,
            addToHistory = false,
            archiveFile = target.archiveFile,
            archiveUri = target.archiveUri,
            archivePath = target.archivePath
        )
        
        if (target.safStack != null) {
            safStack.clear()
            safStack.addAll(target.safStack)
        }
    }

    fun goUp() {
        var destinationPath: File? = null
        var destinationUri: Uri? = null
        var leavingPath: File? = null
        var leavingUri: Uri? = null
        var canPerformGoUp = false
        
        var nextArchiveFile: File? = null
        var nextArchiveUri: Uri? = null
        var nextArchivePath: String? = null

        if (currentArchiveFile != null || currentArchiveUri != null) {
            if (currentArchivePath.isEmpty()) {
                if (currentArchiveFile != null) {
                    destinationPath = currentArchiveFile?.parentFile
                    leavingPath = currentArchiveFile
                }
                if (destinationPath != null) {
                    canPerformGoUp = true
                    nextArchiveFile = null
                    nextArchiveUri = null
                }
            } else {
                val parentPath = File(currentArchivePath).parent?.replace("\\", "/") ?: ""
                nextArchivePath = if (parentPath.isEmpty() || parentPath == ".") "" else "$parentPath/"
                nextArchiveFile = currentArchiveFile
                nextArchiveUri = currentArchiveUri
                canPerformGoUp = true
            }
        } else if (currentPath != null) {
            if (currentPath?.absolutePath == storageRoot.absolutePath) {
                Toast.makeText(context, "Already at storage root", Toast.LENGTH_SHORT).show()
                return
            }

            val parent = currentPath?.parentFile
            if (parent != null && parent.exists()) {
                leavingPath = currentPath
                destinationPath = parent
                canPerformGoUp = true
            }
        } else if (currentSafUri != null) {
            if (safStack.isNotEmpty()) {
                leavingUri = currentSafUri
                destinationUri = safStack.removeLast()
                canPerformGoUp = true
            } else {
                Toast.makeText(context, "Already at picked folder root", Toast.LENGTH_SHORT).show()
            }
        }

        if (canPerformGoUp) {
            navigateTo(
                newPath = destinationPath, 
                newUri = destinationUri,
                archiveFile = nextArchiveFile,
                archiveUri = nextArchiveUri,
                archivePath = nextArchivePath
            )
            focusItemInList(leavingPath, leavingUri)
        }
    }

    private fun focusItemInList(path: File?, uri: Uri?) {
        if (path == null && uri == null) return
        pendingFocusPath = path
        pendingFocusUri = uri
    }

    fun open(item: UniversalFile) {
        if (item.isDirectory) {
            if (item.isArchiveEntry) {
                navigateTo(
                    newPath = null,
                    newUri = null,
                    archiveFile = currentArchiveFile,
                    archiveUri = currentArchiveUri,
                    archivePath = item.archivePath
                )
            } else if (item.fileRef != null) {
                safStack.clear()
                navigateTo(item.fileRef, null)
            } else if (item.documentFileRef != null) {
                currentSafUri?.let { safStack.add(it) }
                navigateTo(null, item.documentFileRef.uri)
            }
        } else {
            if (ZipUtils.isArchive(item) && item.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) {
                navigateTo(
                    newPath = null,
                    newUri = null,
                    archiveFile = item.fileRef,
                    archivePath = ""
                )
            } else if (item.isArchiveEntry) {
                Toast.makeText(context, "Cannot open file inside archive yet", Toast.LENGTH_SHORT).show()
            } else {
                openFile(context, item)
            }
        }
    }

    fun openInNewTab(items: List<UniversalFile>) {
        items.filter { it.isDirectory || (ZipUtils.isArchive(it) && it.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) }.forEach { item ->
            onOpenInNewTab?.invoke(item)
        }
    }

    fun openInNewWindow(items: List<UniversalFile>) {
        if (items.isEmpty()) {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                if (currentPath != null) {
                    putExtra("path", currentPath?.absolutePath)
                } else if (currentSafUri != null) {
                    putExtra("uri", currentSafUri.toString())
                }
            }
            context.startActivity(intent)
        } else {
            items.filter { it.isDirectory || (ZipUtils.isArchive(it) && it.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) }.forEach { item ->
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    if (ZipUtils.isArchive(item) && item.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) {
                        putExtra("archivePath", item.fileRef.absolutePath)
                    } else if (item.fileRef != null) {
                        putExtra("path", item.fileRef.absolutePath)
                    } else if (item.documentFileRef != null) {
                        putExtra("uri", item.documentFileRef.uri.toString())
                    }
                }
                context.startActivity(intent)
            }
        }
    }

    fun copySelection() {
        copyToClipboard(context, selectionManager.selectedItems.toList())
    }

    fun cutSelection() {
        cutToClipboard(context, selectionManager.selectedItems.toList())
    }

    fun paste() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip

        FileOperationsManager.start()
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        scope.launch {
            val pastedNames = pasteFromClipboard(context, currentPath, currentSafUri, clipData)
            refresh()
            if (pastedNames.isNotEmpty()) {
                val pastedFiles = files.filter { pastedNames.contains(it.name) }
                if (pastedFiles.isNotEmpty()) {
                    selectionManager.clear()
                    pastedFiles.forEach { selectionManager.select(it) }
                }
            }
            if (PendingCut.isActive) GlobalEvents.triggerRefresh()
        }
    }

    fun deleteSelection(forcePermanent: Boolean = false) {
        FileOperationsManager.start()
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        scope.launch {
            deleteFiles(context, selectionManager.selectedItems.toList(), forcePermanent)
            refresh()
            selectionManager.clear()
            GlobalEvents.triggerRefresh()
        }
    }

    fun restoreSelection() {
        scope.launch {
            restoreFiles(context, selectionManager.selectedItems.toList())
            refresh()
            selectionManager.clear()
            GlobalEvents.triggerRefresh()
        }
    }
    
    fun undo() {
        scope.launch {
            UndoManager.undo()
            refresh()
            GlobalEvents.triggerRefresh()
        }
    }

    fun redo() {
        scope.launch {
            UndoManager.redo()
            refresh()
            GlobalEvents.triggerRefresh()
        }
    }
    
    fun extractSelection() {
        val selectedArchives = selectionManager.selectedItems.filter { ZipUtils.isArchive(it) && it.fileRef != null }
        if (selectedArchives.isEmpty()) return
        
        scope.launch {
            val intent = Intent(context, PopUpActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            
            val displayTitle = if (selectedArchives.size == 1) selectedArchives[0].name else "${selectedArchives.size} archives"
            val settings = FileOperationsManager.requestExtractOptions(displayTitle)
            if (settings.isCancelled) return@launch
            
            val parentFile = selectedArchives[0].fileRef?.parentFile ?: return@launch
            ZipUtils.extractArchives(context, selectedArchives, parentFile, settings.toSeparateFolder)
            if (settings.deleteSource) {
                deleteFiles(context, selectedArchives, forcePermanent = true, silent = true)
            }
            refresh()
            GlobalEvents.triggerRefresh()
        }
    }
    
    fun compressSelection() {
        val selected = selectionManager.selectedItems.toList()
        if (selected.isEmpty()) return
        val targetFolder = currentPath ?: return 
        val defaultName = if (selected.size == 1) "${selected[0].name}.zip" else "Archive.zip"

        scope.launch {
            val intent = Intent(context, PopUpActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            
            val settings = FileOperationsManager.requestArchiveOptions(defaultName)
            if (settings.isCancelled) return@launch
            
            ZipUtils.compressFiles(context, selected, targetFolder, settings)
            if (settings.deleteSource) {
                deleteFiles(context, selected, forcePermanent = true, silent = true)
            }
            refresh()
            GlobalEvents.triggerRefresh()
        }
    }

    fun renameSelection() {
        val selected = selectionManager.selectedItems.toList()
        if (selected.size == 1) {
            val target = selected[0]
            FileOperationsManager.openRename(target) { newName ->
                confirmRename(target, newName)
            }
            val intent = Intent(context, PopUpActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else if (selected.isEmpty()) {
            Toast.makeText(context, "Select a file to rename", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Select only one file to rename", Toast.LENGTH_SHORT).show()
        }
    }

    fun confirmRename(target: UniversalFile, newName: String) {
        FileOperationsManager.start()
        FileOperationsManager.update(0, 1, "Renaming ${target.name}...")
        FileOperationsManager.currentFileName.value = target.name
        
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        scope.launch {
            kotlinx.coroutines.delay(500)
            val success = renameFile(target, newName)
            withContext(Dispatchers.Main) {
                if (success) {
                    refresh()
                    selectionManager.clear()
                     val newFile = files.find { it.name == newName }
                     if (newFile != null) selectionManager.select(newFile)
                     GlobalEvents.triggerRefresh()
                } else {
                     Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                }
                FileOperationsManager.finish()
            }
        }
    }

    fun createNewFolder() {
        FileOperationsManager.openCreateFolder { name ->
            scope.launch {
                val success = createDirectory(context, currentPath, currentSafUri, name)
                withContext(Dispatchers.Main) {
                    if (success) {
                        refresh()
                        val newFile = files.find { it.name == name }
                        if (newFile != null) selectionManager.select(newFile)
                        GlobalEvents.triggerRefresh()
                    } else {
                        Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun createNewFile() {
        FileOperationsManager.openCreateFile { name ->
            scope.launch {
                val success = createFile(context, currentPath, currentSafUri, name)
                withContext(Dispatchers.Main) {
                    if (success) {
                        refresh()
                        val newFile = files.find { it.name == name }
                        if (newFile != null) selectionManager.select(newFile)
                        GlobalEvents.triggerRefresh()
                    } else {
                        Toast.makeText(context, "Failed to create file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun showProperties() {
        val selected = selectionManager.selectedItems.toList()
        if (selected.isNotEmpty()) {
            FileOperationsManager.showProperties(selected)
            val intent = Intent(context, PopUpActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun emptyRecycleBin() {
        scope.launch {
            val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
            val filesToDelete = trashDir.listFiles()?.map { it.toUniversal() } ?: emptyList()
            if (filesToDelete.isNotEmpty()) {
                FileOperationsManager.start()
                val intent = Intent(context, PopUpActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                
                deleteFiles(context, filesToDelete, forcePermanent = true)
                refresh()
                GlobalEvents.triggerRefresh()
            } else {
                Toast.makeText(context, "Recycle Bin is already empty", Toast.LENGTH_SHORT).show()
            }
        }
    }
    @Composable
    fun getScreenSize(): ScreenSize? {
        val configuration = LocalConfiguration.current

        val screenWidth = configuration.screenWidthDp

        return when {
            screenWidth > 1000 ->ScreenSize.LARGE
            screenWidth > 600 -> ScreenSize.MEDIUM
            screenWidth <= 600 -> ScreenSize.SMALL
            else -> null
        }
    }
}

@Composable
fun rememberFileExplorerState(
    context: Context = LocalContext.current,
    scope: CoroutineScope = rememberCoroutineScope()
): FileExplorerState {
    return remember(context, scope) { FileExplorerState(context, scope) }
}
