package com.example.continuum_explorer.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.continuum_explorer.model.UniversalFile
import java.io.File
import kotlin.text.lowercase

/**
 * Helper to determine the appropriate icon and handle thumbnails for files and folders.
 */
object IconHelper {

    /**
     * Checks if a file type is "thumbnailable" (Images and Videos)
     */
    fun isMimeTypePreviewable(file: UniversalFile): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".webp") || name.endsWith(".bmp") ||
                name.endsWith(".mp4") || name.endsWith(".mkv") ||
                name.endsWith(".avi")
    }


    @Composable
    fun FileThumbnail(
        file: UniversalFile,
        modifier: Modifier = Modifier,
        tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    ) {

        val fallbackIcon = getIconForItem (file)

        if (!file.isDirectory && isMimeTypePreviewable(file)) {
            // This shows the actual image/video preview
            coil.compose.AsyncImage(
                model = file.documentFileRef?.uri ?: file.fileRef?.absolutePath,
                contentDescription = null,
                modifier = modifier,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                placeholder = rememberVectorPainter(fallbackIcon),
                error = rememberVectorPainter(fallbackIcon)
            )
        } else {
            // Show the regular folder/file icon
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = modifier,
                tint = tint
            )
        }
    }


    /**
     * Returns the appropriate icon for a given UniversalFile.
     */
    @Composable
    fun getIconForItem(file: UniversalFile): ImageVector {
        if (file.isDirectory) {
            return Icons.Default.Folder
        }
        return getIconByFileName(file.name)
    }

    /**
     * Returns the appropriate icon for a standard Java File.
     */
    fun getIconForFile(file: File): ImageVector {
        if (file.isDirectory) {
            return Icons.Default.Folder
        }
        return getIconByFileName(file.name)
    }

    /**
     * Internal helper to determine icon based on file extension.
     */
    private fun getIconByFileName(fileName: String): ImageVector {
        val name = fileName.lowercase()
        return when {
            // Archives
            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || 
            name.endsWith(".tar") || name.endsWith(".gz") -> Icons.Default.FolderZip

            // Images
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || 
            name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> Icons.Default.Image

            // Videos
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || 
            name.endsWith(".mov") || name.endsWith(".webm") -> Icons.Default.VideoFile

            // Audio
            name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg") || 
            name.endsWith(".m4a") || name.endsWith(".flac") -> Icons.Default.AudioFile

            // Documents
            name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
            name.endsWith(".csv") || name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".ods") -> Icons.AutoMirrored.Filled.ListAlt
            name.endsWith(".txt") || name.endsWith(".doc") || name.endsWith(".docx") || 
            name.endsWith(".odt") -> Icons.Default.Description
            name.endsWith(".ppt") || name.endsWith(".pptx") || name.endsWith(".odp") -> Icons.Default.Slideshow

            name.endsWith(".apk") -> Icons.Default.Android

            // Default file icon
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }
    
    // Placeholder for thumbnail logic which can be added here later
}
