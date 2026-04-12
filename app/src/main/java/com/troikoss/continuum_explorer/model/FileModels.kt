package com.troikoss.continuum_explorer.model

import android.net.Uri
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Represents a virtual/special view mode for the file list.
 */
enum class LibraryItem { None, Recent, Gallery, RecycleBin }

/**
 * Represents a navigation destination in the side pane.
 */
sealed class NavSection {
    object InternalStorage : NavSection()
    object RecycleBin : NavSection()
    object Recent : NavSection()
    object Gallery : NavSection()
    data class RemovableVolume(val volumeIndex: Int) : NavSection()
}

/**
 * Defines the sorting order for the file list.
 */
enum class SortOrder { Ascending, Descending }

/**
 * Available metadata columns and sorting modes.
 */
enum class FileColumnType { NAME, DATE, SIZE, DATE_DELETED, DELETED_FROM }

/**
 * Represents the current sorting configuration.
 */
data class SortParams(val columnType: FileColumnType, val order: SortOrder)

/**
 * Defines the different UI layouts for displaying files.
 */
enum class ViewMode { CONTENT, GRID, GALLERY, DETAILS }

enum class ScreenSize { SMALL, MEDIUM, LARGE}


data class NavLocation(
    val path: File?,
    val uri: Uri?,
    val archiveFile: File? = null,
    val archiveUri: Uri? = null,
    val archivePath: String? = null,
    val safStack: List<Uri>? = null,
    val libraryItem: LibraryItem = LibraryItem.None
)

/**
 * Definition for a metadata column in the file list.
 */
data class FileColumnDefinition(
    val type: FileColumnType,
    val label: String,
    val minWidth: Dp = 75.dp,
    val initialWidth: Dp = 125.dp
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

/**
 * Recycle bin metadata for a file, stored separately from UniversalFile.
 */
data class RecycleBinMetadata(
    val deletedAt: Long?,
    val deletedFrom: String?
)
