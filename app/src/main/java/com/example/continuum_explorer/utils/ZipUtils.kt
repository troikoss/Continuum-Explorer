package com.example.continuum_explorer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import com.example.continuum_explorer.PopUpActivity
import com.example.continuum_explorer.model.UniversalFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.ArrayDeque
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters

object ZipUtils {

    private data class RawEntry(val path: String, val isDirectory: Boolean, val time: Long, val size: Long)

    /**
     * Parses the archive ONCE and returns a map of Folder Path -> List of Files in that folder.
     */
    suspend fun parseArchive(context: Context, source: Any): Map<String, List<UniversalFile>> = withContext(Dispatchers.IO) {
        val name = getName(context, source).lowercase()
        val rootId = if (source is File) source.absolutePath else source.toString()
        
        try {
            val rawEntries = when {
                name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".apk") -> {
                    if (source is File) getZipEntriesFile(source)
                    else getZipEntriesUri(context, source as Uri)
                }
                else -> emptyList()
            }
            
            organizeEntries(rawEntries, rootId)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    private fun copyToCache(context: Context, uri: Uri): File? {
        return try {
            val fileName = getUriFileName(context, uri) ?: "temp.zip"
            val cacheFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile
        } catch (e: Exception) {
            null
        }
    }

    fun isArchive(file: UniversalFile): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".zip")
    }

    private fun getName(context: Context, source: Any): String {
        return if (source is File) source.name
        else getUriFileName(context, source as Uri) ?: "unknown.zip"
    }

    private fun getUriFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) result = cursor.getString(index)
                    }
                }
            } catch (e: Exception) { }
        }
        if (result == null) {
            val path = uri.path
            val cut = path?.lastIndexOf('/')
            if (cut != null && cut != -1) result = path.substring(cut + 1)
        }
        return result
    }

    private fun organizeEntries(entries: List<RawEntry>, rootId: String): Map<String, List<UniversalFile>> {
        val map = HashMap<String, MutableList<UniversalFile>>(entries.size / 5 + 1)
        val dirIndex = HashMap<String, UniversalFile>(entries.size / 10 + 1)

        fun ensureDir(dirPath: String) {
            if (dirPath.isEmpty() || dirIndex.containsKey(dirPath)) return
            val trimmed = dirPath.substring(0, dirPath.length - 1)
            val lastSlash = trimmed.lastIndexOf('/')
            val parentPath = if (lastSlash == -1) "" else trimmed.substring(0, lastSlash + 1)
            val name = if (lastSlash == -1) trimmed else trimmed.substring(lastSlash + 1)
            ensureDir(parentPath)
            val dirFile = createVirtualDirectory(name, rootId, dirPath)
            dirIndex[dirPath] = dirFile
            map.getOrPut(parentPath) { mutableListOf() }.add(dirFile)
        }

        for (entry in entries) {
            val fullPath = entry.path.replace('\\', '/') 
            if (entry.isDirectory) {
                val path = if (fullPath.endsWith('/')) fullPath else "$fullPath/"
                ensureDir(path)
            } else {
                val lastSlash = fullPath.lastIndexOf('/')
                val parentPath = if (lastSlash == -1) "" else fullPath.substring(0, lastSlash + 1)
                val name = if (lastSlash == -1) fullPath else fullPath.substring(lastSlash + 1)
                ensureDir(parentPath)
                val file = createUniversalFile(name, false, entry.time, entry.size, rootId, fullPath)
                map.getOrPut(parentPath) { mutableListOf() }.add(file)
            }
        }
        return map
    }

    private fun showPasswordPrompt(context: Context) {
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun getZipEntriesFile(file: File): List<RawEntry> {
        val list = ArrayList<RawEntry>(1000)
        try {
            // java.util.zip.ZipFile is heavily optimized and native-backed on Android
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    list.add(RawEntry(e.name, e.isDirectory, e.time, e.size.coerceAtLeast(0)))
                }
            }
        } catch (e: Exception) {
            // Fallback to Zip4j, which supports reading headers of AES encrypted zips
            try {
                val zip = ZipFile(file)
                for (h in zip.fileHeaders) {
                    val time = h.lastModifiedTimeEpoch * 1000L
                    list.add(RawEntry(h.fileName, h.isDirectory, time, h.uncompressedSize.coerceAtLeast(0)))
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
        return list
    }

    private suspend fun getZipEntriesUri(context: Context, uri: Uri): List<RawEntry> {
        val list = ArrayList<RawEntry>(1000)
        var pfd: ParcelFileDescriptor? = null
        try {
            // First attempt: If it's backed by a real file or provider supports /proc/self/fd
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                try {
                    val fdPath = "/proc/self/fd/${pfd.fd}"
                    val fdFile = File(fdPath)
                    if (fdFile.exists() && fdFile.canRead()) {
                        try {
                            java.util.zip.ZipFile(fdFile).use { zip ->
                                val entries = zip.entries()
                                while (entries.hasMoreElements()) {
                                    val e = entries.nextElement()
                                    list.add(RawEntry(e.name, e.isDirectory, e.time, e.size.coerceAtLeast(0)))
                                }
                            }
                        } catch (e: Exception) {
                            val zip = ZipFile(fdFile)
                            for (h in zip.fileHeaders) {
                                val time = h.lastModifiedTimeEpoch * 1000L
                                list.add(RawEntry(h.fileName, h.isDirectory, time, h.uncompressedSize.coerceAtLeast(0)))
                            }
                        }
                        return list
                    }
                } catch (e: Exception) {
                    // Fallback to streaming below
                }
            }
            
            // Fallback: Use ZipInputStream (avoids copying large files to flash storage)
            context.contentResolver.openInputStream(uri)?.use { input ->
                try {
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            list.add(RawEntry(entry.name, entry.isDirectory, entry.time, entry.size.coerceAtLeast(0)))
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    // If streaming fails (e.g. encrypted), we must copy to cache
                }
            }
            
            if (list.isEmpty()) {
                val cacheFile = copyToCache(context, uri)
                if (cacheFile != null && cacheFile.exists()) {
                    try {
                        val zip = ZipFile(cacheFile)
                        for (h in zip.fileHeaders) {
                            val time = h.lastModifiedTimeEpoch * 1000L
                            list.add(RawEntry(h.fileName, h.isDirectory, time, h.uncompressedSize.coerceAtLeast(0)))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        cacheFile.delete()
                    }
                }
            }

        } catch (e: Exception) { e.printStackTrace() }
        finally {
            try { pfd?.close() } catch (e: Exception) {}
        }
        return list
    }

    private fun createUniversalFile(displayName: String, isDirectory: Boolean, time: Long, size: Long, rootId: String, fullEntryPath: String): UniversalFile {
        return UniversalFile(
            name = displayName,
            isDirectory = isDirectory,
            lastModified = time,
            length = size,
            fileRef = null,
            absolutePath = "$rootId::$fullEntryPath",
            isArchiveEntry = true,
            archivePath = fullEntryPath
        )
    }


    private fun createVirtualDirectory(name: String, rootId: String, fullPath: String): UniversalFile {
        return UniversalFile(
            name = name,
            isDirectory = true,
            lastModified = 0,
            length = 0,
            fileRef = null,
            absolutePath = "$rootId::$fullPath",
            isArchiveEntry = true,
            archivePath = fullPath
        )
    }

    /**
     * Obtains an InputStream for a single entry within an archive.
     */
    suspend fun getArchiveInputStream(context: Context, archiveSource: Any, entryPath: String): InputStream? = withContext(Dispatchers.IO) {
        try {
            val zipFile = when (archiveSource) {
                is File -> ZipFile(archiveSource)
                is Uri -> {
                    // For Uris, we unfortunately have to copy to cache to use Zip4j properly
                    // because Zip4j requires a File or RandomAccessFile.
                    val cacheFile = copyToCache(context, archiveSource)
                    if (cacheFile != null) ZipFile(cacheFile) else return@withContext null
                }
                else -> return@withContext null
            }

            if (zipFile.isEncrypted) {
                val password = FileOperationsManager.requestPassword(getName(context, archiveSource))
                if (password != null) zipFile.setPassword(password.toCharArray())
                else return@withContext null
            }

            val header = zipFile.getFileHeader(entryPath)
            if (header != null) {
                // If it's a Uri-based archive, the InputStream will be tied to the cache file.
                // We don't delete the cache file here; it's a limitation of this simplified approach.
                zipFile.getInputStream(header)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lists the children of a "virtual directory" inside an archive.
     */
    suspend fun getArchiveChildren(context: Context, archiveSource: Any, parentArchivePath: String): List<UniversalFile> {
        val structure = parseArchive(context, archiveSource)
        return structure[parentArchivePath] ?: emptyList()
    }

    suspend fun extractArchive(context: Context, archive: UniversalFile, destDir: File) = withContext(Dispatchers.IO) {
        extractArchives(context, listOf(archive), destDir, false)
    }

    suspend fun extractArchives(
        context: Context,
        archives: List<UniversalFile>,
        baseDestDir: File,
        toSeparateFolders: Boolean
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            FileOperationsManager.start()
            FileOperationsManager.statusMessage.value = if (archives.size == 1) "Extracting ${archives[0].name}..." else "Extracting ${archives.size} archives..."
        }

        try {
            var totalBytes = 0L
            var totalItems = 0
            val archivesToProcess = mutableListOf<Pair<File, File>>()

            for (archive in archives) {
                val file = archive.fileRef ?: continue
                val name = file.name.lowercase()
                if (name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".apk")) {
                    val zip4jFile = net.lingala.zip4j.ZipFile(file)
                    val entries = zip4jFile.fileHeaders
                    totalItems += entries.size
                    totalBytes += entries.sumOf { if (it.isDirectory) 0L else it.uncompressedSize }
                    
                    val destDir = if (toSeparateFolders) {
                        val folderName = archive.name.substringBeforeLast('.')
                        val dir = File(baseDestDir, folderName)
                        dir.mkdirs()
                        dir
                    } else {
                        baseDestDir
                    }
                    archivesToProcess.add(file to destDir)
                }
            }

            withContext(Dispatchers.Main) {
                FileOperationsManager.totalSize.longValue = totalBytes
                FileOperationsManager.itemsTotal.intValue = totalItems
            }

            var globalBytesProcessed = 0L
            var globalItemsProcessed = 0
            val buffer = ByteArray(32 * 1024)
            var lastUpdateTime = System.currentTimeMillis()
            val speedWindow = ArrayDeque<Pair<Long, Long>>()
            speedWindow.add(lastUpdateTime to 0L)
            val windowMs = 3000L

            for ((zipFile, destDir) in archivesToProcess) {
                if (FileOperationsManager.isCancelled.value) break
                
                val zip4jFile = net.lingala.zip4j.ZipFile(zipFile)
                if (zip4jFile.isEncrypted) {
                    withContext(Dispatchers.Main) { showPasswordPrompt(context) }
                    val password = FileOperationsManager.requestPassword(zipFile.name)
                    if (password == null) {
                        // Skip this archive or handle cancel
                        continue
                    }
                    zip4jFile.setPassword(password.toCharArray())
                }

                for (entry in zip4jFile.fileHeaders) {
                    if (FileOperationsManager.isCancelled.value) break
                    val outFile = File(destDir, entry.fileName.replace('\\', '/'))
                    
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        try {
                            zip4jFile.getInputStream(entry).use { input ->
                                FileOutputStream(outFile).use { output ->
                                    var bytesRead = input.read(buffer)
                                    while (bytesRead >= 0) {
                                        if (FileOperationsManager.isCancelled.value) break
                                        output.write(buffer, 0, bytesRead)
                                        globalBytesProcessed += bytesRead
                                        
                                        val now = System.currentTimeMillis()
                                        if (now - lastUpdateTime >= 300) {
                                            speedWindow.add(now to globalBytesProcessed)
                                            while (speedWindow.size > 2 && now - speedWindow.first.first > windowMs) {
                                                speedWindow.removeFirst()
                                            }
                                            val startPoint = speedWindow.first
                                            val timeDiff = now - startPoint.first
                                            val bytesDiff = globalBytesProcessed - startPoint.second
                                            val currentSpeed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0L
                                            val remainingBytes = totalBytes - globalBytesProcessed
                                            val timeRemaining = if (currentSpeed > 0) (remainingBytes * 1000) / currentSpeed else 0L
                                            
                                            withContext(Dispatchers.Main) {
                                                FileOperationsManager.updateDetailed(
                                                    processedBytes = globalBytesProcessed,
                                                    totalBytes = totalBytes,
                                                    speed = currentSpeed,
                                                    remainingMillis = timeRemaining,
                                                    fileName = entry.fileName
                                                )
                                            }
                                            lastUpdateTime = now
                                        }
                                        bytesRead = input.read(buffer)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    globalItemsProcessed++
                    withContext(Dispatchers.Main) {
                        FileOperationsManager.itemsProcessed.intValue = globalItemsProcessed
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            withContext(Dispatchers.Main) {
                FileOperationsManager.finish()
            }
        }
    }


    suspend fun compressFiles(
        context: Context,
        filesToCompress: List<UniversalFile>,
        destFolder: File,
        settings: ArchiveSettings
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            FileOperationsManager.start()
            FileOperationsManager.statusMessage.value = "Compressing ${settings.archiveName}..."
        }
        
        try {
            val destFile = File(destFolder, settings.archiveName)
            val zipParameters = ZipParameters()
            zipParameters.compressionMethod = settings.compressionMethod
            zipParameters.compressionLevel = settings.compressionLevel
            if (settings.encryptionMethod != net.lingala.zip4j.model.enums.EncryptionMethod.NONE) {
                zipParameters.isEncryptFiles = true
                zipParameters.encryptionMethod = settings.encryptionMethod
            }

            val zipFile = if (settings.password != null) {
                ZipFile(destFile, settings.password.toCharArray())
            } else {
                ZipFile(destFile)
            }
            
            val filesToAdd = mutableListOf<File>()
            val foldersToAdd = mutableListOf<File>()
            
            for (uFile in filesToCompress) {
                val f = uFile.fileRef
                if (f != null && f.exists()) {
                    if (f.isDirectory) {
                        foldersToAdd.add(f)
                    } else {
                        filesToAdd.add(f)
                    }
                }
            }
            
            zipFile.isRunInThread = true
            val progressMonitor = zipFile.progressMonitor
            
            if (filesToAdd.isNotEmpty() && !FileOperationsManager.isCancelled.value) {
                zipFile.addFiles(filesToAdd, zipParameters)
                monitorProgress(progressMonitor)
            }
            
            for (folder in foldersToAdd) {
                if (FileOperationsManager.isCancelled.value) break
                zipFile.addFolder(folder, zipParameters)
                monitorProgress(progressMonitor)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Compression failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            withContext(Dispatchers.Main) {
                FileOperationsManager.finish()
            }
        }
    }

    private suspend fun monitorProgress(progressMonitor: net.lingala.zip4j.progress.ProgressMonitor) {
        var lastUpdateTime = System.currentTimeMillis()
        val speedWindow = ArrayDeque<Pair<Long, Long>>()
        speedWindow.add(lastUpdateTime to 0L)
        val windowMs = 3000L

        while (progressMonitor.state == net.lingala.zip4j.progress.ProgressMonitor.State.BUSY) {
            if (FileOperationsManager.isCancelled.value) {
                progressMonitor.isCancelAllTasks = true
                break
            }
            
            val now = System.currentTimeMillis()
            if (now - lastUpdateTime >= 300) {
                val processed = progressMonitor.workCompleted
                val total = progressMonitor.totalWork
                
                speedWindow.add(now to processed)
                while (speedWindow.size > 2 && now - speedWindow.first.first > windowMs) {
                    speedWindow.removeFirst()
                }
                val startPoint = speedWindow.first
                val timeDiff = now - startPoint.first
                val bytesDiff = processed - startPoint.second
                val currentSpeed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0L
                val remainingBytes = total - processed
                val timeRemaining = if (currentSpeed > 0) (remainingBytes * 1000) / currentSpeed else 0L

                withContext(Dispatchers.Main) {
                    FileOperationsManager.updateDetailed(
                        processedBytes = processed,
                        totalBytes = total,
                        speed = currentSpeed,
                        remainingMillis = timeRemaining,
                        fileName = progressMonitor.fileName ?: "File"
                    )
                }
                lastUpdateTime = now
            }
            
            kotlinx.coroutines.delay(100)
        }
        
        // Final check to catch exceptions
        if (progressMonitor.result == net.lingala.zip4j.progress.ProgressMonitor.Result.ERROR) {
            throw progressMonitor.exception ?: Exception("Unknown compression error")
        }
    }
}
