package com.troikoss.continuum_explorer.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.OperationType
import com.troikoss.continuum_explorer.managers.UndoAction
import com.troikoss.continuum_explorer.managers.UndoManager
import com.troikoss.continuum_explorer.model.StorageProvider
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import com.troikoss.continuum_explorer.providers.SafProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

/**
 * Global state to track files that have been "Cut" so they can be moved (deleted) after pasting.
 */
object PendingCut {
    var files: List<UniversalFile> = emptyList()
    var isActive: Boolean = false
    fun clear() {
        files = emptyList()
        isActive = false
    }
}

/**
 * Copies selected files to the system clipboard.
 */
fun copyToClipboard(context: Context, files: List<UniversalFile>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    if (files.isEmpty()) return

    PendingCut.clear()
    PendingCut.files = files
    PendingCut.isActive = false

    val firstUri = getUriForUniversalFile(context, files[0]) ?: return
    val clipData = ClipData.newUri(context.contentResolver, "File", firstUri)

    for (i in 1 until files.size) {
        val uri = getUriForUniversalFile(context, files[i])
        if (uri != null) {
            clipData.addItem(ClipData.Item(uri))
        }
    }

    clipboard.setPrimaryClip(clipData)
}

/**
 * Marks files for moving (Cut). They stay on the clipboard but are flagged for deletion after paste.
 */
fun cutToClipboard(context: Context, files: List<UniversalFile>) {
    copyToClipboard(context, files)
    PendingCut.isActive = true
}

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) result = cursor.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result ?: "Unknown"
}

/**
 * Performs the actual file copying or moving from the clipboard to the current directory.
 * Returns a list of names of the files that were successfully pasted.
 */
suspend fun pasteFromClipboard(
    context: Context,
    currentPath: File?,
    currentSafUri: Uri?,
    destProvider: StorageProvider? = null,
    destParentId: String? = null,
    preloadedClipData: ClipData? = null
): List<String> {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = preloadedClipData ?: clipboard.primaryClip
    val pastedFileNames = mutableListOf<String>()
    val pasteLog = mutableListOf<Pair<String, String>>() // For Undo: Source to Dest

    if (clipData == null || clipData.itemCount == 0) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.msg_empty_clipboard), Toast.LENGTH_SHORT).show()
            FileOperationsManager.finish()
        }
        return emptyList()
    }

    val totalCount = clipData.itemCount

    val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
    val isPasteToTrash = currentPath?.absolutePath == trashDir.absolutePath

    val firstClipUriStr = clipData.getItemAt(0).uri?.toString()
    val firstPendingUriStr = if (PendingCut.files.isNotEmpty()) getUriForUniversalFile(context, PendingCut.files[0])?.toString() else null
    val usePendingCut = PendingCut.files.isNotEmpty() &&
            PendingCut.files.size == totalCount &&
            firstClipUriStr != null && firstPendingUriStr != null &&
            (firstClipUriStr == firstPendingUriStr || Uri.decode(firstClipUriStr) == Uri.decode(firstPendingUriStr))

    val isMove = usePendingCut && PendingCut.isActive
    val destSafDoc = if (currentSafUri != null) DocumentFile.fromTreeUri(context, currentSafUri) else null

    var totalBytesToCopy = 0L
    withContext(Dispatchers.IO) {
        if (usePendingCut) {
            totalBytesToCopy = PendingCut.files.sumOf { calculateSizeRecursively(context, it) }
        } else {
            for (i in 0 until totalCount) {
                if (FileOperationsManager.isCancelled.value) return@withContext
                val item = clipData.getItemAt(i)
                val uri = item.uri ?: continue
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && sizeIndex >= 0) {
                        totalBytesToCopy += cursor.getLong(sizeIndex)
                    }
                }
            }
        }
    }

    if (FileOperationsManager.isCancelled.value) {
        withContext(Dispatchers.Main) { FileOperationsManager.finish() }
        return emptyList()
    }

    withContext(Dispatchers.Main) {
        FileOperationsManager.totalSize.longValue = totalBytesToCopy
        FileOperationsManager.itemsTotal.intValue = totalCount
        FileOperationsManager.currentProcessedItems.intValue = 0
        PendingCut.isActive = isMove
    }

    var globalBytesCopied = 0L
    val buffer = ByteArray(32 * 1024)
    var lastUpdateTime = System.currentTimeMillis()
    val speedWindow = ArrayDeque<Pair<Long, Long>>()
    speedWindow.add(lastUpdateTime to 0L)
    val windowMs = 3000L

    withContext(Dispatchers.IO) {
        for (i in 0 until totalCount) {
            if (FileOperationsManager.isCancelled.value) break

            val item = clipData.getItemAt(i)
            val sourceUri = item.uri ?: continue

            val sourceFile = if (usePendingCut) {
                PendingCut.files[i]
            } else {
                val name = getFileName(context, sourceUri)
                var isDir = false
                var fileRef: File? = null

                if (sourceUri.scheme == "file") {
                    val f = File(sourceUri.path ?: "")
                    isDir = f.isDirectory
                    fileRef = f
                } else if (sourceUri.scheme == "content") {
                    val mimeType = context.contentResolver.getType(sourceUri)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        isDir = true
                    }
                }

                val docFile = DocumentFile.fromSingleUri(context, sourceUri)
                if (!isDir && docFile != null && docFile.isDirectory) {
                    isDir = true
                }

                if (fileRef != null) {
                    UniversalFile(
                        name = name,
                        isDirectory = isDir,
                        lastModified = fileRef.lastModified(),
                        length = fileRef.length(),
                        provider = LocalProvider,
                        providerId = fileRef.absolutePath,
                        parentId = fileRef.parentFile?.absolutePath,
                    )
                } else {
                    UniversalFile(
                        name = name,
                        isDirectory = isDir,
                        lastModified = docFile?.lastModified() ?: 0L,
                        length = docFile?.length() ?: 0L,
                        provider = SafProvider,
                        providerId = sourceUri.toString(),
                        parentId = docFile?.parentFile?.uri?.toString(),
                        mimeType = docFile?.type,
                    )
                }
            }

            withContext(Dispatchers.Main) {
                FileOperationsManager.update(i, totalCount, operationType = OperationType.COPY)
                FileOperationsManager.currentFileName.value = sourceFile.name
            }

            try {
                val finalTargetName = copyRecursively(
                    context = context,
                    source = sourceFile,
                    destLocal = currentPath,
                    destSaf = destSafDoc,
                    destProvider = destProvider,
                    destParentId = destParentId,
                ) { src, outputStream ->
                    val inputStream = if (src.isArchiveEntry) {
                        val parts = src.absolutePath.split("::")
                        if (parts.size >= 2) {
                            val rootId = parts[0]
                            val archiveSource: Any = if (rootId.startsWith("/")) File(rootId) else Uri.parse(rootId)
                            ZipUtils.getArchiveInputStream(context, archiveSource, src.archivePath ?: "")
                        } else null
                    } else if (src.fileRef != null) {
                        src.fileRef!!.inputStream()
                    } else if (src.documentFileRef != null) {
                        context.contentResolver.openInputStream(src.documentFileRef!!.uri)
                    } else {
                        try { src.provider.openInput(src.providerId) } catch (_: Exception) { null }
                    }

                    if (inputStream != null) {
                        inputStream.use { input ->
                            var bytesRead = input.read(buffer)
                            while (bytesRead >= 0) {
                                if (FileOperationsManager.isCancelled.value) break
                                outputStream.write(buffer, 0, bytesRead)
                                globalBytesCopied += bytesRead
                                val now = System.currentTimeMillis()
                                if (now - lastUpdateTime >= 300) {
                                    speedWindow.add(now to globalBytesCopied)
                                    while (speedWindow.size > 2 && now - speedWindow.first.first > windowMs) {
                                        speedWindow.removeFirst()
                                    }
                                    val startPoint = speedWindow.first
                                    val timeDiff = now - startPoint.first
                                    val bytesDiff = globalBytesCopied - startPoint.second
                                    val currentSpeed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0L
                                    val remainingBytes = totalBytesToCopy - globalBytesCopied
                                    val timeRemaining = if (currentSpeed > 0) (remainingBytes * 1000) / currentSpeed else 0L
                                    withContext(Dispatchers.Main) {
                                        FileOperationsManager.updateDetailed(
                                            processedBytes = globalBytesCopied,
                                            totalBytes = totalBytesToCopy,
                                            speed = currentSpeed,
                                            remainingMillis = timeRemaining,
                                            fileName = src.name
                                        )
                                    }
                                    lastUpdateTime = now
                                }
                                bytesRead = input.read(buffer)
                            }
                        }
                    }
                }

                if (!FileOperationsManager.isCancelled.value && finalTargetName != null) {
                    pastedFileNames.add(sourceFile.name)
                    if (currentPath != null) {
                        pasteLog.add(sourceFile.absolutePath to File(currentPath, finalTargetName).absolutePath)
                    } else if (destSafDoc != null) {
                        val destFileUri = destSafDoc.findFile(finalTargetName)?.uri?.toString()
                        if (destFileUri != null) {
                            pasteLog.add(sourceFile.absolutePath to destFileUri)
                        }
                    } else if (destProvider != null) {
                        pasteLog.add(sourceFile.absolutePath to finalTargetName)
                    }
                    val sourceFileRef = sourceFile.fileRef
                    if (isPasteToTrash && sourceFileRef != null && !sourceFile.provider.capabilities.isRemote) {
                        saveTrashMetadata(finalTargetName, sourceFileRef.absolutePath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) { FileOperationsManager.itemsProcessed.intValue = i + 1 }
        }

        if (isMove && pastedFileNames.isNotEmpty() && !FileOperationsManager.isCancelled.value) {
            val successfullyCopiedPaths = pasteLog.map { it.first }.toSet()
            PendingCut.files
                .filter { it.absolutePath in successfullyCopiedPaths }
                .forEach { file ->
                    try { file.provider.delete(file.providerId) } catch (_: Exception) {}
                }
            PendingCut.clear()
        }

        if (pastedFileNames.isNotEmpty()) {
            UndoManager.record(UndoAction.Paste(isMove, pasteLog))
        }
    }

    withContext(Dispatchers.Main) {
        val wasCancelled = FileOperationsManager.isCancelled.value
        FileOperationsManager.finish()
        val message = if (wasCancelled) {
            context.getString(R.string.msg_operation_cancelled)
        } else if (isMove) {
            context.getString(R.string.msg_moved_count, pastedFileNames.size)
        } else {
            context.getString(R.string.msg_pasted_count, pastedFileNames.size)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    return pastedFileNames
}