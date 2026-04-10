package com.troikoss.continuum_explorer.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.CollisionResult
import com.troikoss.continuum_explorer.managers.DeleteBehavior
import com.troikoss.continuum_explorer.managers.DeleteResult
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.OperationType
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.managers.UndoAction
import com.troikoss.continuum_explorer.managers.UndoManager
import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.Properties

// region Trash Metadata

private fun getTrashMetadataFile(): File {
    val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
    if (!trashDir.exists()) trashDir.mkdirs()
    return File(trashDir, ".metadata")
}

fun saveTrashMetadata(recycledName: String, originalPath: String, parentUri: String? = null) {
    try {
        val metadataFile = getTrashMetadataFile()
        val props = Properties()
        if (metadataFile.exists()) { metadataFile.inputStream().use { props.load(it) } }
        val entryValue = if (parentUri != null) "$originalPath|$parentUri" else originalPath
        props.setProperty(recycledName, entryValue)
        props.setProperty("$recycledName.deletedAt", System.currentTimeMillis().toString())
        metadataFile.outputStream().use { props.store(it, null) }
    } catch (e: Exception) { e.printStackTrace() }
}

fun getDeletedAt(recycledName: String): Long? {
    return try {
        val metadataFile = getTrashMetadataFile()
        if (!metadataFile.exists()) return null
        val props = Properties()
        metadataFile.inputStream().use { props.load(it) }
        props.getProperty("$recycledName.deletedAt")?.toLongOrNull()
    } catch (_: Exception) { null }
}

fun getOriginalPath(recycledName: String): String? {
    return try {
        val metadataFile = getTrashMetadataFile()
        if (!metadataFile.exists()) return null
        val props = Properties()
        metadataFile.inputStream().use { props.load(it) }
        props.getProperty(recycledName)
    } catch (_: Exception) { null }
}

fun getDeletedFrom(recycledName: String): String? {
    val rawMetadata = getOriginalPath(recycledName) ?: return null
    val parts = rawMetadata.split("|", limit = 2)
    val originalPath = parts[0]
    return if (originalPath.startsWith("content://")) {
        parts.getOrNull(1) ?: originalPath
    } else {
        File(originalPath).parent
    }
}

fun removeTrashMetadata(recycledName: String) {
    try {
        val metadataFile = getTrashMetadataFile()
        if (!metadataFile.exists()) return
        val props = Properties()
        metadataFile.inputStream().use { props.load(it) }
        props.remove(recycledName)
        props.remove("$recycledName.deletedAt")
        metadataFile.outputStream().use { props.store(it, null) }
    } catch (e: Exception) { e.printStackTrace() }
}

// endregion

// region Delete

/**
 * Recursively deletes a file or directory, reporting progress via [onProgress].
 */
suspend fun deleteRecursivelyWithProgress(
    context: Context,
    file: UniversalFile,
    onProgress: suspend (UniversalFile, Long) -> Unit
): Boolean {
    if (FileOperationsManager.isCancelled.value) return false
    val length = file.length
    if (file.isDirectory) {
        val children = try {
            if (file.isArchiveEntry) {
                emptyList()
            } else if (file.fileRef != null) {
                file.fileRef.listFiles()?.map { it.toUniversal() }
            } else if (file.documentFileRef != null) {
                file.documentFileRef.listFiles().map { it.toUniversal() }
            } else emptyList()
        } catch (_: Exception) { emptyList() }
        children?.forEach { child ->
            if (FileOperationsManager.isCancelled.value) return@forEach
            deleteRecursivelyWithProgress(context, child, onProgress)
        }
    }

    if (FileOperationsManager.isCancelled.value) return false
    val success = try {
        if (file.isArchiveEntry) {
            false // Deleting archive entries not supported
        } else if (file.fileRef != null) file.fileRef.delete()
        else file.documentFileRef?.delete() == true
    } catch (_: Exception) { false }
    if (success) onProgress(file, length)
    return success
}

/**
 * Deletes a list of files permanently or moves them to the recycle bin depending on settings.
 */
suspend fun deleteFiles(
    context: Context,
    files: List<UniversalFile>,
    forcePermanent: Boolean = false,
    silent: Boolean = false
) {
    if (files.isEmpty()) return

    val behavior = SettingsManager.deleteBehavior.value

    val result = if (silent) {
        if (forcePermanent) DeleteResult.PERMANENT else DeleteResult.RECYCLE
    } else {
        when (behavior) {
            DeleteBehavior.ASK -> FileOperationsManager.confirmDelete(files.size, permanentOnly = forcePermanent)
            DeleteBehavior.RECYCLE -> if (forcePermanent) DeleteResult.PERMANENT else DeleteResult.RECYCLE
            DeleteBehavior.PERMANENT -> DeleteResult.PERMANENT
        }
    }

    if (result == DeleteResult.CANCEL) {
        FileOperationsManager.finish()
        return
    }
    if (result == DeleteResult.RECYCLE) {
        moveToRecycleBin(context, files)
        return
    }

    var totalBytesToDelete = 0L
    withContext(Dispatchers.IO) { totalBytesToDelete = files.sumOf { calculateSizeRecursively(context, it) } }
    withContext(Dispatchers.Main) {
        FileOperationsManager.itemsTotal.intValue = files.size
        FileOperationsManager.currentProcessedItems.intValue = files.size
        FileOperationsManager.totalSize.longValue = totalBytesToDelete
        FileOperationsManager.currentOperationType.value = OperationType.DELETE
    }

    var globalBytesDeleted = 0L
    var lastUpdateTime = System.currentTimeMillis()
    val speedWindow = ArrayDeque<Pair<Long, Long>>()
    speedWindow.add(lastUpdateTime to 0L)
    val windowMs = 3000L

    withContext(Dispatchers.IO) {
        var deletedCount = 0
        for ((index, file) in files.withIndex()) {
            if (FileOperationsManager.isCancelled.value) break
            withContext(Dispatchers.Main) {
                FileOperationsManager.update(index, files.size, operationType = OperationType.DELETE)
                FileOperationsManager.currentFileName.value = file.name
                FileOperationsManager.itemsProcessed.intValue = index
            }
            val success = deleteRecursivelyWithProgress(context, file) { currentFile, bytes ->
                globalBytesDeleted += bytes
                val now = System.currentTimeMillis()
                if (now - lastUpdateTime >= 300) {
                    speedWindow.add(now to globalBytesDeleted)
                    while (speedWindow.size > 2 && now - speedWindow.first.first > windowMs) { speedWindow.removeFirst() }
                    val startPoint = speedWindow.first
                    val timeDiff = now - startPoint.first
                    val bytesDiff = globalBytesDeleted - startPoint.second
                    val currentSpeed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0L
                    val remainingBytes = totalBytesToDelete - globalBytesDeleted
                    val timeRemaining = if (currentSpeed > 0) (remainingBytes * 1000) / currentSpeed else 0L
                    withContext(Dispatchers.Main) {
                        FileOperationsManager.updateDetailed(globalBytesDeleted, totalBytesToDelete, currentSpeed, timeRemaining, currentFile.name)
                    }
                    lastUpdateTime = now
                }
            }
            if (success) {
                deletedCount++
                removeTrashMetadata(file.name)
            }
        }
        withContext(Dispatchers.Main) {
            FileOperationsManager.finish()
            val msg = if (FileOperationsManager.isCancelled.value) {
                context.getString(R.string.msg_cancelled_deletion)
            } else {
                context.getString(R.string.msg_deletion, deletedCount)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

// endregion

// region Recycle Bin

/**
 * Moves files to a hidden .Trash folder in the root of the storage.
 */
suspend fun moveToRecycleBin(context: Context, files: List<UniversalFile>) {
    val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
    if (!trashDir.exists()) trashDir.mkdirs()

    var totalBytesToMove = 0L
    withContext(Dispatchers.IO) { totalBytesToMove = files.sumOf { calculateSizeRecursively(context, it) } }
    withContext(Dispatchers.Main) {
        FileOperationsManager.itemsTotal.intValue = files.size
        FileOperationsManager.currentProcessedItems.intValue = files.size
        FileOperationsManager.totalSize.longValue = totalBytesToMove
        FileOperationsManager.currentOperationType.value = OperationType.TRASH
    }

    var movedCount = 0
    val recycleLog = mutableListOf<Pair<String, String>>() // RecycledName to OriginalPath

    withContext(Dispatchers.IO) {
        for ((index, file) in files.withIndex()) {
            if (FileOperationsManager.isCancelled.value) break
            withContext(Dispatchers.Main) {
                FileOperationsManager.update(index, files.size, operationType = OperationType.TRASH)
                FileOperationsManager.currentFileName.value = file.name
            }
            if (file.fileRef != null) {
                val recycledName = getUniqueName(file.name) { name -> File(trashDir, name).exists() }
                val tempTarget = File(trashDir, recycledName)
                try {
                    if (file.fileRef.isDirectory) {
                        val copied = file.fileRef.copyRecursively(tempTarget, overwrite = false)
                        if (!copied) {
                            tempTarget.deleteRecursively()
                            throw Exception("copyRecursively returned false for ${file.fileRef.absolutePath}")
                        }
                        file.fileRef.deleteRecursively()
                    } else {
                        if (!file.fileRef.renameTo(tempTarget)) {
                            // Fallback for cross-volume moves
                            file.fileRef.copyTo(tempTarget, overwrite = false)
                            file.fileRef.delete()
                        }
                    }
                    saveTrashMetadata(recycledName, file.fileRef.absolutePath)
                    recycleLog.add(recycledName to file.fileRef.absolutePath)
                    movedCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                    tempTarget.deleteRecursively()
                }
            } else if (file.documentFileRef != null) {
                val recycledName = getUniqueName(file.name) { name -> File(trashDir, name).exists() }
                val target = File(trashDir, recycledName)
                try {
                    if (file.documentFileRef.isDirectory) {
                        fun copyDocDirToLocal(srcDoc: DocumentFile, destDir: File) {
                            destDir.mkdirs()
                            srcDoc.listFiles().forEach { child ->
                                val childDest = File(destDir, child.name ?: return@forEach)
                                if (child.isDirectory) {
                                    copyDocDirToLocal(child, childDest)
                                } else {
                                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                                        FileOutputStream(childDest).use { input.copyTo(it) }
                                    }
                                }
                            }
                        }
                        copyDocDirToLocal(file.documentFileRef, target)
                        if (target.exists()) {
                            file.documentFileRef.delete()
                            val parentUri = file.documentFileRef.parentFile?.uri?.toString()
                            saveTrashMetadata(recycledName, file.documentFileRef.uri.toString(), parentUri)
                            recycleLog.add(recycledName to file.documentFileRef.uri.toString())
                            movedCount++
                        } else {
                            target.deleteRecursively()
                        }
                    } else {
                        context.contentResolver.openInputStream(file.documentFileRef.uri)?.use { input ->
                            FileOutputStream(target).use { output -> input.copyTo(output) }
                        }
                        if (target.exists() && target.length() == file.length) {
                            file.documentFileRef.delete()
                            val parentUri = file.documentFileRef.parentFile?.uri?.toString()
                            saveTrashMetadata(recycledName, file.documentFileRef.uri.toString(), parentUri)
                            recycleLog.add(recycledName to file.documentFileRef.uri.toString())
                            movedCount++
                        } else {
                            target.delete()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    target.deleteRecursively()
                }
            }
        }
        if (recycleLog.isNotEmpty()) {
            UndoManager.record(UndoAction.Recycle(recycleLog))
        }
    }
    withContext(Dispatchers.Main) {
        FileOperationsManager.finish()
        val msg = if (movedCount == files.size) {
            context.getString(R.string.msg_moved_to_recycle_bin)
        } else {
            context.getString(R.string.msg_moved_to_recycle_bin_partial, movedCount)
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Restores files from the Recycle Bin to their original locations.
 */
suspend fun restoreFiles(context: Context, files: List<UniversalFile>) {
    withContext(Dispatchers.Main) {
        FileOperationsManager.start()
        FileOperationsManager.currentOperationType.value = OperationType.RESTORE
        FileOperationsManager.itemsTotal.intValue = files.size
    }

    withContext(Dispatchers.IO) {
        var restoredCount = 0
        for ((index, file) in files.withIndex()) {
            if (FileOperationsManager.isCancelled.value) break

            val rawMetadata = getOriginalPath(file.name) ?: continue
            val metadataParts = rawMetadata.split("|", limit = 2)
            val originalPath = metadataParts[0]
            val parentUriStr = if (metadataParts.size > 1) metadataParts[1] else null

            withContext(Dispatchers.Main) {
                FileOperationsManager.update(index, files.size, operationType = OperationType.RESTORE)
                FileOperationsManager.currentFileName.value = file.name
            }

            if (originalPath.startsWith("content://")) {
                val sourceFile = file.fileRef ?: continue

                try {
                    if (parentUriStr == null) continue
                    val parentDoc = DocumentFile.fromTreeUri(context, Uri.parse(parentUriStr)) ?: continue

                    val originalUri = Uri.parse(originalPath)
                    val originalFileName = try {
                        val docId = DocumentsContract.getDocumentId(originalUri)
                        docId.substringAfterLast('/')
                    } catch (e: Exception) {
                        Uri.decode(originalUri.lastPathSegment)
                            ?.substringAfterLast('/')
                            ?: file.name
                    }

                    val mimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(
                            MimeTypeMap.getFileExtensionFromUrl(originalFileName)?.lowercase()
                        ) ?: "application/octet-stream"

                    val existingFile = parentDoc.findFile(originalFileName)
                    val targetDoc: DocumentFile? = if (existingFile != null) {
                        val result = FileOperationsManager.resolveCollision(
                            existingFile.name ?: originalFileName,
                            existingFile.isDirectory
                        )
                        when (result) {
                            CollisionResult.CANCEL -> break
                            CollisionResult.REPLACE -> {
                                existingFile.delete()
                                parentDoc.createFile(mimeType, originalFileName)
                            }
                            CollisionResult.KEEP_BOTH -> {
                                val newName = getUniqueName(originalFileName) { parentDoc.findFile(it) != null }
                                parentDoc.createFile(mimeType, newName)
                            }
                            CollisionResult.MERGE -> existingFile
                        }
                    } else {
                        parentDoc.createFile(mimeType, originalFileName)
                    }

                    if (targetDoc != null) {
                        context.contentResolver.openOutputStream(targetDoc.uri)?.use { output ->
                            sourceFile.inputStream().use { input -> input.copyTo(output) }
                        }
                        sourceFile.delete()
                        removeTrashMetadata(file.name)
                        restoredCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                var target = File(originalPath)

                if (target.exists()) {
                    val result = FileOperationsManager.resolveCollision(target.name, target.isDirectory)
                    when (result) {
                        CollisionResult.CANCEL -> break
                        CollisionResult.REPLACE -> {
                            if (target.isDirectory) target.deleteRecursively() else target.delete()
                        }
                        CollisionResult.KEEP_BOTH -> {
                            val parent = target.parentFile
                            val newName = getUniqueName(target.name) { File(parent, it).exists() }
                            target = File(parent, newName)
                        }
                        CollisionResult.MERGE -> {
                            // No-op: fall through to recurse into existing directory
                        }
                    }
                }

                target.parentFile?.mkdirs()
                if (file.fileRef != null && file.fileRef.renameTo(target)) {
                    removeTrashMetadata(file.name)
                    restoredCount++
                } else if (file.fileRef != null) {
                    try {
                        if (file.fileRef.isDirectory) {
                            file.fileRef.copyRecursively(target, overwrite = false)
                        } else {
                            file.fileRef.copyTo(target, overwrite = false)
                        }
                        file.fileRef.deleteRecursively()
                        removeTrashMetadata(file.name)
                        restoredCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            FileOperationsManager.finish()
            Toast.makeText(context, context.getString(R.string.msg_restored_count, restoredCount), Toast.LENGTH_SHORT).show()
        }
    }
}

// endregion