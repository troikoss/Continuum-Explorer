package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.model.FileColumnType
import com.troikoss.continuum_explorer.model.SortOrder
import com.troikoss.continuum_explorer.model.UniversalFile
import java.io.File
import kotlin.collections.filter

/**
 * Extracts Video Resolution metadata.
 */
fun getVideoResolution(context: Context, file: UniversalFile): String? {
    val retriever = MediaMetadataRetriever()
    return try {
        if (file.fileRef != null) {
            retriever.setDataSource(file.fileRef.absolutePath)
        } else if (file.documentFileRef != null) {
            retriever.setDataSource(context, file.documentFileRef.uri)
        } else {
            return null
        }

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

        if (width != null && height != null) {
            if (rotation == 90 || rotation == 270) "${height}x${width}" else "${width}x${height}"
        } else {
            null
        }
    } catch (_: Exception) {
        null
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}

/**
 * Extracts Media Duration metadata.
 */
fun getMediaDuration(context: Context, file: UniversalFile): String? {
    val retriever = MediaMetadataRetriever()
    return try {
        if (file.fileRef != null) {
            retriever.setDataSource(file.fileRef.absolutePath)
        } else if (file.documentFileRef != null) {
            retriever.setDataSource(context, file.documentFileRef.uri)
        } else {
            return null
        }

        val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        if (durationString != null) {
            val millis = durationString.toLong()
            val totalSeconds = millis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60

            if (minutes >= 60) {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, remainingMinutes, seconds)
            } else {
                String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}

/**
 * Extracts Image Resolution metadata.
 */
fun getImageResolution(context: Context, file: UniversalFile): String {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    try {
        val inputStream = if (file.fileRef != null) {
            file.fileRef.inputStream()
        } else if (file.documentFileRef != null) {
            context.contentResolver.openInputStream(file.documentFileRef.uri)
        } else {
            null
        }
        inputStream?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        if (options.outWidth > 0 && options.outHeight > 0) {
            return "${options.outWidth}x${options.outHeight}"
        }
        return context.getString(R.string.unknown_resolution)
    } catch (_: Exception) {
        return context.getString(R.string.unknown_resolution)
    }
}

/**
 * Returns a sorted list of sibling file URIs with matching extensions,
 * using the same sort order as the folder they belong to.
 */
fun getSiblingFiles(context: Context, uriString: String, extensions: Set<String>): List<String> {
    if (uriString.isBlank()) return emptyList()
    val uri = Uri.parse(uriString)

    var realPath: String? = null

    if (uri.scheme == null || uri.scheme == "file") {
        realPath = uri.path ?: uriString
    } else if (uri.scheme == "content") {
        // Handle custom FileProvider scheme
        if (uri.authority == "${context.packageName}.provider") {
            val pathSegments = uri.pathSegments
            if (pathSegments.size >= 2) {
                val root = pathSegments[0]
                val rest = pathSegments.subList(1, pathSegments.size).joinToString("/")
                when (root) {
                    "external_files" -> realPath = Environment.getExternalStorageDirectory().absolutePath + "/" + rest
                    "root" -> realPath = "/$rest"
                    "my_files" -> realPath = context.filesDir.absolutePath + "/" + rest
                }
            }
        }
        // Handle SAF scheme
        if (realPath == null && DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            val path = if (split.size > 1) split[1] else ""
            realPath = if ("primary".equals(type, ignoreCase = true)) {
                Environment.getExternalStorageDirectory().absolutePath + "/" + path
            } else {
                "/storage/$type/$path"
            }
        }
        // Handle MediaStore
        if (realPath == null) {
            try {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                            realPath = cursor.getString(columnIndex)
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("FileScannerUtils", "Error querying MediaStore", e)
            }
        }
    }

    if (realPath != null) {
        val file = File(realPath)
        val parent = file.parentFile
        if (parent != null && parent.exists() && parent.isDirectory) {
            val prefs = context.getSharedPreferences("folder_sort_params", Context.MODE_PRIVATE)
            val saved = prefs.getString(parent.absolutePath, null)
            val sortColumn: FileColumnType
            val sortOrder: SortOrder
            if (saved != null) {
                val split = saved.split(":")
                sortColumn = try { FileColumnType.valueOf(split[0]) } catch (_: Exception) { FileColumnType.NAME }
                sortOrder = try { SortOrder.valueOf(split[1]) } catch (_: Exception) { SortOrder.Ascending }
            } else {
                sortColumn = FileColumnType.NAME
                sortOrder = SortOrder.Ascending
            }

            val files = parent.listFiles()
            if (files != null) {
                val filtered = files.filter { it.isFile && extensions.contains(it.extension.lowercase()) }
                val sorted = when (sortColumn) {
                    FileColumnType.NAME -> filtered.sortedBy { it.name.lowercase() }
                    FileColumnType.DATE -> filtered.sortedBy { it.lastModified() }
                    FileColumnType.DATE_DELETED -> {
                        val deletedAtMap = filtered.associate { it.name to (getDeletedAt(it.name) ?: 0L) }
                        filtered.sortedBy { deletedAtMap[it.name] }
                    }
                    FileColumnType.SIZE -> filtered.sortedBy { it.length() }
                    FileColumnType.DELETED_FROM -> filtered.sortedBy { it.name.lowercase() }
                }
                val ordered = if (sortOrder == SortOrder.Descending) sorted.reversed() else sorted
                return ordered.map { Uri.fromFile(it).toString() }
            }
        }
    }

    return listOf(uriString)
}
