package com.example.continuum_explorer.model

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Defines the sorting order for the file list.
 */
enum class SortOrder { Ascending, Descending }

/**
 * Available metadata columns and sorting modes.
 */
enum class FileColumnType { NAME, DATE, SIZE }

/**
 * Represents the current sorting configuration.
 */
data class SortParams(val columnType: FileColumnType, val order: SortOrder)

/**
 * Defines the different UI layouts for displaying files.
 */
enum class ViewMode { CONTENT, GRID, DETAILS }

enum class ScreenSize { SMALL, MEDIUM, LARGE}


data class NavLocation(
    val path: File?, 
    val uri: Uri?,
    val archiveFile: File? = null,
    val archiveUri: Uri? = null,
    val archivePath: String? = null,
    val safStack: List<Uri>? = null
)

/**
 * Definition for a metadata column in the file list.
 */
data class FileColumnDefinition(
    val type: FileColumnType,
    val label: String,
    val minWidth: Float = 150f,
    val initialWidth: Float = 250f
)

/**
 * A unified data class that represents a file or folder from either the 
 * standard Java File API or the Storage Access Framework (DocumentFile).
 */
data class UniversalFile(
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val length: Long,
    val fileRef: File? = null,           // Reference to local file system item
    val documentFileRef: DocumentFile? = null, // Reference to SAF/external item
    val absolutePath: String = "",
    val parentFile: File? = null,
    // Archive support
    val isArchiveEntry: Boolean = false,
    val archivePath: String? = null // Path inside the zip file
)
