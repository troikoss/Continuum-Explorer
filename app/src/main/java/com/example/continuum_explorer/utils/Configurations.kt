package com.example.continuum_explorer.utils

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.model.*

/**
 * Manages configuration specific to folders (view mode, sort order, etc.)
 */
class FolderConfigurations(private val context: Context) {
    var sortParams by mutableStateOf(SortParams(FileColumnType.NAME, SortOrder.Descending))
        private set
    
    var viewMode by mutableStateOf(ViewMode.DETAILS)
        private set
        
    var gridItemSize by mutableIntStateOf(100)

    val extraColumns = listOf(
        FileColumnDefinition(FileColumnType.DATE, "Date Modified", initialWidth = 225f),
        FileColumnDefinition(FileColumnType.SIZE, "Size", initialWidth = 175f)
    )
    
    val columnWidths = mutableStateMapOf<FileColumnType, Float>().apply {
        extraColumns.forEach { put(it.type, it.initialWidth) }
    }

    fun updateViewMode(mode: ViewMode, key: String?) {
        if (viewMode != mode) {
            viewMode = mode
            if (key != null) {
                saveViewModeForCurrentPath(mode, key)
            }
        }
    }

    private fun saveViewModeForCurrentPath(mode: ViewMode, key: String) {
        val prefs = context.getSharedPreferences("folder_view_modes", Context.MODE_PRIVATE)
        prefs.edit().putString(key, mode.name).apply()
    }

    fun resolveViewMode(key: String?) {
        val prefs = context.getSharedPreferences("folder_view_modes", Context.MODE_PRIVATE)
        if (key != null) {
            val saved = prefs.getString(key, null)
            if (saved != null) {
                try {
                    updateViewMode(ViewMode.valueOf(saved), null)
                    return
                } catch (e: Exception) {}
            }
        }
        updateViewMode(ViewMode.DETAILS, null)
    }

    fun toggleSort(columnType: FileColumnType, key: String?, onSortChanged: () -> Unit) {
        val isSameColumn = sortParams.columnType == columnType
        val newOrder = if (isSameColumn) {
            if (sortParams.order == SortOrder.Ascending) SortOrder.Descending else SortOrder.Ascending
        } else {
            SortOrder.Ascending
        }
        updateSortParams(SortParams(columnType, newOrder), key)
        onSortChanged()
    }
    
    fun updateSortParams(params: SortParams, key: String?) {
        if (sortParams != params) {
            sortParams = params
            if (key != null) {
                saveSortParamsForCurrentPath(params, key)
            }
        }
    }

    private fun saveSortParamsForCurrentPath(params: SortParams, key: String) {
        val prefs = context.getSharedPreferences("folder_sort_params", Context.MODE_PRIVATE)
        val value = "${params.columnType.name}:${params.order.name}"
        prefs.edit().putString(key, value).apply()
    }

    fun resolveSortParams(key: String?) {
        val prefs = context.getSharedPreferences("folder_sort_params", Context.MODE_PRIVATE)
        fun parseParams(value: String): SortParams? {
            return try {
                val split = value.split(":")
                SortParams(FileColumnType.valueOf(split[0]), SortOrder.valueOf(split[1]))
            } catch (e: Exception) { null }
        }

        if (key != null) {
            val saved = prefs.getString(key, null)
            if (saved != null) {
                val params = parseParams(saved)
                if (params != null) {
                    updateSortParams(params, null)
                    return
                }
            }
        }
        updateSortParams(SortParams(FileColumnType.NAME, SortOrder.Ascending), null)
    }

    @Composable
    fun SortArrow(type: FileColumnType) {
        if (sortParams.columnType == type) {
            Icon(
                imageVector = if (sortParams.order == SortOrder.Ascending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    fun updateGridSize(newSize: Int, key: String?) {
        gridItemSize = newSize
        if (key != null) {
            saveGridSizeForCurrentPath(newSize, key)
        }
    }

    private fun saveGridSizeForCurrentPath(size: Int, key: String) {
        val prefs = context.getSharedPreferences("folder_grid_sizes", Context.MODE_PRIVATE)
        prefs.edit().putInt(key, size).apply()
    }

    fun resolveGridSize(key: String?) {
        val prefs = context.getSharedPreferences("folder_grid_sizes", Context.MODE_PRIVATE)
        if (key != null) {
            // Load the saved size, using 100 as a default if nothing is found for that key
            val savedSize = prefs.getInt(key, 100)
            gridItemSize = savedSize
            return
        }
        // If there's no specific folder, just use the default
        gridItemSize = 100
    }


}






/**
 * Manages global application configurations (favorites, SAF roots, library layout)
 */
class AppConfigurations(private val context: Context) {
    val addedSafUris = mutableStateListOf<Uri>()
    val favoritePaths = mutableStateListOf<String>()
    val libraryOrder = mutableStateListOf<String>("recent", "trash")
    var isRecentVisible by mutableStateOf(true)

    var navPaneWidthPx by mutableFloatStateOf(400f)
    var detailsPaneWidthPx by mutableFloatStateOf(400f)

    init {
        reload()
    }

    fun reload() {
        loadAddedSafUris()
        loadFavorites()
        loadLibrarySettings()
        loadPaneWidths()
    }

    fun savePaneWidths() {
        val prefs = context.getSharedPreferences("pane_widths", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("nav_width", navPaneWidthPx)
            putFloat("details_width", detailsPaneWidthPx)
        }.apply()
    }

    private fun loadPaneWidths() {
        val prefs = context.getSharedPreferences("pane_widths", Context.MODE_PRIVATE)
        navPaneWidthPx = prefs.getFloat("nav_width", 400f)
        detailsPaneWidthPx = prefs.getFloat("details_width", 400f)
    }

    private fun loadAddedSafUris() {
        val prefs = context.getSharedPreferences("saf_storage", Context.MODE_PRIVATE)
        val uris = prefs.getStringSet("uris", emptySet()) ?: emptySet()
        addedSafUris.clear()
        uris.forEach { uriString ->
            addedSafUris.add(Uri.parse(uriString))
        }
    }

    fun saveAddedSafUris() {
        val prefs = context.getSharedPreferences("saf_storage", Context.MODE_PRIVATE)
        val uriStrings = addedSafUris.map { it.toString() }.toSet()
        prefs.edit().putStringSet("uris", uriStrings).apply()
    }

    private fun loadFavorites() {
        val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val favoritesList = prefs.getString("ordered_paths", "") ?: ""
        favoritePaths.clear()
        if (favoritesList.isNotEmpty()) {
            favoritePaths.addAll(favoritesList.split("|"))
        } else {
            val favoritesSet = prefs.getStringSet("paths", emptySet()) ?: emptySet()
            favoritePaths.addAll(favoritesSet.sorted())
            saveFavorites()
        }
    }
    
    fun saveFavorites() {
        val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val orderedString = favoritePaths.joinToString("|")
        prefs.edit().putString("ordered_paths", orderedString).apply()
        prefs.edit().putStringSet("paths", favoritePaths.toSet()).apply()
    }

    private fun loadLibrarySettings() {
        val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
        val order = prefs.getString("order", "recent|trash") ?: "recent|trash"
        libraryOrder.clear()
        libraryOrder.addAll(order.split("|"))
        isRecentVisible = prefs.getBoolean("is_recent_visible", true)
    }
    
    fun saveLibrarySettings() {
        val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("order", libraryOrder.joinToString("|"))
            putBoolean("is_recent_visible", isRecentVisible)
        }.apply()
    }

    fun toggleRecentVisibility() {
        isRecentVisible = !isRecentVisible
        saveLibrarySettings()
        GlobalEvents.triggerConfigUpdate()
    }
    
    fun addFavorite(path: String) {
        if (!favoritePaths.contains(path)) {
            favoritePaths.add(path)
            saveFavorites()
            GlobalEvents.triggerConfigUpdate()
        }
    }
    
    fun removeFavorite(path: String) {
        if (favoritePaths.remove(path)) {
            saveFavorites()
            GlobalEvents.triggerConfigUpdate()
        }
    }
    
    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val item = favoritePaths.removeAt(fromIndex)
        favoritePaths.add(toIndex, item)
        saveFavorites()
        GlobalEvents.triggerConfigUpdate()
    }
    
    fun moveLibraryItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val item = libraryOrder.removeAt(fromIndex)
        libraryOrder.add(toIndex, item)
        saveLibrarySettings()
        GlobalEvents.triggerConfigUpdate()
    }
    
    fun isFavorite(path: String): Boolean {
        return favoritePaths.contains(path)
    }
}