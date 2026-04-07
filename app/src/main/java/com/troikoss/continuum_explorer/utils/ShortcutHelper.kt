package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.ui.activities.MainActivity
import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper to manage Android App Shortcuts (the quick actions that appear when long-pressing the app icon)
 * and pinned shortcuts on the home screen.
 */
object ShortcutHelper {
    /**
     * Updates the dynamic shortcuts to match the user's favorite folders.
     */
    fun updateFavoritesShortcuts(context: Context, favoritePaths: List<String>) {
        // Android limits the number of shortcuts (usually 4-5 dynamic ones)
        val shortcuts = favoritePaths.take(4).map { path ->
            val file = File(path)
            val name = file.name.ifEmpty { context.getString(R.string.folder) }
            
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("path", path)
                // Use flags to ensure a new window/instance is created
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            }

            ShortcutInfoCompat.Builder(context, "fav_$path")
                .setShortLabel(name)
                .setLongLabel("$name")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_folder))
                .setIntent(intent)
                .build()
        }

        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            // Silently fail if there's an issue with shortcut management
            e.printStackTrace()
        }
    }

    /**
     * Pins a shortcut for a file or folder to the device's home screen.
     */
    fun addToHome(context: Context, scope: CoroutineScope, item: UniversalFile) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return

        scope.launch {
            val name = item.name
            val path = item.fileRef?.absolutePath ?: item.documentFileRef?.uri.toString()

            val intent = if (item.isDirectory) {
                // Folders open in the file explorer
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    if (item.fileRef != null) putExtra("path", path)
                    else if (item.documentFileRef != null) putExtra("uri", path)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                }
            } else {
                // Files open with their default app
                val uri = getUriForUniversalFile(context, item)
                if (uri != null) {
                    val mimeType = android.webkit.MimeTypeMap
                        .getSingleton()
                        .getMimeTypeFromExtension(
                            android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())?.lowercase()
                        ) ?: context.contentResolver.getType(uri) ?: "*/*"

                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    // Fallback to explorer if URI couldn't be resolved
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra("path", path)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
            }

            val iconBitmap = withContext(Dispatchers.IO) {
                IconHelper.getFileBitmap(context, item)
            }
            val icon = iconBitmap?.let { IconCompat.createWithBitmap(it) }
                ?: IconCompat.createWithResource(context, R.drawable.ic_folder)

            val shortcutInfo = ShortcutInfoCompat.Builder(context, "pinned_$path")
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(icon)
                .setIntent(intent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        }
    }
}
