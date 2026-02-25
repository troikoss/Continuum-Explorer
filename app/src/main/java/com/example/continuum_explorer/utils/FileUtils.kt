package com.example.continuum_explorer.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.Properties

/**
 * Extension functions to convert native File and DocumentFile types 
 * into our common UniversalFile wrapper.
 */
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
 * Gets a human-readable file type string
 */
fun getFileType(file: UniversalFile): String {
    if (file.isDirectory) return "Folder"

    val extension = file.name.substringAfterLast(".", "").lowercase()
    val extensionString = extension.uppercase()

    return when (extension) {
        "zip", "rar", "7z", "tar", "gz" -> "Archive"
        "jpg", "jpeg", "bmp", "png", "gif", "webp" -> "Image"
        "mp4", "mkv", "avi", "mov", "webm" -> "Video"
        "mp3", "wav", "ogg", "m4a", "flac" -> "Audio"
        "txt", "doc", "docx", "odt", "pdf" -> "Document"
        "" -> "File"
        else -> "$extensionString File"
    }
}

/**
 * Extracts Video Resolution metadata
 */
fun getVideoResolution(context: Context, file: UniversalFile): String? {
    val retriever = MediaMetadataRetriever()
    return try {
        if (file.fileRef != null) {
            retriever.setDataSource(file.fileRef.absolutePath)
        } else if (file.documentFileRef != null) {
            retriever.setDataSource(context, file.documentFileRef.uri)
        } else {
            return null
        }

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

        if (width != null && height != null) {
            if (rotation == 90 || rotation == 270) {
                "${height}x${width}"
            } else {
                "${width}x${height}"
            }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    } finally {
        try { retriever.release() } catch (e: Exception) {}
    }
}

/**
 * Extracts Media Duration metadata
 */
fun getMediaDuration(context: Context, file: UniversalFile): String? {
    val retriever = MediaMetadataRetriever()
    return try {
        if (file.fileRef != null) {
            retriever.setDataSource(file.fileRef.absolutePath)
        } else if (file.documentFileRef != null) {
            retriever.setDataSource(context, file.documentFileRef.uri)
        } else {
            return null
        }

        val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        if (durationString != null) {
            val millis = durationString.toLong()
            val totalSeconds = millis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60

            if (minutes >= 60) {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                String.format("%02d:%02d:%02d", hours, remainingMinutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    } finally {
        try { retriever.release() } catch (e: Exception) {}
    }
}

/**
 * Extracts Image Resolution metadata
 */
fun getImageResolution(context: Context, file: UniversalFile): String {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    try {
        val inputStream = if (file.fileRef != null) {
            file.fileRef.inputStream()
        } else if (file.documentFileRef != null) {
            context.contentResolver.openInputStream(file.documentFileRef.uri)
        } else {
            null
        }
        inputStream?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        if (options.outWidth > 0 && options.outHeight > 0) {
            return "${options.outWidth}x${options.outHeight}"
        }
        return "Unknown Resolution"
    } catch (e: Exception) {
        return "Unknown Resolution"
    }
}

/**
 * Copies selected files to the system clipboard.
 */
fun copyToClipboard(context: Context, files: List<UniversalFile>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    if (files.isEmpty()) return

    // Reset any pending move state
    PendingCut.clear()
    PendingCut.files = files
    PendingCut.isActive = false 

    val firstUri = getUriForUniversalFile(context, files[0]) ?: return
    val clipData = ClipData.newUri(context.contentResolver, "File", firstUri)

    // Add additional files to the same clip
    for (i in 1 until files.size) {
        val uri = getUriForUniversalFile(context, files[i])
        if (uri != null) {
            clipData.addItem(ClipData.Item(uri))
        }
    }

    clipboard.setPrimaryClip(clipData)
}

/**
 * Marks files for moving (Cut). They stay on the clipboard but are flagged to be deleted after paste.
 */
fun cutToClipboard(context: Context, files: List<UniversalFile>) {
    copyToClipboard(context, files)
    PendingCut.isActive = true
}

/**
 * Calculates the total size of a list of files, recursing into directories.
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
            } else if (file.fileRef != null) {
                file.fileRef.listFiles()?.forEach { 
                     if (FileOperationsManager.isCancelled.value) return 0L
                    size += calculateSizeRecursively(context, it.toUniversal()) 
                }
            } else if (file.documentFileRef != null) {
                file.documentFileRef.listFiles().forEach {
                    if (FileOperationsManager.isCancelled.value) return 0L
                    size += calculateSizeRecursively(context, it.toUniversal())
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

private fun getUniqueName(name: String, exists: (String) -> Boolean): String {
    if (!exists(name)) return name
    
    val lastDot = name.lastIndexOf('.')
    val base = if (lastDot != -1) name.substring(0, lastDot) else name
    val ext = if (lastDot != -1) name.substring(lastDot) else ""
    
    var count = 1
    var newName: String
    do {
        newName = "$base ($count)$ext"
        count++
    } while (exists(newName))
    
    return newName
}

/**
 * Recursively copies a file or directory.
 * Returns the final name of the copied item.
 */
suspend fun copyRecursively(
    context: Context,
    source: UniversalFile,
    destLocal: File?,
    destSaf: DocumentFile?,
    onCopyFile: suspend (UniversalFile, OutputStream) -> Unit
): String? {
    if (FileOperationsManager.isCancelled.value) return null

    var targetName = source.name
    
    // Check for collision at this level
    val alreadyExists = if (destLocal != null) {
        File(destLocal, targetName).exists()
    } else if (destSaf != null) {
        destSaf.findFile(targetName) != null
    } else false

    if (alreadyExists) {
        val result = FileOperationsManager.resolveCollision(targetName)
        when (result) {
            CollisionResult.CANCEL -> {
                FileOperationsManager.cancel()
                return null
            }
            CollisionResult.REPLACE -> {
                // Delete existing one first
                if (destLocal != null) {
                    val existing = File(destLocal, targetName)
                    if (existing.isDirectory) existing.deleteRecursively() else existing.delete()
                } else if (destSaf != null) {
                    destSaf.findFile(targetName)?.delete()
                }
            }
            CollisionResult.KEEP_BOTH -> {
                targetName = getUniqueName(targetName) { name ->
                    if (destLocal != null) File(destLocal, name).exists()
                    else destSaf?.findFile(name) != null
                }
            }
        }
    }

    if (source.isDirectory) {
        // Create directory in destination
        var newDestLocal: File? = null
        var newDestSaf: DocumentFile? = null
        
        if (destLocal != null) {
            newDestLocal = File(destLocal, targetName)
            if (!newDestLocal.exists()) {
                newDestLocal.mkdir()
            }
        } else if (destSaf != null) {
            newDestSaf = destSaf.findFile(targetName) ?: destSaf.createDirectory(targetName)
        }
        
        // Iterate children
        if (source.isArchiveEntry) {
            val parts = source.absolutePath.split("::")
            if (parts.size >= 2) {
                val rootId = parts[0]
                val archiveSource: Any = if (rootId.startsWith("/")) File(rootId) else Uri.parse(rootId)
                ZipUtils.getArchiveChildren(context, archiveSource, source.archivePath ?: "").forEach { child ->
                    if (FileOperationsManager.isCancelled.value) return@forEach
                    copyRecursively(context, child, newDestLocal, newDestSaf, onCopyFile)
                }
            }
        } else if (source.fileRef != null) {
            source.fileRef.listFiles()?.forEach { child ->
                if (FileOperationsManager.isCancelled.value) return@forEach
                copyRecursively(context, child.toUniversal(), newDestLocal, newDestSaf, onCopyFile)
            }
        } else if (source.documentFileRef != null) {
            source.documentFileRef.listFiles().forEach { child ->
                if (FileOperationsManager.isCancelled.value) return@forEach
                copyRecursively(context, child.toUniversal(), newDestLocal, newDestSaf, onCopyFile)
            }
        }
        
    } else {
        // Copy file
        val outputStream: OutputStream? = if (destLocal != null) {
             FileOutputStream(File(destLocal, targetName))
        } else if (destSaf != null) {
             val mime = source.documentFileRef?.type ?: "*/*"
             val newFile = destSaf.createFile(mime, targetName)
             if (newFile != null) context.contentResolver.openOutputStream(newFile.uri) else null
        } else null
        
        if (outputStream != null) {
             try {
                onCopyFile(source, outputStream)
             } catch (e: Exception) {
                throw e
             } finally {
                 try { outputStream.close() } catch (_: Exception) {}
                 
                 // If cancelled, delete the partial file
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
    currentPath: File?,       // Destination for local file system
    currentSafUri: Uri?,       // Destination for SAF (SD Card/External)
    preloadedClipData: ClipData? = null
): List<String> {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = preloadedClipData ?: clipboard.primaryClip
    val pastedFileNames = mutableListOf<String>()
    val pasteLog = mutableListOf<Pair<String, String>>() // For Undo: Source to Dest

    if (clipData == null || clipData.itemCount == 0) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            FileOperationsManager.finish()
        }
        return emptyList()
    }

    val isMove = PendingCut.isActive
    val totalCount = clipData.itemCount

    val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
    val isPasteToTrash = currentPath?.absolutePath == trashDir.absolutePath
    
    // Check if we can use our internal file references safely
    val usePendingCut = PendingCut.files.isNotEmpty() && 
                        PendingCut.files.size == totalCount &&
                        (clipData.getItemAt(0).uri == getUriForUniversalFile(context, PendingCut.files[0]))

    val destSafDoc = if (currentSafUri != null) DocumentFile.fromTreeUri(context, currentSafUri) else null

    // 1. Calculate Total Size
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
        FileOperationsManager.start()
        FileOperationsManager.totalSize.longValue = totalBytesToCopy
        FileOperationsManager.itemsTotal.intValue = totalCount
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
                UniversalFile(
                    name = name,
                    isDirectory = false, 
                    lastModified = 0L,
                    length = 0L,
                    documentFileRef = DocumentFile.fromSingleUri(context, sourceUri),
                    absolutePath = sourceUri.toString()
                )
            }
            
             withContext(Dispatchers.Main) {
                FileOperationsManager.update(i, totalCount, "Copying ${sourceFile.name}")
                FileOperationsManager.currentFileName.value = sourceFile.name
            }

            try {
                val finalTargetName = copyRecursively(
                    context = context,
                    source = sourceFile,
                    destLocal = currentPath,
                    destSaf = destSafDoc
                ) { src, outputStream ->
                    val inputStream = if (src.isArchiveEntry) {
                        val parts = src.absolutePath.split("::")
                        if (parts.size >= 2) {
                            val rootId = parts[0]
                            val archiveSource: Any = if (rootId.startsWith("/")) File(rootId) else Uri.parse(rootId)
                            ZipUtils.getArchiveInputStream(context, archiveSource, src.archivePath ?: "")
                        } else null
                    } else if (src.fileRef != null) {
                         src.fileRef.inputStream()
                    } else {
                        val uri = src.documentFileRef?.uri ?: sourceUri
                        context.contentResolver.openInputStream(uri)
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
                    }
                    // Save restore metadata if pasting to trash
                    if (isPasteToTrash && sourceFile.fileRef != null) {
                        saveTrashMetadata(finalTargetName, sourceFile.fileRef.absolutePath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
             
            withContext(Dispatchers.Main) { FileOperationsManager.itemsProcessed.intValue = i + 1 }
        }
        
        if (isMove && pastedFileNames.isNotEmpty() && !FileOperationsManager.isCancelled.value) {
            withContext(Dispatchers.Main) { FileOperationsManager.statusMessage.value = "Deleting source files..." }
            // Perform a silent permanent delete after moving
            deleteFiles(context, PendingCut.files, forcePermanent = true, silent = true)
            PendingCut.clear()
        }
        
        if (pastedFileNames.isNotEmpty()) {
            UndoManager.record(UndoAction.Paste(isMove, pasteLog))
        }
    }

    withContext(Dispatchers.Main) {
        val wasCancelled = FileOperationsManager.isCancelled.value
        FileOperationsManager.finish()
        val message = if (wasCancelled) "Operation Cancelled" else if (isMove) "Moved ${pastedFileNames.size} files" else "Pasted ${pastedFileNames.size} files"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    return pastedFileNames
}

/**
 * Helper to delete a file or directory recursively, updating progress.
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
                 // Deleting inside archives not supported yet via this method
                 emptyList()
            } else if (file.fileRef != null) {
                file.fileRef.listFiles()?.map { it.toUniversal() }
            } else if (file.documentFileRef != null) {
                file.documentFileRef.listFiles().map { it.toUniversal() }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
        children?.forEach { child ->
             if (FileOperationsManager.isCancelled.value) return@forEach
             deleteRecursivelyWithProgress(context, child, onProgress)
        }
    }
    val success = try {
        if (file.isArchiveEntry) {
             false // Deleting archive entries not supported yet
        } else if (file.fileRef != null) file.fileRef.delete()
        else file.documentFileRef?.delete() == true
    } catch (e: Exception) { false }
    if (success) onProgress(file, length)
    return success
}

/**
 * Deletes a list of files from the system.
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
        FileOperationsManager.totalSize.longValue = totalBytesToDelete
        FileOperationsManager.statusMessage.value = "Deleting..."
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
                FileOperationsManager.update(index, files.size, "Deleting ${file.name}...")
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
                     val currentSpeed = if (now - startPoint.first > 0) (globalBytesDeleted - startPoint.second) * 1000 / (now - startPoint.first) else 0L
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
            val msg = if (FileOperationsManager.isCancelled.value) "Deletion Cancelled" else "Deleted $deletedCount files"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun getTrashMetadataFile(): File {
    val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
    if (!trashDir.exists()) trashDir.mkdirs()
    return File(trashDir, ".metadata")
}

fun saveTrashMetadata(recycledName: String, originalPath: String) {
    try {
        val metadataFile = getTrashMetadataFile()
        val props = Properties()
        if (metadataFile.exists()) { metadataFile.inputStream().use { props.load(it) } }
        props.setProperty(recycledName, originalPath)
        metadataFile.outputStream().use { props.store(it, null) }
    } catch (e: Exception) { e.printStackTrace() }
}

fun getOriginalPath(recycledName: String): String? {
    return try {
        val metadataFile = getTrashMetadataFile()
        if (!metadataFile.exists()) return null
        val props = Properties()
        metadataFile.inputStream().use { props.load(it) }
        props.getProperty(recycledName)
    } catch (e: Exception) { null }
}

fun removeTrashMetadata(recycledName: String) {
    try {
        val metadataFile = getTrashMetadataFile()
        if (!metadataFile.exists()) return
        val props = Properties()
        metadataFile.inputStream().use { props.load(it) }
        props.remove(recycledName)
        metadataFile.outputStream().use { props.store(it, null) }
    } catch (e: Exception) { e.printStackTrace() }
}

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
        FileOperationsManager.totalSize.longValue = totalBytesToMove
        FileOperationsManager.statusMessage.value = "Moving to Recycle Bin..."
    }

    var movedCount = 0
    val recycleLog = mutableListOf<Pair<String, String>>() // RecycledName to OriginalPath

    withContext(Dispatchers.IO) {
        for ((index, file) in files.withIndex()) {
            if (FileOperationsManager.isCancelled.value) break
            withContext(Dispatchers.Main) {
                FileOperationsManager.update(index, files.size, "Moving ${file.name} to Trash...")
                FileOperationsManager.currentFileName.value = file.name
            }
            if (file.fileRef != null) {
                val recycledName = getUniqueName(file.name) { name -> File(trashDir, name).exists() }
                val target = File(trashDir, recycledName)
                if (file.fileRef.renameTo(target)) {
                    saveTrashMetadata(recycledName, file.fileRef.absolutePath)
                    recycleLog.add(recycledName to file.fileRef.absolutePath)
                    movedCount++
                }
            }
        }
        if (recycleLog.isNotEmpty()) {
            UndoManager.record(UndoAction.Recycle(recycleLog))
        }
    }
    withContext(Dispatchers.Main) {
        FileOperationsManager.finish()
        val msg = if (movedCount == files.size) "Moved to Recycle Bin" else "Moved $movedCount files to Recycle Bin (some failed)"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Restores files from the Recycle Bin to their original locations.
 */
suspend fun restoreFiles(context: Context, files: List<UniversalFile>) {
    withContext(Dispatchers.IO) {
        var restoredCount = 0
        for (file in files) {
            val originalPath = getOriginalPath(file.name) ?: continue
            val target = File(originalPath)
            target.parentFile?.mkdirs()
            if (file.fileRef != null && file.fileRef.renameTo(target)) {
                removeTrashMetadata(file.name)
                restoredCount++
            }
        }
        withContext(Dispatchers.Main) { Toast.makeText(context, "Restored $restoredCount files", Toast.LENGTH_SHORT).show() }
    }
}

/**
 * Renames a file.
 */
suspend fun renameFile(file: UniversalFile, newName: String): Boolean {
    if (FileOperationsManager.isCancelled.value) return false
    return withContext(Dispatchers.IO) {
        try {
            if (file.fileRef != null) {
                val oldName = file.fileRef.name
                val newFile = File(file.fileRef.parentFile, newName)
                if (file.fileRef.renameTo(newFile)) {
                    UndoManager.record(UndoAction.Rename(newFile.parentFile, oldName, newName))
                    true
                } else false
            } else if (file.documentFileRef != null) {
                file.documentFileRef.renameTo(newName)
            } else false
        } catch (e: Exception) { e.printStackTrace(); false }
    }
}

/**
 * Creates a new directory.
 */
suspend fun createDirectory(context: Context, parentPath: File?, parentSafUri: Uri?, name: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (parentPath != null) {
                val newDir = File(parentPath, name)
                if (newDir.exists()) false else newDir.mkdir()
            } else if (parentSafUri != null) {
                val parentDoc = DocumentFile.fromTreeUri(context, parentSafUri)
                parentDoc?.createDirectory(name) != null
            } else false
        } catch (e: Exception) { e.printStackTrace(); false }
    }
}

/**
 * Creates a new file.
 */
suspend fun createFile(context: Context, parentPath: File?, parentSafUri: Uri?, name: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
             if (parentPath != null) {
                val newFile = File(parentPath, name)
                if (newFile.exists()) false else newFile.createNewFile()
            } else if (parentSafUri != null) {
                val parentDoc = DocumentFile.fromTreeUri(context, parentSafUri)
                parentDoc?.createFile("text/plain", name) != null
            } else false
        } catch (e: Exception) { e.printStackTrace(); false }
    }
}

/**
 * Shares a file.
 */
fun shareFiles(context: Context, files: List<UniversalFile>) {
    if (files.isEmpty()) return
    val uris = ArrayList<Uri>()
    files.forEach { file ->
        val uri = getUriForUniversalFile(context, file)
        if (uri != null) uris.add(uri)
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, "Could not prepare files for sharing", Toast.LENGTH_SHORT).show()
        return
    }
    val shareIntent = Intent().apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size == 1) {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uris[0])
            type = context.contentResolver.getType(uris[0]) ?: "*/*"
        } else {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            type = "*/*"
        }
    }
    try { context.startActivity(Intent.createChooser(shareIntent, "Share via")) }
    catch (e: Exception) { Toast.makeText(context, "No app found to share this file", Toast.LENGTH_SHORT).show() }
}

/**
 * Opens a file using a system-level "Open with" chooser.
 */
fun openWith(context: Context, file: UniversalFile) {
    val uri = getUriForUniversalFile(context, file) ?: return
    
    // Guess MIME type from extension if possible
    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    val mimeType = if (extension != null) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    } else {
        context.contentResolver.getType(uri)
    } ?: "*/*"
    
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        context.startActivity(Intent.createChooser(intent, "Open with..."))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens a file using the default Android app.
 */
fun openFile(context: Context, file: UniversalFile) {
    val uri = getUriForUniversalFile(context, file) ?: return
    
    // Guess MIME type from extension if possible
    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    val mimeType = if (extension != null) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    } else {
        context.contentResolver.getType(uri)
    } ?: "*/*"
    
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: try opening with generic selector if type specific fails
        openWith(context, file)
    }
}

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
