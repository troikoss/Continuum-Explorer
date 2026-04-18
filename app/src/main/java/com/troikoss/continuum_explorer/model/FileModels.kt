package com.troikoss.continuum_explorer.model

import android.net.Uri
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import com.troikoss.continuum_explorer.providers.SafProvider
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
    data class NetworkStorage(val connectionId: String) : NavSection()
}

/**
 * Defines the sorting order for the file list.
 */
enum class SortOrder { Ascending, Descending }

/**
 * Available metadata columns and sorting modes.
 */
enum class FileColumnType { NAME, DATE, SIZE, TYPE, DATE_DELETED, DELETED_FROM }

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
 * A unified data class that represents a file or folder from any storage backend.
 * `provider` + `providerId` are the authoritative identity. The legacy `fileRef`,
 * `documentFileRef`, `isArchiveEntry`, and `archivePath` fields are derived
 * automatically so that all existing call sites continue to compile unchanged.
 */
data class UniversalFile(
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val length: Long,
    val provider: StorageProvider,
    val providerId: String,
    val parentId: String? = null,
    val mimeType: String? = null,
) {
    // Legacy convenience accessors — delegate to the provider so existing code compiles
    val fileRef: File?
        get() = if (provider is LocalProvider) File(providerId) else null

    val documentFileRef: DocumentFile?
        get() = if (provider is SafProvider) {
            try { DocumentFile.fromTreeUri(SafProvider.appContext(), Uri.parse(providerId))
                    ?: DocumentFile.fromSingleUri(SafProvider.appContext(), Uri.parse(providerId)) }
            catch (_: Exception) { null }
        } else null

    val isArchiveEntry: Boolean
        get() = provider is com.troikoss.continuum_explorer.providers.ArchiveProvider

    val archivePath: String?
        get() = if (provider is com.troikoss.continuum_explorer.providers.ArchiveProvider)
            (provider as com.troikoss.continuum_explorer.providers.ArchiveProvider).entryPath(providerId)
        else null

    val absolutePath: String
        get() = providerId

    val parentFile: File?
        get() = if (provider is LocalProvider) File(providerId).parentFile else null
}

/**
 * Recycle bin metadata for a file, stored separately from UniversalFile.
 */
data class RecycleBinMetadata(
    val deletedAt: Long?,
    val deletedFrom: String?
)

enum class NetworkProtocol(val defaultPort: Int) {
    FTP(21), WEBDAV(443), SMB(445), GOOGLE_DRIVE(0)
}

data class NetworkConnection(
    val id: String,
    val protocol: NetworkProtocol,
    val displayName: String,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    val remotePath: String = "/"
)
