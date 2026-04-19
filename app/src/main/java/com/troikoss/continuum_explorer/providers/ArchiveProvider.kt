package com.troikoss.continuum_explorer.providers

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.troikoss.continuum_explorer.model.FileMetadata
import com.troikoss.continuum_explorer.model.ProviderCapabilities
import com.troikoss.continuum_explorer.model.ProviderKind
import com.troikoss.continuum_explorer.model.StorageProvider
import com.troikoss.continuum_explorer.model.UniversalFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Read-only provider for browsing entries inside a ZIP archive.
 *
 * The `id` format is: `<archive-source-id>::<entry-path>`
 * where `<archive-source-id>` is the absolute path (local) or URI string (SAF),
 * and `<entry-path>` is the path of the entry inside the archive (empty string = root).
 */
class ArchiveProvider(
    val archiveSource: Any, // File or Uri
    private val appContext: Context
) : StorageProvider {

    override val kind = ProviderKind.ARCHIVE
    override val capabilities = ProviderCapabilities(
        canWrite = false,
        canRename = false,
        canDelete = false,
        canRandomAccessRead = false,
        canGetShareableUri = false,
        supportsChildEnumeration = true,
        isRemote = false,
    )

    val sourceId: String = when (archiveSource) {
        is File -> archiveSource.absolutePath
        is Uri -> archiveSource.toString()
        else -> archiveSource.toString()
    }

    fun makeId(entryPath: String): String = "$sourceId::$entryPath"

    fun entryPath(id: String): String {
        val sep = id.indexOf("::")
        return if (sep >= 0) id.substring(sep + 2) else ""
    }

    override fun rootId() = makeId("")

    override fun parentId(childId: String): String? {
        val path = entryPath(childId).removeSuffix("/")
        if (path.isEmpty()) return null
        val lastSlash = path.lastIndexOf('/')
        return makeId(if (lastSlash < 0) "" else path.substring(0, lastSlash + 1))
    }

    override fun displayName(id: String): String {
        val path = entryPath(id).removeSuffix("/")
        return if (path.isEmpty()) when (archiveSource) {
            is File -> archiveSource.name
            else -> "Archive"
        } else {
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash < 0) path else path.substring(lastSlash + 1)
        }
    }

    override fun exists(id: String) = true

    override suspend fun listChildren(id: String): List<UniversalFile> {
        val ep = entryPath(id)
        val cache = com.troikoss.continuum_explorer.utils.ZipUtils.parseArchive(appContext, archiveSource)
        return cache[ep] ?: emptyList()
    }

    override fun getMetadata(id: String): FileMetadata {
        return FileMetadata(0L, 0L, entryPath(id).endsWith("/"), null)
    }

    override fun findChild(parentId: String, name: String): UniversalFile? = null

    override fun openInput(id: String): InputStream {
        val ep = entryPath(id)
        return kotlinx.coroutines.runBlocking {
            com.troikoss.continuum_explorer.utils.ZipUtils.getArchiveInputStream(appContext, archiveSource, ep)
        } ?: throw IllegalStateException("Entry not found: $ep")
    }

    override fun openReadFd(id: String): ParcelFileDescriptor? = null

    override fun getShareableUri(id: String): Uri? = Uri.parse("archive://${entryPath(id)}")

    override fun createChild(parentId: String, name: String, isDirectory: Boolean): UniversalFile =
        throw UnsupportedOperationException("Archive is read-only")

    override fun createAndOpenOutput(parentId: String, name: String): Pair<UniversalFile, OutputStream> =
        throw UnsupportedOperationException("Archive is read-only")

    override fun delete(id: String): Boolean = false

    override fun rename(id: String, newName: String): UniversalFile? = null

    fun createEntryFile(name: String, isDir: Boolean, time: Long, size: Long, entryFullPath: String): UniversalFile {
        return UniversalFile(
            name = name,
            isDirectory = isDir,
            lastModified = time,
            length = size,
            provider = this,
            providerId = makeId(entryFullPath),
            parentId = parentId(makeId(entryFullPath)),
        )
    }
}
