package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.model.ProviderKind
import com.troikoss.continuum_explorer.model.StorageProvider
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import com.troikoss.continuum_explorer.providers.SafProvider
import java.io.File

fun File.toUniversal(): UniversalFile {
    return UniversalFile(
        name = this.name,
        isDirectory = this.isDirectory,
        lastModified = this.lastModified(),
        length = this.length(),
        provider = LocalProvider,
        providerId = this.absolutePath,
        parentId = this.parentFile?.absolutePath,
    )
}

fun DocumentFile.toUniversal(): UniversalFile {
    return UniversalFile(
        name = this.name ?: "Unknown",
        isDirectory = this.isDirectory,
        lastModified = this.lastModified(),
        length = this.length(),
        provider = SafProvider,
        providerId = this.uri.toString(),
        parentId = this.parentFile?.uri?.toString(),
        mimeType = this.type,
    )
}

/**
 * Provider-aware extension shortcuts.
 */
val UniversalFile.kind: ProviderKind get() = provider.kind

fun UniversalFile.openInput() = provider.openInput(providerId)
fun UniversalFile.openReadFd() = provider.openReadFd(providerId)

suspend fun UniversalFile.children() = provider.listChildren(providerId)

fun UniversalFile.delete() = provider.delete(providerId)

fun UniversalFile.rename(newName: String) = provider.rename(providerId, newName)

/**
 * Generates a shareable URI for a file.
 * Uses FileProvider for local files and direct URIs for SAF documents.
 */
fun getUriForUniversalFile(context: Context, file: UniversalFile): Uri? {
    return try {
        when {
            file.isArchiveEntry -> Uri.parse("archive://${file.absolutePath}")
            file.provider is LocalProvider -> FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                File(file.providerId)
            )
            else -> Uri.parse(file.providerId)
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
        "zip", "rar", "7z", "tar", "gz" -> context.getString(R.string.file_type_format_archive, extensionString)
        "jpg", "jpeg", "bmp", "png", "gif", "webp" -> context.getString(R.string.file_type_format_image, extensionString)
        "mp4", "mkv", "avi", "mov", "webm" -> context.getString(R.string.file_type_format_video, extensionString)
        "mp3", "wav", "ogg", "m4a", "flac" -> context.getString(R.string.file_type_format_audio, extensionString)
        "txt", "doc", "docx", "odt", "pdf" -> context.getString(R.string.file_type_format_document, extensionString)
        "" -> context.getString(R.string.file)
        else -> context.getString(R.string.file_type_format_file, extensionString)
    }
}
