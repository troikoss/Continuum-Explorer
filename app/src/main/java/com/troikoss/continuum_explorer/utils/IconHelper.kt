package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import coil.compose.AsyncImage
import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Helper to determine the appropriate icon and handle thumbnails for files and folders.
 */
object IconHelper {

    // --- Public UI Components ---

    @Composable
    fun FileThumbnail(
        file: UniversalFile,
        modifier: Modifier = Modifier,
        tint: Color = MaterialTheme.colorScheme.primary,
        isDetailView: Boolean = false
    ) {
        val fallbackIcon = getIconForItem(file)

        if (!file.isDirectory && isMimeTypePreviewable(file)) {
            val name = file.name.lowercase()
            when {
                name.endsWith(".pdf") -> PdfThumbnail(file, fallbackIcon, modifier, tint)
                name.endsWith(".apk") -> ApkThumbnail(file, fallbackIcon, modifier, tint)
                name.endsWith(".txt") -> TextFilePreview(file, fallbackIcon, modifier, tint, isDetailView)
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
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = modifier,
                tint = tint
            )
        }
    }

    // --- Icon Logic ---

    /**
     * Returns the appropriate icon for a given UniversalFile.
     */
    fun getIconForItem(file: UniversalFile): ImageVector {
        if (file.isDirectory) return Icons.Default.Folder
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
            name.endsWith(".csv") || name.endsWith(".xls") || name.endsWith(".xlsx") ||
            name.endsWith(".ods") -> Icons.AutoMirrored.Filled.ListAlt
            name.endsWith(".txt") || name.endsWith(".doc") || name.endsWith(".docx") ||
            name.endsWith(".odt") -> Icons.Default.Description
            name.endsWith(".ppt") || name.endsWith(".pptx") || name.endsWith(".odp") -> Icons.Default.Slideshow

            name.endsWith(".apk") -> Icons.Default.Android

            // Default file icon
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }

    fun isMimeTypePreviewable(file: UniversalFile): Boolean {
        val name = file.name.lowercase()
        val extensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".mp4", ".mkv", ".avi", ".pdf", ".apk", ".txt")
        return extensions.any { name.endsWith(it) }
    }

    // --- Internal Thumbnail Helpers ---

    @Composable
    private fun PdfThumbnail(
        file: UniversalFile,
        fallbackIcon: ImageVector,
        modifier: Modifier = Modifier,
        tint: Color = MaterialTheme.colorScheme.primary
    ) {
        val context = LocalContext.current
        val thumbFile = remember(file) { DiskCache.getCacheFile(context, file) }
        var isReady by remember(file) { mutableStateOf(thumbFile.exists()) }

        LaunchedEffect(file) {
            if (!thumbFile.exists()) {
                withContext(Dispatchers.IO) { renderPdfThumbnail(context, file, thumbFile) }
                isReady = true
            }
        }

        ThumbnailImage(isReady, thumbFile, fallbackIcon, modifier, tint)
    }

    @Composable
    private fun ApkThumbnail(
        file: UniversalFile,
        fallbackIcon: ImageVector,
        modifier: Modifier,
        tint: Color
    ) {
        val context = LocalContext.current
        val thumbFile = remember(file) { DiskCache.getCacheFile(context, file) }
        var isReady by remember(file) { mutableStateOf(thumbFile.exists()) }

        LaunchedEffect(file) {
            if (!thumbFile.exists()) {
                withContext(Dispatchers.IO) { renderApkThumbnail(context, file, thumbFile) }
                isReady = true
            }
        }

        ThumbnailImage(isReady, thumbFile, fallbackIcon, modifier, tint)
    }

    @Composable
    private fun ThumbnailImage(isReady: Boolean, thumbFile: File, fallbackIcon: ImageVector, modifier: Modifier, tint: Color) {
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
            Icon(imageVector = fallbackIcon, contentDescription = null, modifier = modifier, tint = tint)
        }
    }

    @Composable
    private fun TextFilePreview(
        file: UniversalFile,
        fallbackIcon: ImageVector,
        modifier: Modifier,
        tint: Color,
        isDetailView: Boolean
    ) {
        val context = LocalContext.current
        var textSnippet by remember(file) { mutableStateOf("") }

        LaunchedEffect(file) {
            textSnippet = withContext(Dispatchers.IO) {
                try {
                    val stream = if (file.documentFileRef != null) {
                        context.contentResolver.openInputStream(file.documentFileRef!!.uri)
                    } else {
                        file.fileRef?.inputStream()
                    }
                    stream?.use { it.bufferedReader().use { reader ->
                        val buffer = CharArray(300)
                        val length = reader.read(buffer)
                        if (length > 0) String(buffer, 0, length) else ""
                    } } ?: ""
                } catch (e: Exception) { "" }
            }
        }

        if (isDetailView && textSnippet.isNotEmpty()) {
            Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall) {
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
            Icon(imageVector = fallbackIcon, contentDescription = null, modifier = modifier, tint = tint)
        }
    }

    // --- Rendering Logic ---

    private fun renderPdfThumbnail(context: Context, file: UniversalFile, thumbFile: File) {
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
                    saveBitmapToCache(bmp, thumbFile)
                }
                renderer.close()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun renderApkThumbnail(context: Context, file: UniversalFile, thumbFile: File) {
        try {
            val apkPath = file.fileRef?.absolutePath ?: return
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkPath, 0)
            info?.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = apkPath
                appInfo.publicSourceDir = apkPath
                saveBitmapToCache(drawableToBitmap(appInfo.loadIcon(pm)), thumbFile)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveBitmapToCache(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }

    // --- Utilities ---

    suspend fun getFileBitmap(context: Context, file: UniversalFile): Bitmap = withContext(Dispatchers.IO) {
        getThumbnailBitmap(context, file) ?: getIconForItem(file).toBitmap(sizePx = 96)
    }

    private suspend fun getThumbnailBitmap(context: Context, file: UniversalFile): Bitmap? = withContext(Dispatchers.IO) {
        if (file.isDirectory) return@withContext null
        val thumbFile = DiskCache.getCacheFile(context, file)
        if (!thumbFile.exists()) {
            val name = file.name.lowercase()
            if (name.endsWith(".pdf")) renderPdfThumbnail(context, file, thumbFile)
            else if (name.endsWith(".apk")) renderApkThumbnail(context, file, thumbFile)
        }
        if (thumbFile.exists()) BitmapFactory.decodeFile(thumbFile.absolutePath) else null
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val bitmap = createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1))
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun ImageVector.toBitmap(sizePx: Int, tintArgb: Int = android.graphics.Color.DKGRAY): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        canvas.scale(sizePx / viewportWidth, sizePx / viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintArgb
            style = Paint.Style.FILL
        }
        drawVectorGroup(canvas, root, paint)
        return bitmap
    }

    private fun drawVectorGroup(canvas: Canvas, group: VectorGroup, paint: Paint) {
        canvas.save()
        if (group.rotation != 0f) canvas.rotate(group.rotation, group.pivotX, group.pivotY)
        canvas.translate(group.translationX, group.translationY)
        canvas.scale(group.scaleX, group.scaleY, group.pivotX, group.pivotY)

        for (node in group) {
            when (node) {
                is VectorGroup -> drawVectorGroup(canvas, node, paint)
                is VectorPath -> canvas.drawPath(PathParser().addPathNodes(node.pathData).toPath().asAndroidPath(), paint)
            }
        }
        canvas.restore()
    }
}

object DiskCache {
    fun getThumbnailFolder(context: Context): File {
        val folder = File(context.cacheDir, "thumbnails")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun getCacheFile(context: Context, file: UniversalFile): File {
        val size = file.documentFileRef?.length() ?: file.fileRef?.length() ?: 0L
        val lastModified = file.documentFileRef?.lastModified() ?: file.fileRef?.lastModified() ?: 0L
        val uniqueId = "${file.name}_${size}_${lastModified}".hashCode().toString()
        return File(getThumbnailFolder(context), "$uniqueId.png")
    }
}
