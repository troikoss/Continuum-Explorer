package com.troikoss.continuum_explorer.managers

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import com.troikoss.continuum_explorer.model.FileColumnType
import com.troikoss.continuum_explorer.model.SortOrder
import com.troikoss.continuum_explorer.model.ViewMode
import com.troikoss.continuum_explorer.utils.GlobalEvents

enum class DetailsMode {
    OFF,
    PANE,
    BAR
}

enum class DeleteBehavior {
    ASK,
    RECYCLE,
    PERMANENT
}

enum class TouchDragBehavior {
    ASK,
    COPY,
    MOVE
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object SettingsManager {
    private const val PREFS_NAME = "explorer_settings"
    private const val KEY_DELETE_BEHAVIOR = "delete_behavior"

    private const val KEY_TOUCH_DRAG_BEHAVIOR = "touch_drag_behavior"
    private const val KEY_DEFAULT_ARCHIVE_VIEWER = "default_archive_viewer"
    private const val KEY_THEME_MODE = "theme_mode"

    private const val KEY_LANGUAGE = "language"
    private const val KEY_DETAILS_MODE = "details_mode"

    private const val KEY_COMMAND_BAR_VISIBLE = "command_bar_visible"
    private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
    private const val KEY_ICON_TOUCH_SELECTION = "icon_touch_selection"
    private const val KEY_DEFAULT_VIEW_MODE = "default_view_mode"
    private const val KEY_COLORFUL_BARS = "colorful_bars"
    private const val KEY_FOLDERS_FIRST = "folders_first"
    private const val KEY_GROUPING_ENABLED = "grouping_enabled"
    private const val KEY_DEFAULT_SORT_COLUMN = "default_sort_column"
    private const val KEY_DEFAULT_SORT_ORDER = "default_sort_order"
    private const val KEY_DEFAULT_HEADER_DATE = "default_header_date"
    private const val KEY_DEFAULT_HEADER_SIZE = "default_header_size"
    private const val KEY_DEFAULT_HEADER_TYPE = "default_header_type"
    private const val KEY_DEFAULT_GRID_ZOOM = "default_grid_zoom"

    private val _deleteBehavior = mutableStateOf(DeleteBehavior.ASK)
    val deleteBehavior: State<DeleteBehavior> = _deleteBehavior

    private val _touchDragBehavior = mutableStateOf(TouchDragBehavior.ASK)
    val touchDragBehavior: State<TouchDragBehavior> = _touchDragBehavior

    private val _themeMode = mutableStateOf(ThemeMode.SYSTEM)
    val themeMode: State<ThemeMode> = _themeMode

    private val _language = mutableStateOf("system")
    val language: State<String> = _language

    private val _detailsMode = mutableStateOf(DetailsMode.OFF)
    val detailsMode: State<DetailsMode> = _detailsMode

    private val _isCommandBarVisible = mutableStateOf(true)
    val isCommandBarVisible: State<Boolean> = _isCommandBarVisible

    private val _showHiddenFiles = mutableStateOf(false)
    val showHiddenFiles: State<Boolean> = _showHiddenFiles

    private val _iconTouchSelection = mutableStateOf(true)
    val iconTouchSelection: State<Boolean> = _iconTouchSelection

    private val _defaultViewMode = mutableStateOf(ViewMode.DETAILS)
    val defaultViewMode: State<ViewMode> = _defaultViewMode

    private val _isColorfulBarsEnabled = mutableStateOf(false)
    val isColorfulBarsEnabled: State<Boolean> = _isColorfulBarsEnabled

    // Derived state: enabled if behavior is not PERMANENT
    private val _isRecycleBinEnabled = mutableStateOf(true)
    val isRecycleBinEnabled: State<Boolean> = _isRecycleBinEnabled

    private val _isDefaultArchiveViewerEnabled = mutableStateOf(true)
    val isDefaultArchiveViewerEnabled: State<Boolean> = _isDefaultArchiveViewerEnabled

    private val _foldersFirst = mutableStateOf(true)
    val foldersFirst: State<Boolean> = _foldersFirst

    private val _isGroupingEnabled = mutableStateOf(false)
    val isGroupingEnabled: State<Boolean> = _isGroupingEnabled

    private val _defaultSortColumnType = mutableStateOf(FileColumnType.NAME)
    val defaultSortColumnType: State<FileColumnType> = _defaultSortColumnType

    private val _defaultSortOrder = mutableStateOf(SortOrder.Ascending)
    val defaultSortOrder: State<SortOrder> = _defaultSortOrder

    private val _defaultHeaderDate = mutableStateOf(true)
    val defaultHeaderDate: State<Boolean> = _defaultHeaderDate

    private val _defaultHeaderSize = mutableStateOf(true)
    val defaultHeaderSize: State<Boolean> = _defaultHeaderSize

    private val _defaultHeaderType = mutableStateOf(true)
    val defaultHeaderType: State<Boolean> = _defaultHeaderType

    private val _defaultGridZoom = mutableIntStateOf(100)
    val defaultGridZoom: State<Int> = _defaultGridZoom

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val savedBehavior = prefs.getString(KEY_DELETE_BEHAVIOR, DeleteBehavior.ASK.name)
        val behavior = try {
            DeleteBehavior.valueOf(savedBehavior ?: DeleteBehavior.ASK.name)
        } catch (e: Exception) {
            DeleteBehavior.ASK
        }
        
        updateBehaviorInternal(behavior)

        val savedTouchDrag = prefs.getString(KEY_TOUCH_DRAG_BEHAVIOR, TouchDragBehavior.ASK.name)
        _touchDragBehavior.value = try {
            TouchDragBehavior.valueOf(savedTouchDrag ?: TouchDragBehavior.ASK.name)
        } catch (e: Exception) {
            TouchDragBehavior.ASK
        }

        val savedTheme = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val theme = try {
            ThemeMode.valueOf(savedTheme ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
        _themeMode.value = theme

        val savedLanguage = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        _language.value = savedLanguage
        applyLocale(savedLanguage)

        val savedDetails = prefs.getString(KEY_DETAILS_MODE, DetailsMode.OFF.name)
        _detailsMode.value = try {
            DetailsMode.valueOf(savedDetails ?: DetailsMode.OFF.name)
        } catch (e: Exception) {
            DetailsMode.OFF
        }

        _isCommandBarVisible.value = prefs.getBoolean(KEY_COMMAND_BAR_VISIBLE, true)
        _showHiddenFiles.value = prefs.getBoolean(KEY_SHOW_HIDDEN_FILES, false)
        _iconTouchSelection.value = prefs.getBoolean(KEY_ICON_TOUCH_SELECTION, true)
        _isColorfulBarsEnabled.value = prefs.getBoolean(KEY_COLORFUL_BARS, false)

        _isDefaultArchiveViewerEnabled.value = prefs.getBoolean(KEY_DEFAULT_ARCHIVE_VIEWER, true)
        _foldersFirst.value = prefs.getBoolean(KEY_FOLDERS_FIRST, true)
        _isGroupingEnabled.value = prefs.getBoolean(KEY_GROUPING_ENABLED, false)

        val savedSortCol = prefs.getString(KEY_DEFAULT_SORT_COLUMN, FileColumnType.NAME.name)
        _defaultSortColumnType.value = try {
            FileColumnType.valueOf(savedSortCol ?: FileColumnType.NAME.name)
        } catch (e: Exception) {
            FileColumnType.NAME
        }
        val savedSortOrder = prefs.getString(KEY_DEFAULT_SORT_ORDER, SortOrder.Ascending.name)
        _defaultSortOrder.value = try {
            SortOrder.valueOf(savedSortOrder ?: SortOrder.Ascending.name)
        } catch (e: Exception) {
            SortOrder.Ascending
        }

        val savedViewMode = prefs.getString(KEY_DEFAULT_VIEW_MODE, ViewMode.DETAILS.name)
        _defaultViewMode.value = try {
            ViewMode.valueOf(savedViewMode ?: ViewMode.DETAILS.name)
        } catch (e: Exception) {
            ViewMode.DETAILS
        }

        _defaultHeaderDate.value = prefs.getBoolean(KEY_DEFAULT_HEADER_DATE, true)
        _defaultHeaderSize.value = prefs.getBoolean(KEY_DEFAULT_HEADER_SIZE, true)
        _defaultHeaderType.value = prefs.getBoolean(KEY_DEFAULT_HEADER_TYPE, true)
        _defaultGridZoom.value = prefs.getInt(KEY_DEFAULT_GRID_ZOOM, 100).coerceIn(60, 300)
    }

    fun setLanguage(context: Context, languageTag: String) {
        _language.value = languageTag
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageTag).apply()
        applyLocale(languageTag)
        GlobalEvents.triggerConfigUpdate()
    }

    private fun applyLocale(languageTag: String) {
        val localeList = if (languageTag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)

    }

    fun setDetailsMode(context: Context, mode: DetailsMode) {
        _detailsMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DETAILS_MODE, mode.name).apply()

        // ADD THIS LINE: It tells the rest of the app "Hey! Settings changed!"
        GlobalEvents.triggerConfigUpdate()
    }

    fun setCommandBarVisible(context: Context, visible: Boolean) {
        _isCommandBarVisible.value = visible
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_COMMAND_BAR_VISIBLE, visible).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setShowHiddenFiles(context: Context, show: Boolean) {
        _showHiddenFiles.value = show
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN_FILES, show).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setIconTouchSelection(context: Context, enabled: Boolean) {
        _iconTouchSelection.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ICON_TOUCH_SELECTION, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setColorfulBarsEnabled(context: Context, enabled: Boolean) {
        _isColorfulBarsEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_COLORFUL_BARS, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDeleteBehavior(context: Context, behavior: DeleteBehavior) {
        updateBehaviorInternal(behavior)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DELETE_BEHAVIOR, behavior.name).apply()
    }

    fun setTouchDragBehavior(context: Context, behavior: TouchDragBehavior) {
        _touchDragBehavior.value = behavior
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOUCH_DRAG_BEHAVIOR, behavior.name).apply()
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private fun updateBehaviorInternal(behavior: DeleteBehavior) {
        _deleteBehavior.value = behavior
        // Automatically disable recycle bin view if user chooses to always delete permanently
        _isRecycleBinEnabled.value = (behavior != DeleteBehavior.PERMANENT)
    }

    fun setDefaultArchiveViewerEnabled(context: Context, enabled: Boolean) {
        _isDefaultArchiveViewerEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEFAULT_ARCHIVE_VIEWER, enabled).apply()
    }

    fun setDefaultViewMode(context: Context, mode: ViewMode) {
        _defaultViewMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_VIEW_MODE, mode.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setFoldersFirst(context: Context, enabled: Boolean) {
        _foldersFirst.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FOLDERS_FIRST, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setGroupingEnabled(context: Context, enabled: Boolean) {
        _isGroupingEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_GROUPING_ENABLED, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultSortColumnType(context: Context, columnType: FileColumnType) {
        _defaultSortColumnType.value = columnType
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_SORT_COLUMN, columnType.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultSortOrder(context: Context, order: SortOrder) {
        _defaultSortOrder.value = order
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_SORT_ORDER, order.name).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHeaderDate(context: Context, enabled: Boolean) {
        _defaultHeaderDate.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEFAULT_HEADER_DATE, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHeaderSize(context: Context, enabled: Boolean) {
        _defaultHeaderSize.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEFAULT_HEADER_SIZE, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultHeaderType(context: Context, enabled: Boolean) {
        _defaultHeaderType.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEFAULT_HEADER_TYPE, enabled).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDefaultGridZoom(context: Context, zoom: Int) {
        val clamped = zoom.coerceIn(60, 300)
        _defaultGridZoom.value = clamped
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DEFAULT_GRID_ZOOM, clamped).apply()
        GlobalEvents.triggerConfigUpdate()
    }

    fun resetDefaultFolderOptions(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_DEFAULT_VIEW_MODE, ViewMode.DETAILS.name)
            putString(KEY_DEFAULT_SORT_COLUMN, FileColumnType.NAME.name)
            putString(KEY_DEFAULT_SORT_ORDER, SortOrder.Ascending.name)
            putBoolean(KEY_SHOW_HIDDEN_FILES, false)
            putBoolean(KEY_FOLDERS_FIRST, true)
            putBoolean(KEY_GROUPING_ENABLED, false)
            putBoolean(KEY_DEFAULT_HEADER_DATE, true)
            putBoolean(KEY_DEFAULT_HEADER_SIZE, true)
            putBoolean(KEY_DEFAULT_HEADER_TYPE, true)
            putInt(KEY_DEFAULT_GRID_ZOOM, 100)
            apply()
        }
        _defaultViewMode.value = ViewMode.DETAILS
        _defaultSortColumnType.value = FileColumnType.NAME
        _defaultSortOrder.value = SortOrder.Ascending
        _showHiddenFiles.value = false
        _foldersFirst.value = true
        _isGroupingEnabled.value = false
        _defaultHeaderDate.value = true
        _defaultHeaderSize.value = true
        _defaultHeaderType.value = true
        _defaultGridZoom.value = 100
        GlobalEvents.triggerConfigUpdate()
    }

    fun resetAllFolderOverrides(context: Context) {
        val prefNames = listOf(
            "folder_view_modes", "folder_sort_params", "folder_grid_sizes",
            "folder_grouping", "folder_folders_first", "folder_show_hidden",
            "column_visibility", "column_widths"
        )
        prefNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        GlobalEvents.triggerConfigUpdate()
    }
}
