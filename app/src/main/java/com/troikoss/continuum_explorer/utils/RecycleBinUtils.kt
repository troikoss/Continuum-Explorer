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

// region Trash Helpers

private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

internal fun isUuidDir(f: File): Boolean = f.isDirectory && UUID_REGEX.matches(f.name)

fun migrateLegacyTrash(trashDir: File) {
    if (!trashDir.exists() || !trashDir.isDirectory) return
    val children = trashDir.listFiles() ?: return
    val legacy = children.filter { it.name != ".metadata" && !isUuidDir(it) }
    val orphanUuids = children.filter { isUuidDir(it) && (it.listFiles()?.isEmpty() == true) }
    if (legacy.isEmpty() && orphanUuids.isEmpty()) return

    val metadataFile = File(trashDir, ".metadata")
    val props = Properties()
    if (metadataFile.exists()) metadataFile.inputStream().use { props.load(it) }

    for (entry in legacy) {
        val uuid = java.util.UUID.randomUUID().toString()
        val wrapper = File(trashDir, uuid)
        if (!wrapper.mkdirs()) continue
        val moved = File(wrapper, entry.name)
        if (!entry.renameTo(moved)) { wrapper.delete(); continue }
        props.getProperty(entry.name)?.let { props.setProperty(uuid, it); props.remove(entry.name) }
        props.getProperty("${entry.name}.deletedAt")?.let {
            props.setProperty("$uuid.deletedAt", it); props.remove("${entry.name}.deletedAt")
        }
    }
    for (orphan in orphanUuids) {
        orphan.delete()
        props.remove(orphan.name); props.remove("${orphan.name}.deletedAt")
    }
    try { metadataFile.outputStream().use { props.store(it, null) } } catch (e: Exception) { e.printStackTrace() }
}

// endregion

// region Trash Metadata

private fun getTrashMetadataFile(): File {
    val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
    if (!trashDir.exists()) trashDir.mkdirs()
    return File(trashDir, ".metadata")
}

fun saveTrashMetadata(uuid: String, originalPath: String, parentUri: String? = null) {
    try {
        val metadataFile = getTrashMetadataFile()
        val props = Properties()
        if (metadataFile.exists()) { metadataFile.inputStream().use { props.load(it) } }
        val entryValue = if (parentUri != null) "$originalPath|$parentUri" else originalPath
        props.setProperty(uuid, entryValue)
        props.setProperty("$uuid.deletedAt", System.currentTimeMillis().toString())
        metadataFile.outputStream().use { props.store(it, null) }
    } catch (e: Exception) { e.printStackTrace() }
}

fun getDeletedAt(uuid: String): Long? {
    return try {
        val metadataFile = getTrashMetadataFile()
        if (!metadataFile.exists()) return null
        val props = Properties()
        metadataFile.inputStream().use { props.load(it) }
        props.getProperty("$uuid.deletedAt")?.toLongOrNull()
    } catch (_: Exception) { null }
}

fun getOriginalPath(uuid: String): String? {
    return try {
        val metadataFile = getTrashMetadataFile()
        if (!metadataFile.exists()) return null
        val props = Properties()
        metadataFile.inputStream().use { props.load(it) }
        props.getProperty(uuid)
    } catch (_: Exception) { null }
}

fun getDeletedFrom(uuid: String): String? {
    val rawMetadata = getOriginalPath(uuid) ?: return null
    val parts = rawMetadata.split("|", limit = 2)
    val originalPath = parts[0]
    return if (originalPath.startsWith("content://")) {
        parts.getOrNull(1) ?: originalPath
    } else {
        File(originalPath).parent
    }
}

fun removeTrashMetadata(uuid: String) {
    try {
        val metadataFile = getTrashMetadataFile()
        if (!metadataFile.exists()) return
        val props = Properties()
        metadataFile.inputStream().use { props.load(it) }
        props.remove(uuid)
        props.remove("$uuid.deletedAt")
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
        val fileRef = file.fileRef
        val docRef = file.documentFileRef
        val children = try {
            when {
                file.isArchiveEntry -> emptyList()
                fileRef != null -> fileRef.listFiles()?.map { it.toUniversal() } ?: emptyList()
                docRef != null -> docRef.listFiles().map { it.toUniversal() }
                else -> file.provider.listChildren(file.providerId)
            }
        } catch (_: Exception) { emptyList() }
        children.forEach { child ->
            if (FileOperationsManager.isCancelled.value) return@forEach
            deleteRecursivelyWithProgress(context, child, onProgress)
        }
    }

    if (FileOperationsManager.isCancelled.value) return false
    val fileRef2 = file.fileRef
    val success = try {
        when {
            file.isArchiveEntry -> false
            fileRef2 != null -> fileRef2.delete()
            file.documentFileRef != null -> file.documentFileRef!!.delete()
            else -> file.provider.delete(file.providerId)
        }
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

    val anyRemote = files.any { it.provider.capabilities.isRemote }
    val effectivePermanent = forcePermanent || anyRemote
    val behavior = SettingsManager.deleteBehavior.value

    val result = if (silent) {
        if (effectivePermanent) DeleteResult.PERMANENT else DeleteResult.RECYCLE
    } else {
        when (behavior) {
            DeleteBehavior.ASK -> FileOperationsManager.confirmDelete(files.size, permanentOnly = effectivePermanent)
            DeleteBehavior.RECYCLE -> if (effectivePermanent) DeleteResult.PERMANENT else DeleteResult.RECYCLE
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
                val parent = file.fileRef?.parentFile
                if (parent != null && isUuidDir(parent) && parent.parentFile?.name == ".Trash") {
                    parent.deleteRecursively()
                    removeTrashMetadata(parent.name)
                }
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
    val (remote, trashable) = files.partition { it.provider.capabilities.isRemote }
    if (remote.isNotEmpty()) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.msg_remote_trash_skipped), Toast.LENGTH_SHORT).show()
        }
    }
    if (trashable.isEmpty()) return
    @Suppress("NAME_SHADOWING") val files = trashable

    val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
    if (!trashDir.exists()) trashDir.mkdirs()
    migrateLegacyTrash(trashDir)

    var totalBytesToMove = 0L
    withContext(Dispatchers.IO) { totalBytesToMove = files.sumOf { calculateSizeRecursively(context, it) } }
    withContext(Dispatchers.Main) {
        FileOperationsManager.itemsTotal.intValue = files.size
        FileOperationsManager.currentProcessedItems.intValue = files.size
        FileOperationsManager.totalSize.longValue = totalBytesToMove
        FileOperationsManager.currentOperationType.value = OperationType.TRASH
    }

    var movedCount = 0
    val recycleLog = mutableListOf<Triple<String, String, String>>() // (uuid, originalPath, innerName)

    withContext(Dispatchers.IO) {
        for ((index, file) in files.withIndex()) {
            if (FileOperationsManager.isCancelled.value) break
            withContext(Dispatchers.Main) {
                FileOperationsManager.update(index, files.size, operationType = OperationType.TRASH)
                FileOperationsManager.currentFileName.value = file.name
            }
            val fileRef = file.fileRef
            val documentFileRef = file.documentFileRef
            if (fileRef != null) {
                val uuid = java.util.UUID.randomUUID().toString()
                val wrapper = File(trashDir, uuid).apply { mkdirs() }
                val tempTarget = File(wrapper, fileRef.name)
                try {
                    if (fileRef.isDirectory) {
                        val copied = fileRef.copyRecursively(tempTarget, overwrite = false)
                        if (!copied) {
                            wrapper.deleteRecursively()
                            throw Exception("copyRecursively returned false for ${fileRef.absolutePath}")
                        }
                        fileRef.deleteRecursively()
                    } else {
                        if (!fileRef.renameTo(tempTarget)) {
                            // Fallback for cross-volume moves
                            fileRef.copyTo(tempTarget, overwrite = false)
                            fileRef.delete()
                        }
                    }
                    saveTrashMetadata(uuid, fileRef.absolutePath)
                    recycleLog.add(Triple(uuid, fileRef.absolutePath, fileRef.name))
                    movedCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                    wrapper.deleteRecursively()
                }
            } else if (documentFileRef != null) {
                val uuid = java.util.UUID.randomUUID().toString()
                val innerName = documentFileRef.name ?: "unnamed"
                val wrapper = File(trashDir, uuid).apply { mkdirs() }
                val target = File(wrapper, innerName)
                try {
                    if (documentFileRef.isDirectory) {
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
                        copyDocDirToLocal(documentFileRef, target)
                        if (target.exists()) {
                            documentFileRef.delete()
                            val parentUri = file.parentId ?: documentFileRef.parentFile?.uri?.toString()
                            saveTrashMetadata(uuid, documentFileRef.uri.toString(), parentUri)
                            recycleLog.add(Triple(uuid, documentFileRef.uri.toString(), innerName))
                            movedCount++
                        } else {
                            wrapper.deleteRecursively()
                        }
                    } else {
                        context.contentResolver.openInputStream(documentFileRef.uri)?.use { input ->
                            FileOutputStream(target).use { output -> input.copyTo(output) }
                        }
                        if (target.exists() && target.length() == file.length) {
                            documentFileRef.delete()
                            val parentUri = file.parentId ?: documentFileRef.parentFile?.uri?.toString()
                            saveTrashMetadata(uuid, documentFileRef.uri.toString(), parentUri)
                            recycleLog.add(Triple(uuid, documentFileRef.uri.toString(), innerName))
                            movedCount++
                        } else {
                            wrapper.deleteRecursively()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    wrapper.deleteRecursively()
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

            val parentDir = file.fileRef?.parentFile
            val uuid = parentDir?.name
            if (uuid == null || !UUID_REGEX.matches(uuid) || parentDir.parentFile?.name != ".Trash") continue
            val rawMetadata = getOriginalPath(uuid) ?: continue
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
                    // Original filename is preserved as the inner file's name in the UUID wrapper.
                    val originalFileName = sourceFile.name

                    if (parentUriStr == null) {
                        // No tree URI stored (file was opened via single-file picker).
                        // Attempt a local path fallback for ExternalStorageProvider files.
                        val originalUri = Uri.parse(originalPath)
                        val docId = try { DocumentsContract.getDocumentId(originalUri) } catch (_: Exception) { null }
                        if (docId != null && docId.startsWith("primary:")) {
                            val relativePath = docId.removePrefix("primary:")
                            var localTarget = File(Environment.getExternalStorageDirectory(), relativePath)
                            if (localTarget.exists()) {
                                val result = FileOperationsManager.resolveCollision(localTarget.name, localTarget.isDirectory)
                                when (result) {
                                    CollisionResult.CANCEL -> break
                                    CollisionResult.REPLACE -> {
                                        if (localTarget.isDirectory) localTarget.deleteRecursively() else localTarget.delete()
                                    }
                                    CollisionResult.KEEP_BOTH -> {
                                        val parent = localTarget.parentFile
                                        val newName = getUniqueName(localTarget.name) { File(parent, it).exists() }
                                        localTarget = File(parent, newName)
                                    }
                                    CollisionResult.MERGE -> {}
                                }
                            }
                            localTarget.parentFile?.mkdirs()
                            val moved = sourceFile.renameTo(localTarget)
                            if (!moved) {
                                sourceFile.copyTo(localTarget, overwrite = false)
                                sourceFile.delete()
                            }
                            removeTrashMetadata(uuid)
                            parentDir.delete()
                            restoredCount++
                        }
                        continue
                    }

                    val parentDoc = DocumentFile.fromTreeUri(context, Uri.parse(parentUriStr)) ?: continue

                    val ext = originalFileName.substringAfterLast('.', "").lowercase().ifEmpty { null }
                    val mimeType = ext?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                        ?: "application/octet-stream"

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
                        val outputStream = context.contentResolver.openOutputStream(targetDoc.uri)
                        if (outputStream != null) {
                            outputStream.use { output ->
                                sourceFile.inputStream().use { input -> input.copyTo(output) }
                            }
                            sourceFile.delete()
                            removeTrashMetadata(uuid)
                            parentDir.delete()
                            restoredCount++
                        }
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
                val fileRef = file.fileRef
                if (fileRef != null && fileRef.renameTo(target)) {
                    removeTrashMetadata(uuid)
                    parentDir.delete()
                    restoredCount++
                } else if (fileRef != null) {
                    try {
                        if (fileRef.isDirectory) {
                            fileRef.copyRecursively(target, overwrite = false)
                        } else {
                            fileRef.copyTo(target, overwrite = false)
                        }
                        fileRef.deleteRecursively()
                        removeTrashMetadata(uuid)
                        parentDir.delete()
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