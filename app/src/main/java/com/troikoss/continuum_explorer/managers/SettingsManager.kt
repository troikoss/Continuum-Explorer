package com.troikoss.continuum_explorer.managers

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
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

        val savedViewMode = prefs.getString(KEY_DEFAULT_VIEW_MODE, ViewMode.DETAILS.name)
        _defaultViewMode.value = try {
            ViewMode.valueOf(savedViewMode ?: ViewMode.DETAILS.name)
        } catch (e: Exception) {
            ViewMode.DETAILS
        }
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
    }
}
