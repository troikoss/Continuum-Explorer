package com.troikoss.continuum_explorer.model

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.io.OutputStream

enum class ProviderKind {
    LOCAL, SAF, ARCHIVE,
    NETWORK_FTP, NETWORK_SFTP, NETWORK_WEBDAV, NETWORK_SMB
}

data class ProviderCapabilities(
    val canWrite: Boolean,
    val canRename: Boolean,
    val canDelete: Boolean,
    val canRandomAccessRead: Boolean,
    val canStreamRead: Boolean = true,
    val canGetShareableUri: Boolean,
    val supportsChildEnumeration: Boolean,
    val isRemote: Boolean,
)

data class FileMetadata(
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val mimeType: String?,
)

interface StorageProvider {
    val kind: ProviderKind
    val capabilities: ProviderCapabilities
    val connectionId: String get() = ""

    fun rootId(): String
    fun parentId(childId: String): String?
    fun displayName(id: String): String
    fun exists(id: String): Boolean

    suspend fun listChildren(id: String): List<UniversalFile>
    fun getMetadata(id: String): FileMetadata
    fun findChild(parentId: String, name: String): UniversalFile?

    fun openInput(id: String): InputStream
    fun openReadFd(id: String): ParcelFileDescriptor?
    fun getShareableUri(id: String): Uri?

    fun createChild(parentId: String, name: String, isDirectory: Boolean): UniversalFile
    fun createAndOpenOutput(parentId: String, name: String): Pair<UniversalFile, OutputStream>
    fun delete(id: String): Boolean
    fun rename(id: String, newName: String): UniversalFile?

    fun getDiskInfo(): Pair<Long, Long>? = null  // Pair(totalBytes, freeBytes)

    suspend fun copyFrom(
        source: UniversalFile,
        destParentId: String,
        destName: String,
        onProgress: (Long) -> Unit
    ): UniversalFile {
        val (dest, out) = createAndOpenOutput(destParentId, destName)
        out.use { output ->
            source.provider.openInput(source.providerId).use { input ->
                val buf = ByteArray(32 * 1024)
                var read = input.read(buf)
                var total = 0L
                while (read >= 0) {
                    output.write(buf, 0, read)
                    total += read
                    onProgress(total)
                    read = input.read(buf)
                }
            }
        }
        return dest
    }
}
