package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.model.UniversalFile
import java.io.File

fun File.toUniversal(): UniversalFile {
    return UniversalFile(
        name = this.name,
        isDirectory = this.isDirectory,
        lastModified = this.lastModified(),
        length = this.length(),
        fileRef = this,
        absolutePath = this.absolutePath,
        parentFile = this.parentFile
    )
}

fun DocumentFile.toUniversal(): UniversalFile {
    return UniversalFile(
        name = this.name ?: "Unknown",
        isDirectory = this.isDirectory,
        lastModified = this.lastModified(),
        length = this.length(),
        documentFileRef = this,
        absolutePath = this.uri.toString(),
        parentFile = null
    )
}

/**
 * Generates a shareable URI for a file.
 * Uses FileProvider for local files and direct URIs for SAF documents.
 */
fun getUriForUniversalFile(context: Context, file: UniversalFile): Uri? {
    return try {
        if (file.isArchiveEntry) {
            // Virtual URI for archive entries to enable Drag & Drop
            Uri.parse("archive://${file.absolutePath}")
        } else if (file.fileRef != null) {
            FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                file.fileRef
            )
        } else {
            file.documentFileRef?.uri
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Gets a human-readable file type string.
 */
fun getFileType(file: UniversalFile, context: Context): String {
    if (file.isDirectory) return context.getString(R.string.folder)

    val extension = file.name.substringAfterLast(".", "").lowercase()
    val extensionString = extension.uppercase()

    return when (extension) {
        "zip", "rar", "7z", "tar", "gz" -> "$extensionString ${context.getString(R.string.archive)}"
        "jpg", "jpeg", "bmp", "png", "gif", "webp" -> "$extensionString ${context.getString(R.string.image)}"
        "mp4", "mkv", "avi", "mov", "webm" -> "$extensionString ${context.getString(R.string.video)}"
        "mp3", "wav", "ogg", "m4a", "flac" -> "$extensionString ${context.getString(R.string.audio)}"
        "txt", "doc", "docx", "odt", "pdf" -> "$extensionString ${context.getString(R.string.document)}"
        "" -> context.getString(R.string.file)
        else -> "$extensionString ${context.getString(R.string.file)}"
    }
}