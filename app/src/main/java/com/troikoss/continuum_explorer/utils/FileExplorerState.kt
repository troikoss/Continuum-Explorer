package com.troikoss.continuum_explorer.utils

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
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.GalleryManager
import com.troikoss.continuum_explorer.managers.RecentFilesManager
import com.troikoss.continuum_explorer.managers.SearchManager
import com.troikoss.continuum_explorer.managers.SelectionManager
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.*
import com.troikoss.continuum_explorer.model.StorageProvider
import com.troikoss.continuum_explorer.providers.LocalProvider
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
    val scope: CoroutineScope
) {
    // Configuration managers
    val folderConfigs = FolderConfigurations(context)
    val appConfigs = AppConfigurations(context)

    // Reference to the main storage root to prevent navigating into system files
    var storageRoot by mutableStateOf<File>(Environment.getExternalStorageDirectory())

    // Current directory or SAF URI being viewed
    var currentPath by mutableStateOf<File?>(Environment.getExternalStorageDirectory())
    var currentSafUri by mutableStateOf<Uri?>(null)

    // Flag for special virtual locations
    var libraryItem by mutableStateOf(LibraryItem.None)

    // Archive Navigation State
    var currentArchiveFile by mutableStateOf<File?>(null)
    var currentArchiveUri by mutableStateOf<Uri?>(null)
    var currentArchivePath by mutableStateOf("")

    // Cache for current archive structure: Path -> List of Files
    var archiveCache: Map<String, List<UniversalFile>>? = null

    // Network provider state
    var currentNetworkProvider by mutableStateOf<StorageProvider?>(null)
    var currentNetworkId by mutableStateOf<String?>(null)
    var networkError by mutableStateOf<String?>(null)

    // The processed and sorted list of files to display
    var files by mutableStateOf(emptyList<UniversalFile>())

    // Recycle bin metadata keyed by file name, populated only when in the recycle bin
    var recycleBinMetadata by mutableStateOf(emptyMap<String, RecycleBinMetadata>())

    // Loading state
    var isLoading by mutableStateOf(false)
    private var loadingJob: Job? = null

    // A stable key that only updates once the files for a directory have actually loaded.
    var loadedPathKey by mutableStateOf("initial")
        private set

    // Tracks if shift key is currently pressed (used for drag and drop logic)
    var isShiftPressed by mutableStateOf(false)

    // Tracks the last pointer type for drag logic
    var isMouseInteraction by mutableStateOf(false)

    // Y position (in ComposeView coordinates) of the active system drag, null when idle.
    // Every fileDropTarget writes here; FileContent reads it for edge auto-scroll.
    val activeDragY = mutableStateOf<Float?>(null)

    // Flag indicating if a system drag is currently active.
    val isSystemDragActive = mutableStateOf(false)

    // Centralized selection manager
    val selectionManager = SelectionManager()

    // Date formatter for consistent date display
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())

    // Stack to track navigation hierarchy for SAF (Storage Access Framework)
    val safStack = mutableStateListOf<Uri>()

    var scrollToItemIndex by mutableStateOf<Int?>(null)
        internal set

    val backStack = mutableStateListOf<NavLocation>()
    val forwardStack = mutableStateListOf<NavLocation>()

    // Callback to open a new tab
    var onOpenInNewTab: ((UniversalFile) -> Unit)? = null

    var isSearchMode by mutableStateOf(false)
    var isSearchUIActive by mutableStateOf(false)

    val activeViewMode: ViewMode
        @Composable
        get() {
            return folderConfigs.viewMode
        }

    init {
        scope.launch(Dispatchers.IO) {
            val key = getCurrentStorageKey()
            withContext(Dispatchers.Main) {
                folderConfigs.resolveViewMode(key)
                folderConfigs.resolveSortParams(key)
                folderConfigs.resolveGridSize(key)
                folderConfigs.resolveColumnVisibility(key, libraryItem == LibraryItem.RecycleBin)
                folderConfigs.resolveColumnWidths(key)
            }
        }
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
                // If a configuration changed (like hidden files toggle), refresh the list
                refresh()
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
            return if (rootId == "primary") context.getString(R.string.nav_internal_storage) else rootId
        }

        val pm = context.packageManager
        val providerInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveContentProvider(uri.authority!!, PackageManager.ComponentInfoFlags.of(0))
            } else {
                pm.resolveContentProvider(uri.authority!!, 0)
            }
        } catch (_: Exception) {
            null
        }

        val appName = providerInfo?.loadLabel(pm)?.toString() ?: ""
        if (appName.isNotEmpty()) return appName

        val doc = DocumentFile.fromTreeUri(context, currentSafUri!!)
        return doc?.name ?: context.getString(R.string.nav_external_location)
    }

    val currentName: String
        get() {
            if (currentNetworkProvider != null && currentNetworkId != null) {
                return currentNetworkProvider!!.displayName(currentNetworkId!!)
            }
            return if (libraryItem == LibraryItem.RecycleBin) {
                context.getString(R.string.nav_recycle_bin)
            } else if (libraryItem == LibraryItem.Recent) {
                context.getString(R.string.nav_recent)
            } else if (libraryItem == LibraryItem.Gallery) {
                if (currentPath != null) currentPath!!.name
                else context.getString(R.string.nav_gallery)
            } else if (currentArchiveFile != null) {
                currentArchiveFile?.name ?: context.getString(R.string.archive)
            } else if (currentArchiveUri != null) {
                context.getString(R.string.archive)
            } else if (currentPath != null) {
                if (currentPath?.absolutePath == storageRoot.absolutePath) {
                    if (storageRoot.absolutePath == Environment.getExternalStorageDirectory().absolutePath) context.getString(R.string.nav_internal_storage)
                    else context.getString(R.string.nav_sd_card)
                }
                else currentPath?.name ?: context.getString(R.string.unknown)
            } else if (currentSafUri != null) {
                if (safStack.isNotEmpty()) {
                    val doc = DocumentFile.fromTreeUri(context, currentSafUri!!)
                    doc?.name ?: context.getString(R.string.nav_unknown_folder)
                } else {
                    getSafDisplayName(currentSafUri!!)
                }
            } else {
                context.getString(R.string.new_tab)
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

            libraryItem == LibraryItem.Gallery -> UniversalFile(
                name = if (currentPath != null) currentPath!!.name else context.getString(R.string.nav_gallery),
                isDirectory = true,
                lastModified = 0L,
                length = 0L,
                provider = LocalProvider,
                providerId = currentPath?.absolutePath ?: "virtual://gallery",
            )

            libraryItem == LibraryItem.Recent -> UniversalFile(
                name = context.getString(R.string.nav_recent),
                isDirectory = true,
                lastModified = 0L,
                length = 0L,
                provider = LocalProvider,
                providerId = "virtual://recent"
            )

            else -> null
        }

    val canGoUp: Boolean
        get() = if (currentNetworkProvider != null && currentNetworkId != null) {
            currentNetworkProvider!!.parentId(currentNetworkId!!) != null
        } else if (currentArchiveFile != null || currentArchiveUri != null) {
            true
        } else if (libraryItem != LibraryItem.None) {
            libraryItem == LibraryItem.Gallery && currentPath != null
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
            val sortParams = folderConfigs.sortParams
            val showHidden = SettingsManager.showHiddenFiles.value

            withContext(Dispatchers.Main) {
                isSearchMode = true
                isLoading = true
                files = emptyList() // Clear previous results immediately
                val key = getCurrentStorageKey()
                folderConfigs.resolveViewMode(key)
                folderConfigs.resolveSortParams(key)
                folderConfigs.resolveGridSize(key)
                loadedPathKey = key ?: context.getString(R.string.msg_search_results)
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
                    currentNetworkProvider = currentNetworkProvider,
                    currentNetworkId = currentNetworkId,
                    searchSubfolders = searchSubfolders,
                    archiveCache = archiveCache,
                    currentArchivePath = currentArchivePath
                )

                val filtered = results.filter { if (showHidden) true else !it.name.startsWith(".") }

                val sorted = withContext(Dispatchers.IO) {
                    sortFiles(filtered, sortParams)
                }

                withContext(Dispatchers.Main) {
                    files = sorted
                    selectionManager.allFiles = files
                    isLoading = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, context.getString(R.string.msg_search_failed), Toast.LENGTH_SHORT).show()
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
            } catch (_: Exception) {}
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

    internal var pendingFocusPath: File? = null
    internal var pendingFocusUri: Uri? = null

    private suspend fun loadFiles(forceRefresh: Boolean = false) {
        isLoading = true
        if (currentArchiveFile == null && currentArchiveUri == null) {
            archiveCache = null
        }
        if (forceRefresh) {
            archiveCache = null
        }

        // Resolve sort params early so files are sorted correctly during IO work.
        // ViewMode/gridSize/columnVisibility are resolved together with the file list
        // assignment so they never change before the new list is visible.
        val key = getCurrentStorageKey()
        folderConfigs.resolveSortParams(key)

        val sortParams = folderConfigs.sortParams
        val showHidden = SettingsManager.showHiddenFiles.value

        try {
            val (sortedList, newMeta) = withContext(Dispatchers.IO) {
                val universalList = when (libraryItem) {
                    LibraryItem.Recent -> RecentFilesManager.getRecentFiles(context)
                    LibraryItem.Gallery -> when {
                        currentPath != null -> GalleryManager.getAlbumContents(context, currentPath!!.absolutePath)
                        appConfigs.isGalleryAlbumsEnabled -> GalleryManager.getGalleryAlbums(context)
                        else -> GalleryManager.getGalleryFiles(context)
                    }
                    LibraryItem.RecycleBin -> if (currentPath != null) {
                        val rawFiles = currentPath!!.listFiles()?.toList() ?: emptyList()
                        rawFiles.filter { it.name != ".metadata" }.map { it.toUniversal() }
                    } else emptyList()
                    LibraryItem.None -> if (currentArchiveFile != null || currentArchiveUri != null) {
                        if (archiveCache == null) {
                            val source: Any = currentArchiveFile ?: currentArchiveUri!!
                            archiveCache = ZipUtils.parseArchive(context, source)
                        }
                        archiveCache?.get(currentArchivePath) ?: emptyList()
                    } else if (currentNetworkProvider != null && currentNetworkId != null) {
                        try {
                            val result = currentNetworkProvider!!.listChildren(currentNetworkId!!)
                            withContext(Dispatchers.Main) { networkError = null }
                            result
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { networkError = e.message ?: "Network error" }
                            emptyList()
                        }
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

                val meta: Map<String, RecycleBinMetadata> = if (libraryItem == LibraryItem.RecycleBin) {
                    universalList.associate { file ->
                        file.name to RecycleBinMetadata(
                            deletedAt = getDeletedAt(file.name),
                            deletedFrom = getDeletedFrom(file.name)
                        )
                    }
                } else emptyMap()

                val filteredList = if (showHidden) universalList else universalList.filter { !it.name.startsWith(".") }

                val skipSort = libraryItem == LibraryItem.Recent || (libraryItem == LibraryItem.Gallery && !appConfigs.isGalleryAlbumsEnabled)
                Pair(if (skipSort) filteredList else sortFiles(filteredList, sortParams, meta), meta)
            }

            withContext(Dispatchers.Main) {
                folderConfigs.resolveViewMode(key)
                folderConfigs.resolveGridSize(key)
                folderConfigs.resolveColumnVisibility(key, libraryItem == LibraryItem.RecycleBin)
                folderConfigs.resolveColumnWidths(key)
                recycleBinMetadata = newMeta
                files = sortedList

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

                loadedPathKey = key ?: when (libraryItem) {
                    LibraryItem.Recent -> "recent"
                    LibraryItem.Gallery -> "gallery"
                    LibraryItem.None -> "root"
                    LibraryItem.RecycleBin -> "trash"
                }
                selectionManager.allFiles = files
                isLoading = false
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    private fun sortFiles(
        rawList: List<UniversalFile>,
        params: SortParams,
        meta: Map<String, RecycleBinMetadata> = emptyMap()
    ): List<UniversalFile> {
        return rawList.sortedWith { f1, f2 ->
            if (f1.isDirectory && !f2.isDirectory) return@sortedWith -1
            if (!f1.isDirectory && f2.isDirectory) return@sortedWith 1

            val result = when (params.columnType) {
                FileColumnType.NAME -> f1.name.lowercase().compareTo(f2.name.lowercase())
                FileColumnType.DATE -> f1.lastModified.compareTo(f2.lastModified)
                FileColumnType.SIZE -> f1.length.compareTo(f2.length)
                FileColumnType.TYPE -> {
                    val type1 = getFileType(f1, context)
                    val type2 = getFileType(f2, context)
                    type1.compareTo(type2)
                }
                FileColumnType.DATE_DELETED -> (meta[f1.name]?.deletedAt ?: 0L).compareTo(meta[f2.name]?.deletedAt ?: 0L)
                FileColumnType.DELETED_FROM -> (meta[f1.name]?.deletedFrom ?: "").compareTo(meta[f2.name]?.deletedFrom ?: "")
            }
            if (params.order == SortOrder.Ascending) result else -result
        }
    }

    fun formatDate(timestamp: Long): String = dateFormatter.format(timestamp)
    fun formatSize(size: Long): String = Formatter.formatFileSize(context, size)

    fun getCurrentStorageKey(): String? {
        if (isSearchMode) return context.getString(R.string.msg_search_results)
        if (currentNetworkProvider != null && currentNetworkId != null) return currentNetworkId
        return if (currentArchiveFile != null || currentArchiveUri != null) {
            val base = currentArchiveFile?.absolutePath ?: currentArchiveUri.toString()
            "archive:$base:${currentArchivePath.removeSuffix("/")}"
        } else if (libraryItem == LibraryItem.Gallery) {
            if (currentPath != null) "virtual://gallery_album:${currentPath!!.absolutePath}"
            else "virtual://gallery"
        } else if (libraryItem == LibraryItem.Recent) {
            "virtual://recent"
        } else if (libraryItem == LibraryItem.RecycleBin) {
            "virtual://recycle_bin"
        } else if (currentPath != null) {
            currentPath?.absolutePath
        } else if (currentSafUri != null) {
            currentSafUri.toString()
        } else {
            null
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
