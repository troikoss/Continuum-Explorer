package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.OperationType
import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shares one or more files via the system share sheet.
 */
fun shareFiles(context: Context, scope: CoroutineScope, files: List<UniversalFile>) {
    if (files.isEmpty()) return
    if (files.any { it.provider.capabilities.isRemote }) {
        scope.launch {
            try {
                FileOperationsManager.start()
                withContext(Dispatchers.Main) {
                    FileOperationsManager.update(0, files.size, operationType = OperationType.COPY)
                }
                val uris = ArrayList<Uri>()
                withContext(Dispatchers.IO) {
                    files.forEach { file ->
                        val uri = if (file.provider.capabilities.isRemote) {
                            val cached = RemoteCache.cache(context, file)
                            FileProvider.getUriForFile(context, context.packageName + ".provider", cached)
                        } else {
                            getUriForUniversalFile(context, file)
                        }
                        if (uri != null) uris.add(uri)
                    }
                }
                withContext(Dispatchers.Main) {
                    FileOperationsManager.finish()
                    if (uris.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.msg_share_failed_prepare), Toast.LENGTH_SHORT).show()
                    } else {
                        launchShareIntent(context, uris)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    FileOperationsManager.finish()
                    Toast.makeText(context, context.getString(R.string.msg_share_failed_prepare), Toast.LENGTH_SHORT).show()
                }
            }
        }
        return
    }
    val uris = ArrayList<Uri>()
    files.forEach { file ->
        val uri = getUriForUniversalFile(context, file)
        if (uri != null) uris.add(uri)
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.msg_share_failed_prepare), Toast.LENGTH_SHORT).show()
        return
    }
    launchShareIntent(context, uris)
}

private fun launchShareIntent(context: Context, uris: ArrayList<Uri>) {
    val shareIntent = Intent().apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size == 1) {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uris[0])
            type = context.contentResolver.getType(uris[0]) ?: "*/*"
        } else {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            type = "*/*"
        }
    }
    try {
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.menu_share)))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.msg_no_app_share), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens a file using the system "Open with" chooser.
 */
fun openWith(context: Context, scope: CoroutineScope, file: UniversalFile) {
    if (file.provider.capabilities.isRemote) {
        scope.launch {
            try {
                FileOperationsManager.start()
                withContext(Dispatchers.Main) {
                    FileOperationsManager.update(0, 1, operationType = OperationType.COPY)
                    FileOperationsManager.currentFileName.value = file.name
                }
                val cached = RemoteCache.cache(context, file)
                withContext(Dispatchers.Main) {
                    FileOperationsManager.finish()
                    val cachedUri = FileProvider.getUriForFile(context, context.packageName + ".provider", cached)
                    launchOpenWithIntent(context, cachedUri)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    FileOperationsManager.finish()
                    Toast.makeText(context, context.getString(R.string.msg_no_app_open), Toast.LENGTH_SHORT).show()
                }
            }
        }
        return
    }

    val uri = getUriForUniversalFile(context, file) ?: return
    launchOpenWithIntent(context, uri)
}

private fun launchOpenWithIntent(context: Context, uri: Uri) {
    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    val mimeType = if (extension != null) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    } else {
        context.contentResolver.getType(uri)
    } ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.menu_open_with_no_dots)))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.msg_no_app_open), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens a remote file by caching it locally first, then launching the appropriate viewer.
 */
fun openRemoteFile(context: Context, scope: CoroutineScope, file: UniversalFile) {
    val mime = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase())

    scope.launch {
        try {
            FileOperationsManager.start()
            withContext(Dispatchers.Main) {
                FileOperationsManager.update(0, 1, operationType = OperationType.COPY)
                FileOperationsManager.currentFileName.value = file.name
            }
            val cached = RemoteCache.cache(context, file) { copied, total ->
                if (total > 0) {
                    FileOperationsManager.updateDetailed(
                        processedBytes = copied, totalBytes = total,
                        speed = 0L, remainingMillis = 0L, fileName = file.name
                    )
                }
            }
            withContext(Dispatchers.Main) {
                FileOperationsManager.finish()
                val cachedUri = FileProvider.getUriForFile(
                    context, context.packageName + ".provider", cached
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(cachedUri, mime ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.msg_no_app_open), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                FileOperationsManager.finish()
                Toast.makeText(context, e.message ?: context.getString(R.string.msg_share_failed_prepare), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Opens a file with the default system app.
 * Falls back to the "Open with" chooser if no default handles the type.
 */
fun openFile(context: Context, file: UniversalFile) {
    if (file.provider.capabilities.isRemote) return // Must use openRemoteFile with a scope instead

    val uri = getUriForUniversalFile(context, file) ?: return

    if (file.name.endsWith(".apk", ignoreCase = true)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                Toast.makeText(context, context.getString(R.string.msg_apk_install_permission), Toast.LENGTH_LONG).show()
                return
            }
        }
    }

    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    val mimeType = if (extension != null) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    } else {
        context.contentResolver.getType(uri)
    } ?: "*/*"

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        launchOpenWithIntent(context, uri)
    }
}