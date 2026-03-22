package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import coil.compose.AsyncImage
import com.troikoss.continuum_explorer.model.UniversalFile
import java.io.File
import java.io.FileOutputStream

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
                name.endsWith(".avi") || name.endsWith(".pdf") ||
                name.endsWith(".apk") || name.endsWith(".txt")
    }


    @Composable
    fun FileThumbnail(
        file: UniversalFile,
        modifier: Modifier = Modifier,
        tint: Color = MaterialTheme.colorScheme.primary,
        isDetailView: Boolean = false
    ) {

        val fallbackIcon = getIconForItem (file)

        if (!file.isDirectory && isMimeTypePreviewable(file)) {
            val name = file.name.lowercase()
            when {
                name.endsWith(".pdf") -> {
                    PdfThumbnail(file, fallbackIcon, modifier, tint)
                }
                name.endsWith(".apk") -> {
                    ApkThumbnail(file, fallbackIcon, modifier, tint)
                }
                name.endsWith(".txt") -> {
                    TextFilePreview(file, fallbackIcon, modifier, tint, isDetailView)
                }
                else -> {
                    AsyncImage(
                        model = file.documentFileRef?.uri ?: file.fileRef?.absolutePath,
                        contentDescription = null,
                        modifier = modifier,
                        contentScale = ContentScale.Fit,
                        placeholder = rememberVectorPainter(fallbackIcon),
                        error = rememberVectorPainter(fallbackIcon)
                    )
                }
            }
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
    fun getIconForItem(file: UniversalFile): ImageVector {
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

    @Composable
    fun PdfThumbnail(
        file: UniversalFile,
        fallbackIcon: ImageVector,
        modifier: Modifier = Modifier,
        tint: Color = MaterialTheme.colorScheme.primary
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current

        // Determine where the thumbnail file is/should be on disk
        val thumbFile = remember(file) { DiskCache.getCacheFile(context, file) }

        // A state to track if we are ready to show the image
        var isReady by remember(file) { mutableStateOf(thumbFile.exists()) }

        // If the thumbnail doesn't exist yet, render it once and save it
        LaunchedEffect(file) {
            if (!thumbFile.exists()) {
                try {
                    val pfd = if (file.documentFileRef != null) {
                        context.contentResolver.openFileDescriptor(file.documentFileRef!!.uri, "r")
                    } else {
                        ParcelFileDescriptor.open(file.fileRef, ParcelFileDescriptor.MODE_READ_ONLY)
                    }

                    pfd?.use { descriptor ->
                        val renderer = PdfRenderer(descriptor)
                        renderer.openPage(0).use { page ->
                            val bmp = createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            // Save the Bitmap to the thumbFile location
                            FileOutputStream(thumbFile).use { out ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                        }
                        renderer.close()
                    }
                    isReady = true // Now Coil can load it
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (isReady) {
            // HAND THE DISK FILE TO COIL
            AsyncImage(
                model = thumbFile,
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Fit,
                // Coil's built-in placeholder while it reads from disk
                placeholder = rememberVectorPainter(fallbackIcon),
                error = rememberVectorPainter(fallbackIcon)
            )
        } else {
            // Show the icon while we are rendering the PDF for the first time
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = modifier,
                tint = tint
            )
        }
    }

    @Composable
    fun ApkThumbnail(
        file: UniversalFile,
        fallbackIcon: ImageVector,
        modifier: Modifier = Modifier,
        tint: Color = MaterialTheme.colorScheme.primary
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val thumbFile = remember(file) { DiskCache.getCacheFile(context, file) }
        var isReady by remember(file) { mutableStateOf(thumbFile.exists()) }

        LaunchedEffect(file) {
            if (!thumbFile.exists()) {
                try {
                    // PackageManager needs a physical file path.
                    // If using DocumentFile (URIs), this is more complex,
                    // but for local files (fileRef), it's straightforward:
                    val apkPath = file.fileRef?.absolutePath

                    if (apkPath != null) {
                        val pm = context.packageManager
                        val info = pm.getPackageArchiveInfo(apkPath, 0)

                        info?.applicationInfo?.let { appInfo ->
                            // These two lines are "tricks" required to load icons from uninstalled APKs
                            appInfo.sourceDir = apkPath
                            appInfo.publicSourceDir = apkPath

                            val drawable = appInfo.loadIcon(pm)
                            val bmp = drawableToBitmap(drawable)

                            FileOutputStream(thumbFile).use { out ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            isReady = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (isReady) {
            AsyncImage(
                model = thumbFile,
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Fit,
                placeholder = rememberVectorPainter(fallbackIcon),
                error = rememberVectorPainter(fallbackIcon)
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = modifier,
                tint = tint
            )
        }
    }

    // Helper to convert Android Drawable to Bitmap
    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable) return drawable.bitmap
        val bitmap = createBitmap(
            width = drawable.intrinsicWidth.coerceAtLeast(1),
            height = drawable.intrinsicHeight.coerceAtLeast(1),
            config = Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @Composable
    fun TextFilePreview(
        file: UniversalFile,
        fallbackIcon: ImageVector,
        modifier: Modifier = Modifier,
        tint: Color = MaterialTheme.colorScheme.primary,
        isDetailView: Boolean
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var textSnippet by remember(file) { mutableStateOf("...") }

        // Read the file in a background thread
        LaunchedEffect(file) {
            textSnippet = try {
                val inputStream = if (file.documentFileRef != null) {
                    // Handle modern Scoped Storage URIs
                    context.contentResolver.openInputStream(file.documentFileRef!!.uri)
                } else {
                    // Handle traditional File paths
                    file.fileRef?.inputStream()
                }

                inputStream?.use { stream ->
                    // Efficiently read only the first 300 characters
                    stream.bufferedReader().use { reader ->
                        val buffer = CharArray(300)
                        val length = reader.read(buffer)
                        if (length > 0) String(buffer, 0, length) else ""
                    }
                } ?: ""
            } catch (e: Exception) {
                "Error loading preview"
            }
        }

        if (isDetailView) {
            Surface(
                modifier = modifier,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = textSnippet,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(4.dp),
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = modifier,
                tint = tint
            )
        }
    }


}

object DiskCache {
    fun getThumbnailFolder(context: Context): File {
        val folder = File(context.cacheDir, "pdf_thumbnails")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    // This creates the unique name for the thumbnail file
    fun getCacheFile(context: Context, file: UniversalFile): File {
        val size = file.documentFileRef?.length() ?: file.fileRef?.length() ?: 0L
        val lastModified = file.documentFileRef?.lastModified() ?: file.fileRef?.lastModified() ?: 0L
        val uniqueId = "${file.name}_${size}_${lastModified}".hashCode().toString()

        return File(getThumbnailFolder(context), "$uniqueId.png")
    }
}

