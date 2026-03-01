package com.example.continuum_explorer.utils

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

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

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object SettingsManager {
    private const val PREFS_NAME = "explorer_settings"
    private const val KEY_DELETE_BEHAVIOR = "delete_behavior"
    private const val KEY_DEFAULT_ARCHIVE_VIEWER = "default_archive_viewer"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DETAILS_MODE = "details_mode"

    private val _deleteBehavior = mutableStateOf(DeleteBehavior.ASK)
    val deleteBehavior: State<DeleteBehavior> = _deleteBehavior

    private val _themeMode = mutableStateOf(ThemeMode.SYSTEM)
    val themeMode: State<ThemeMode> = _themeMode

    private val _detailsMode = mutableStateOf(DetailsMode.OFF)
    val detailsMode: State<DetailsMode> = _detailsMode

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

        val savedTheme = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val theme = try {
            ThemeMode.valueOf(savedTheme ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
        _themeMode.value = theme

        val savedDetails = prefs.getString(KEY_DETAILS_MODE, DetailsMode.OFF.name)
        _detailsMode.value = try {
            DetailsMode.valueOf(savedDetails ?: DetailsMode.OFF.name)
        } catch (e: Exception) {
            DetailsMode.OFF
        }

        _isDefaultArchiveViewerEnabled.value = prefs.getBoolean(KEY_DEFAULT_ARCHIVE_VIEWER, true)
    }

    fun setDetailsMode(context: Context, mode: DetailsMode) {
        _detailsMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DETAILS_MODE, mode.name).apply()

        // ADD THIS LINE: It tells the rest of the app "Hey! Settings changed!"
        GlobalEvents.triggerConfigUpdate()
    }

    fun setDeleteBehavior(context: Context, behavior: DeleteBehavior) {
        updateBehaviorInternal(behavior)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DELETE_BEHAVIOR, behavior.name).apply()
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
}
