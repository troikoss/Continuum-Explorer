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
        "zip", "rar", "7z", "tar", "gz" -> "$extensionString ${context.getString(R.string.archive)}"
        "jpg", "jpeg", "bmp", "png", "gif", "webp" -> "$extensionString ${context.getString(R.string.image)}"
        "mp4", "mkv", "avi", "mov", "webm" -> "$extensionString ${context.getString(R.string.video)}"
        "mp3", "wav", "ogg", "m4a", "flac" -> "$extensionString ${context.getString(R.string.audio)}"
        "txt", "doc", "docx", "odt", "pdf" -> "$extensionString ${context.getString(R.string.document)}"
        "" -> context.getString(R.string.file)
        else -> "$extensionString ${context.getString(R.string.file)}"
    }
}
