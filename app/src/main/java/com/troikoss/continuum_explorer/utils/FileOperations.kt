package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.managers.CollisionResult
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.UndoAction
import com.troikoss.continuum_explorer.managers.UndoManager
import com.troikoss.continuum_explorer.model.StorageProvider
import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * Calculates the total size of a file or directory recursively.
 */
suspend fun calculateSizeRecursively(context: Context, file: UniversalFile): Long {
    if (FileOperationsManager.isCancelled.value) return 0L

    if (file.isDirectory) {
        var size = 0L
        try {
            if (file.isArchiveEntry) {
                val parts = file.absolutePath.split("::")
                if (parts.size >= 2) {
                    val rootId = parts[0]
                    val archiveSource: Any = if (rootId.startsWith("/")) File(rootId) else Uri.parse(rootId)
                    ZipUtils.getArchiveChildren(context, archiveSource, file.archivePath ?: "").forEach { child ->
                        if (FileOperationsManager.isCancelled.value) return@forEach
                        size += calculateSizeRecursively(context, child)
                    }
                }
            } else {
                val fileRef = file.fileRef
                val docRef = file.documentFileRef
                if (fileRef != null) {
                    fileRef.listFiles()?.forEach {
                        if (FileOperationsManager.isCancelled.value) return 0L
                        size += calculateSizeRecursively(context, it.toUniversal())
                    }
                } else if (docRef != null) {
                    docRef.listFiles().forEach {
                        if (FileOperationsManager.isCancelled.value) return 0L
                        size += calculateSizeRecursively(context, it.toUniversal())
                    }
                } else {
                    try {
                        file.provider.listChildren(file.providerId).forEach { child ->
                            if (FileOperationsManager.isCancelled.value) return 0L
                            size += calculateSizeRecursively(context, child)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    } else {
        return file.length
    }
}

/**
 * Returns a name that doesn't collide with existing entries.
 * Appends " (1)", " (2)", … up to 999, then falls back to a timestamp suffix.
 */
fun getUniqueName(name: String, exists: (String) -> Boolean): String {
    if (!exists(name)) return name

    val lastDot = name.lastIndexOf('.')
    val base = if (lastDot != -1) name.substring(0, lastDot) else name
    val ext = if (lastDot != -1) name.substring(lastDot) else ""

    var count = 1
    var newName: String
    do {
        newName = "$base ($count)$ext"
        count++
        if (count > 999) {
            return "${base}_${System.currentTimeMillis()}$ext"
        }
    } while (exists(newName))

    return newName
}

/**
 * Recursively copies a file or directory to a local or SAF destination.
 * Returns the final name used at the destination, or null on failure/cancellation.
 */
suspend fun copyRecursively(
    context: Context,
    source: UniversalFile,
    destLocal: File?,
    destSaf: DocumentFile?,
    destProvider: StorageProvider? = null,
    destParentId: String? = null,
    onCopyFile: suspend (UniversalFile, OutputStream) -> Unit
): String? {
    if (FileOperationsManager.isCancelled.value) return null

    var targetName = source.name

    val alreadyExists = if (destLocal != null) {
        File(destLocal, targetName).exists()
    } else if (destSaf != null) {
        destSaf.findFile(targetName) != null
    } else if (destProvider != null && destParentId != null) {
        destProvider.findChild(destParentId, targetName) != null
    } else false

    if (alreadyExists) {
        val result = FileOperationsManager.resolveCollision(targetName, source.isDirectory)
        when (result) {
            CollisionResult.CANCEL -> {
                FileOperationsManager.cancelSoft()
                return null
            }
            CollisionResult.REPLACE -> {
                if (destLocal != null) {
                    val existing = File(destLocal, targetName)
                    if (existing.isDirectory) existing.deleteRecursively() else existing.delete()
                } else if (destSaf != null) {
                    destSaf.findFile(targetName)?.delete()
                } else if (destProvider != null && destParentId != null) {
                    destProvider.findChild(destParentId, targetName)?.let { destProvider.delete(it.providerId) }
                }
            }
            CollisionResult.KEEP_BOTH -> {
                targetName = getUniqueName(targetName) { name ->
                    if (destLocal != null) File(destLocal, name).exists()
                    else if (destSaf != null) destSaf.findFile(name) != null
                    else if (destProvider != null && destParentId != null) destProvider.findChild(destParentId, name) != null
                    else false
                }
            }
            CollisionResult.MERGE -> {
                // No-op: fall through to recurse into existing directory
            }
        }
    }

    if (source.isDirectory) {
        var newDestLocal: File? = null
        var newDestSaf: DocumentFile? = null
        var newDestParentId: String? = null

        if (destLocal != null) {
            newDestLocal = File(destLocal, targetName)
            if (!newDestLocal.exists()) {
                if (!newDestLocal.mkdir()) return null
            }
        } else if (destSaf != null) {
            newDestSaf = destSaf.findFile(targetName) ?: destSaf.createDirectory(targetName)
            if (newDestSaf == null) return null
        } else if (destProvider != null && destParentId != null) {
            val existing = destProvider.findChild(destParentId, targetName)
            newDestParentId = if (existing != null) {
                existing.providerId
            } else {
                try {
                    destProvider.createChild(destParentId, targetName, isDirectory = true).providerId
                } catch (_: Exception) { return null }
            }
        }

        if (source.isArchiveEntry) {
            val parts = source.absolutePath.split("::")
            if (parts.size >= 2) {
                val rootId = parts[0]
                val archiveSource: Any = if (rootId.startsWith("/")) File(rootId) else Uri.parse(rootId)
                ZipUtils.getArchiveChildren(context, archiveSource, source.archivePath ?: "").forEach { child ->
                    if (FileOperationsManager.isCancelled.value) return@forEach
                    copyRecursively(context, child, newDestLocal, newDestSaf, destProvider, newDestParentId, onCopyFile)
                }
            }
        } else {
            val fileRef = source.fileRef
            val docRef = source.documentFileRef
            if (fileRef != null) {
                fileRef.listFiles()?.forEach { child ->
                    if (FileOperationsManager.isCancelled.value) return@forEach
                    copyRecursively(context, child.toUniversal(), newDestLocal, newDestSaf, destProvider, newDestParentId, onCopyFile)
                }
            } else if (docRef != null) {
                docRef.listFiles().forEach { child ->
                    if (FileOperationsManager.isCancelled.value) return@forEach
                    copyRecursively(context, child.toUniversal(), newDestLocal, newDestSaf, destProvider, newDestParentId, onCopyFile)
                }
            } else {
                try {
                    source.provider.listChildren(source.providerId).forEach { child ->
                        if (FileOperationsManager.isCancelled.value) return@forEach
                        copyRecursively(context, child, newDestLocal, newDestSaf, destProvider, newDestParentId, onCopyFile)
                    }
                } catch (_: Exception) {}
            }
        }
    } else if (destProvider != null && destParentId != null && source.provider === destProvider) {
        // Same-provider copy: delegate to the provider so it can handle single-connection
        // constraints (e.g. FTP must buffer between RETR and STOR on one channel).
        try {
            destProvider.copyFrom(source, destParentId, targetName) {}
        } catch (_: Exception) {
            return null
        }
    } else {
        val outputStream: OutputStream? = if (destLocal != null) {
            java.io.FileOutputStream(File(destLocal, targetName))
        } else if (destSaf != null) {
            val mime = source.documentFileRef?.type
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    source.name.substringAfterLast('.', "").lowercase()
                )
                ?: "application/octet-stream"
            val newFile = destSaf.createFile(mime, targetName)
            if (newFile != null) context.contentResolver.openOutputStream(newFile.uri) else null
        } else if (destProvider != null && destParentId != null) {
            try {
                val (_, out) = destProvider.createAndOpenOutput(destParentId, targetName)
                out
            } catch (_: Exception) { null }
        } else null

        if (outputStream != null) {
            try {
                onCopyFile(source, outputStream)
            } catch (e: Exception) {
                throw e
            } finally {
                try { outputStream.close() } catch (_: Exception) {}
                if (FileOperationsManager.isCancelled.value) {
                    if (destLocal != null) {
                        File(destLocal, targetName).delete()
                    } else if (destSaf != null) {
                        destSaf.findFile(targetName)?.delete()
                    }
                }
            }
        }
    }
    return targetName
}

/**
 * Decodes an ExternalStorageProvider document URI to an absolute file path.
 * Returns null if the URI cannot be decoded (e.g. non-primary volume, different provider).
 */
fun safUriToFilePath(uri: Uri): String? {
    val docId = try {
        DocumentsContract.getDocumentId(uri)
    } catch (_: Exception) { return null }
    if (docId.startsWith("/")) return docId
    val colon = docId.indexOf(':')
    if (colon < 0) return null
    val volume = docId.substring(0, colon)
    val relative = docId.substring(colon + 1)
    val root = if (volume.equals("primary", ignoreCase = true)) {
        Environment.getExternalStorageDirectory().absolutePath
    } else {
        "/storage/$volume"
    }
    return "$root/$relative"
}

/**
 * Renames a file via SAF by copying to a new document then deleting the original.
 * Used as a last resort when the provider doesn't support DocumentsContract.renameDocument().
 */
fun safCopyRename(context: Context, src: DocumentFile, parent: DocumentFile, targetName: String): Boolean {
    val mimeType = src.type ?: "application/octet-stream"
    val newDoc = parent.createFile(mimeType, targetName) ?: run {
        android.util.Log.e("FileOperations", "safCopyRename: createFile failed for $targetName")
        return false
    }
    return try {
        context.contentResolver.openInputStream(src.uri)?.use { input ->
            context.contentResolver.openOutputStream(newDoc.uri)?.use { output ->
                input.copyTo(output)
            }
        }
        src.delete()
        true
    } catch (e: Exception) {
        newDoc.delete()
        android.util.Log.e("FileOperations", "safCopyRename failed: ${e.message}", e)
        false
    }
}

/**
 * Renames a file, handling collision resolution and undo recording.
 */
suspend fun renameFile(file: UniversalFile, newName: String, context: Context? = null): Boolean {
    if (FileOperationsManager.isCancelled.value) return false
    return withContext(Dispatchers.IO) {
        try {
            val fileRef = file.fileRef
            val documentFileRef = file.documentFileRef
            if (fileRef != null) {
                val oldName = fileRef.name
                if (oldName == newName) return@withContext true

                var targetName = newName
                if (File(fileRef.parentFile, newName).exists()) {
                    val result = FileOperationsManager.resolveCollision(newName, file.isDirectory)
                    when (result) {
                        CollisionResult.CANCEL -> return@withContext false
                        CollisionResult.REPLACE -> {
                            val existing = File(fileRef.parentFile, newName)
                            if (existing.isDirectory) existing.deleteRecursively() else existing.delete()
                        }
                        CollisionResult.KEEP_BOTH -> {
                            targetName = getUniqueName(newName) { File(fileRef.parentFile, it).exists() }
                        }
                        CollisionResult.MERGE -> {
                            // No-op: fall through to recurse into existing directory
                        }
                    }
                }

                val newFile = File(fileRef.parentFile, targetName)
                if (fileRef.renameTo(newFile)) {
                    UndoManager.record(UndoAction.Rename(newFile.parentFile, oldName, targetName))
                    true
                } else false
            } else if (documentFileRef != null) {
                val oldName = documentFileRef.name ?: file.name
                if (oldName == newName) return@withContext true

                // documentFileRef is reconstructed from the child URI, so parentFile may be null.
                // Fall back to provider.findChild via the stored parentId.
                val parent = documentFileRef.parentFile
                val safParentId = parent?.uri?.toString() ?: file.parentId

                var targetName = newName
                val hasCollision = if (parent != null) {
                    parent.findFile(newName) != null
                } else {
                    safParentId?.let { file.provider.findChild(it, newName) } != null
                }

                if (hasCollision) {
                    val result = FileOperationsManager.resolveCollision(newName, file.isDirectory)
                    when (result) {
                        CollisionResult.CANCEL -> return@withContext false
                        CollisionResult.REPLACE -> {
                            if (parent != null) {
                                parent.findFile(newName)?.delete()
                            } else {
                                safParentId?.let { file.provider.findChild(it, newName) }
                                    ?.let { file.provider.delete(it.providerId) }
                            }
                        }
                        CollisionResult.KEEP_BOTH -> {
                            targetName = if (parent != null) {
                                getUniqueName(newName) { parent.findFile(it) != null }
                            } else {
                                safParentId?.let { pid ->
                                    getUniqueName(newName) { n -> file.provider.findChild(pid, n) != null }
                                } ?: newName
                            }
                        }
                        CollisionResult.MERGE -> {
                            // No-op
                        }
                    }
                }

                val success = try {
                    if (context != null) {
                        DocumentsContract.renameDocument(context.contentResolver, documentFileRef.uri, targetName) != null
                    } else {
                        documentFileRef.renameTo(targetName)
                    }
                } catch (e: UnsupportedOperationException) {
                    val filePath = safUriToFilePath(documentFileRef.uri)
                    val fileRenamed = if (filePath != null) {
                        val f = File(filePath)
                        val dest = File(f.parent, targetName)
                        f.renameTo(dest)
                    } else false
                    if (!fileRenamed && context != null && parent != null) {
                        safCopyRename(context, documentFileRef, parent, targetName)
                    } else fileRenamed
                } catch (e: Exception) {
                    android.util.Log.e("FileOperations", "SAF rename error: ${e.javaClass.simpleName}: ${e.message}", e)
                    false
                }
                if (success) {
                    UndoManager.record(UndoAction.RenameSaf(
                        parent?.uri?.toString() ?: "",
                        oldName,
                        targetName
                    ))
                }
                success
            } else {
                val oldName = file.name
                if (oldName == newName) return@withContext true
                val pId = file.parentId ?: file.provider.parentId(file.providerId)
                val collision = pId?.let { file.provider.findChild(it, newName) }
                val targetName = if (collision != null) {
                    when (FileOperationsManager.resolveCollision(newName, file.isDirectory)) {
                        CollisionResult.CANCEL -> return@withContext false
                        CollisionResult.REPLACE -> { file.provider.delete(collision.providerId); newName }
                        CollisionResult.KEEP_BOTH -> getUniqueName(newName) { n ->
                            @Suppress("UNNECESSARY_SAFE_CALL")
                            pId?.let { file.provider.findChild(it, n) } != null
                        }
                        CollisionResult.MERGE -> newName
                    }
                } else newName
                file.provider.rename(file.providerId, targetName) != null
            }
        } catch (e: Exception) { e.printStackTrace(); false }
    }
}

/**
 * Creates a new directory, auto-renaming on collision.
 */
suspend fun createDirectory(
    context: Context,
    parentPath: File?,
    parentSafUri: Uri?,
    parentProvider: StorageProvider? = null,
    parentId: String? = null,
    name: String,
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            var targetName = name
            if (parentPath != null) {
                if (File(parentPath, targetName).exists()) {
                    targetName = getUniqueName(targetName) { File(parentPath, it).exists() }
                }
                File(parentPath, targetName).mkdir()
            } else if (parentSafUri != null) {
                val parentDoc = DocumentFile.fromTreeUri(context, parentSafUri) ?: return@withContext false
                if (parentDoc.findFile(targetName) != null) {
                    targetName = getUniqueName(targetName) { parentDoc.findFile(it) != null }
                }
                parentDoc.createDirectory(targetName) != null
            } else if (parentProvider != null && parentId != null) {
                if (parentProvider.findChild(parentId, targetName) != null) {
                    targetName = getUniqueName(targetName) { parentProvider.findChild(parentId, it) != null }
                }
                parentProvider.createChild(parentId, targetName, isDirectory = true)
                true
            } else false
        } catch (e: Exception) { e.printStackTrace(); false }
    }
}

/**
 * Creates a new empty file, auto-renaming on collision.
 */
suspend fun createFile(
    context: Context,
    parentPath: File?,
    parentSafUri: Uri?,
    parentProvider: StorageProvider? = null,
    parentId: String? = null,
    name: String,
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            var targetName = name
            if (parentPath != null) {
                if (File(parentPath, targetName).exists()) {
                    targetName = getUniqueName(targetName) { File(parentPath, it).exists() }
                }
                File(parentPath, targetName).createNewFile()
            } else if (parentSafUri != null) {
                val parentDoc = DocumentFile.fromTreeUri(context, parentSafUri) ?: return@withContext false
                if (parentDoc.findFile(targetName) != null) {
                    targetName = getUniqueName(targetName) { parentDoc.findFile(it) != null }
                }
                parentDoc.createFile("text/plain", targetName) != null
            } else if (parentProvider != null && parentId != null) {
                if (parentProvider.findChild(parentId, targetName) != null) {
                    targetName = getUniqueName(targetName) { parentProvider.findChild(parentId, it) != null }
                }
                parentProvider.createChild(parentId, targetName, isDirectory = false)
                true
            } else false
        } catch (e: Exception) { e.printStackTrace(); false }
    }
}