package com.troikoss.continuum_explorer.providers

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.troikoss.continuum_explorer.model.*
import com.troikoss.continuum_explorer.utils.NetworkProviderException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.util.EnumSet

class SmbProvider(
    private val connection: NetworkConnection,
    @Suppress("UNUSED_PARAMETER") appContext: Context,
) : StorageProvider, Closeable {

    override val kind = ProviderKind.NETWORK_SMB
    override val connectionId: String get() = connection.id
    override val capabilities = ProviderCapabilities(
        canWrite = true,
        canRename = true,
        canDelete = true,
        canRandomAccessRead = false,
        canStreamRead = true,
        canGetShareableUri = false,
        supportsChildEnumeration = true,
        isRemote = true,
    )

    private val mutex = Mutex()
    private var smbClient: SMBClient? = null
    private var diskShare: DiskShare? = null

    // remotePath format: "/shareName" or "/shareName/subfolder"
    // extractShare returns the first path segment as the share name
    private fun extractShare(remotePath: String): String {
        val trimmed = remotePath.trim('/')
        return trimmed.substringBefore('/')
    }

    // extractShareRoot returns the part of remotePath after the share name (the sub-folder root)
    private fun extractShareRoot(remotePath: String): String {
        val trimmed = remotePath.trim('/')
        val slashIdx = trimmed.indexOf('/')
        return if (slashIdx < 0) "" else trimmed.substring(slashIdx + 1)
    }

    private val shareName: String by lazy { extractShare(connection.remotePath) }
    private val shareRoot: String by lazy { extractShareRoot(connection.remotePath) }

    private fun ensureShare(): DiskShare {
        val existing = diskShare
        if (existing != null && existing.isConnected) return existing

        smbClient?.runCatching { close() }
        smbClient = null
        diskShare = null

        val client = SMBClient()
        smbClient = client

        val port = if (connection.port == 0) connection.protocol.defaultPort else connection.port
        val conn = try {
            client.connect(connection.host, port)
        } catch (e: ConnectException) {
            throw NetworkProviderException("Cannot reach ${connection.host}: ${e.message}", NetworkProviderException.Kind.UNREACHABLE, e)
        } catch (e: IOException) {
            throw NetworkProviderException("Cannot reach ${connection.host}: ${e.message}", NetworkProviderException.Kind.UNREACHABLE, e)
        }

        val domain = connection.smbDomain.ifEmpty { "WORKGROUP" }
        val authCtx = if (connection.username.isNotEmpty()) {
            AuthenticationContext(connection.username, connection.password.toCharArray(), domain)
        } else {
            AuthenticationContext.anonymous()
        }

        val session = try {
            conn.authenticate(authCtx)
        } catch (e: Exception) {
            throw NetworkProviderException("Authentication failed: ${e.message}", NetworkProviderException.Kind.AUTH, e)
        }

        val share = try {
            session.connectShare(shareName) as DiskShare
        } catch (e: Exception) {
            throw NetworkProviderException("Cannot open share '$shareName': ${e.message}", NetworkProviderException.Kind.SERVER_ERROR, e)
        }

        diskShare = share
        return share
    }

    // ID format: smb://{connectionId}/{sharePath}
    // sharePath is relative to the share root; empty string = root
    private fun makeId(sharePath: String): String {
        val normalized = sharePath.trim('/')
        return if (normalized.isEmpty()) "smb://${connection.id}/" else "smb://${connection.id}/$normalized"
    }

    private fun idToPath(id: String): String {
        val prefix = "smb://${connection.id}/"
        val afterPrefix = if (id.startsWith(prefix)) id.removePrefix(prefix) else id.trim('/')
        return if (shareRoot.isEmpty()) afterPrefix else if (afterPrefix.isEmpty()) shareRoot else "$shareRoot/$afterPrefix"
    }

    private fun pathToId(smbPath: String): String {
        val rel = if (shareRoot.isNotEmpty() && smbPath.startsWith(shareRoot)) {
            smbPath.removePrefix(shareRoot).trim('/')
        } else {
            smbPath.trim('/')
        }
        return makeId(rel)
    }

    override fun rootId(): String = makeId("")

    override fun parentId(childId: String): String? {
        val prefix = "smb://${connection.id}/"
        val rel = childId.removePrefix(prefix).trim('/')
        if (rel.isEmpty()) return null
        val parent = rel.substringBeforeLast('/', "")
        return makeId(parent)
    }

    override fun displayName(id: String): String {
        val prefix = "smb://${connection.id}/"
        val rel = id.removePrefix(prefix).trim('/')
        return if (rel.isEmpty()) connection.displayName else rel.substringAfterLast('/')
    }

    override fun exists(id: String): Boolean {
        return try {
            val share = ensureShare()
            val path = idToPath(id).replace('/', '\\')
            share.fileExists(path) || share.folderExists(path)
        } catch (_: Exception) { false }
    }

    override suspend fun listChildren(id: String): List<UniversalFile> = mutex.withLock {
        val share = ensureShare()
        val dirPath = idToPath(id).replace('/', '\\')
        val searchPath = if (dirPath.isEmpty()) "*" else "$dirPath\\*"

        return share.list(if (dirPath.isEmpty()) "" else dirPath).mapNotNull { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@mapNotNull null
            val isDir = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            val childSmbPath = if (dirPath.isEmpty()) name else "$dirPath\\$name"
            val childPath = childSmbPath.replace('\\', '/')
            UniversalFile(
                name = name,
                isDirectory = isDir,
                lastModified = info.lastWriteTime.toEpochMillis(),
                length = if (isDir) 0L else info.endOfFile,
                provider = this,
                providerId = pathToId(childPath),
                parentId = id,
            )
        }
    }

    override fun getMetadata(id: String): FileMetadata {
        val share = ensureShare()
        val path = idToPath(id).replace('/', '\\')
        return try {
            val info = share.getFileInformation(path)
            FileMetadata(
                size = info.standardInformation.endOfFile,
                lastModified = info.basicInformation.lastWriteTime.toEpochMillis(),
                isDirectory = info.basicInformation.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L,
                mimeType = null,
            )
        } catch (e: Exception) {
            throw NetworkProviderException("Cannot stat $path: ${e.message}", NetworkProviderException.Kind.SERVER_ERROR, e)
        }
    }

    override fun findChild(parentId: String, name: String): UniversalFile? {
        return try {
            val share = ensureShare()
            val parentPath = idToPath(parentId).replace('/', '\\')
            val childPath = if (parentPath.isEmpty()) name else "$parentPath\\$name"
            if (!share.fileExists(childPath) && !share.folderExists(childPath)) return null
            val info = share.getFileInformation(childPath)
            val isDir = info.basicInformation.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            UniversalFile(
                name = name,
                isDirectory = isDir,
                lastModified = info.basicInformation.lastWriteTime.toEpochMillis(),
                length = if (isDir) 0L else info.standardInformation.endOfFile,
                provider = this,
                providerId = pathToId(childPath.replace('\\', '/')),
                parentId = parentId,
            )
        } catch (_: Exception) { null }
    }

    override fun openInput(id: String): InputStream {
        val share = ensureShare()
        val path = idToPath(id).replace('/', '\\')
        val file = share.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        val delegate = file.inputStream
        return object : InputStream() {
            override fun read() = delegate.read()
            override fun read(b: ByteArray, off: Int, len: Int) = delegate.read(b, off, len)
            override fun close() { delegate.close(); file.close() }
        }
    }

    override fun openReadFd(id: String): ParcelFileDescriptor? = null

    override fun getShareableUri(id: String): Uri? = null

    override fun createChild(parentId: String, name: String, isDirectory: Boolean): UniversalFile {
        val share = ensureShare()
        val parentPath = idToPath(parentId).replace('/', '\\')
        val childPath = if (parentPath.isEmpty()) name else "$parentPath\\$name"
        if (isDirectory) {
            share.mkdir(childPath)
        } else {
            share.openFile(
                childPath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_CREATE,
                null
            ).use {}
        }
        return UniversalFile(
            name = name,
            isDirectory = isDirectory,
            lastModified = System.currentTimeMillis(),
            length = 0L,
            provider = this,
            providerId = pathToId(childPath.replace('\\', '/')),
            parentId = parentId,
        )
    }

    override fun createAndOpenOutput(parentId: String, name: String): Pair<UniversalFile, OutputStream> {
        val share = ensureShare()
        val parentPath = idToPath(parentId).replace('/', '\\')
        val childPath = if (parentPath.isEmpty()) name else "$parentPath\\$name"
        val file = share.openFile(
            childPath,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        val universalFile = UniversalFile(
            name = name,
            isDirectory = false,
            lastModified = System.currentTimeMillis(),
            length = 0L,
            provider = this,
            providerId = pathToId(childPath.replace('\\', '/')),
            parentId = parentId,
        )
        val delegate = file.outputStream
        val stream = object : OutputStream() {
            override fun write(b: Int) = delegate.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
            override fun flush() = delegate.flush()
            override fun close() { delegate.close(); file.close() }
        }
        return Pair(universalFile, stream)
    }

    override fun delete(id: String): Boolean {
        return try {
            val share = ensureShare()
            val path = idToPath(id).replace('/', '\\')
            if (share.folderExists(path)) {
                share.rmdir(path, false)
            } else {
                share.rm(path)
            }
            true
        } catch (_: Exception) { false }
    }

    override fun rename(id: String, newName: String): UniversalFile? {
        return try {
            val share = ensureShare()
            val path = idToPath(id).replace('/', '\\')
            val parentSmbPath = path.substringBeforeLast('\\', "")
            val newPath = if (parentSmbPath.isEmpty()) newName else "$parentSmbPath\\$newName"
            val isDir = share.folderExists(path)
            val accessMask = if (isDir) {
                EnumSet.of(AccessMask.GENERIC_ALL)
            } else {
                EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE, AccessMask.DELETE)
            }
            val createOpts = if (isDir) EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE) else null
            share.openFile(
                path,
                accessMask,
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                SMB2CreateDisposition.FILE_OPEN,
                createOpts
            ).use { it.rename(newPath) }
            val parentId = parentId(id) ?: rootId()
            UniversalFile(
                name = newName,
                isDirectory = isDir,
                lastModified = System.currentTimeMillis(),
                length = 0L,
                provider = this,
                providerId = pathToId(newPath.replace('\\', '/')),
                parentId = parentId,
            )
        } catch (e: Exception) {
            throw NetworkProviderException("Rename failed: ${e.message}", NetworkProviderException.Kind.SERVER_ERROR, e)
        }
    }

    override fun getDiskInfo(): Pair<Long, Long>? {
        return try {
            val share = ensureShare()
            val info = share.shareInformation
            Pair(info.totalSpace, info.freeSpace)
        } catch (_: Exception) { null }
    }

    override fun close() {
        diskShare?.runCatching { close() }
        diskShare = null
        smbClient?.runCatching { close() }
        smbClient = null
    }
}
