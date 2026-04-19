package com.troikoss.continuum_explorer.providers

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.troikoss.continuum_explorer.model.*
import com.troikoss.continuum_explorer.utils.NetworkProviderException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPSClient
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException

class FtpProvider(
    private val connection: NetworkConnection,
    @Suppress("UNUSED_PARAMETER") appContext: Context,
) : StorageProvider, Closeable {

    override val kind = ProviderKind.NETWORK_FTP
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
    private var client: FTPClient? = null

    private fun ensureConnected(): FTPClient {
        val existing = client
        if (existing != null && existing.isConnected) return existing

        val ftpClient = if (connection.useTls) FTPSClient(true) else FTPClient()
        ftpClient.connectTimeout = 15_000
        ftpClient.defaultTimeout = 60_000
        ftpClient.dataTimeout = java.time.Duration.ofSeconds(60)

        val port = if (connection.port == 0) connection.protocol.defaultPort else connection.port
        try {
            ftpClient.connect(connection.host, port)
        } catch (e: SocketTimeoutException) {
            throw NetworkProviderException("Connection timed out: ${e.message}", NetworkProviderException.Kind.TIMEOUT, e)
        } catch (e: IOException) {
            throw NetworkProviderException("Cannot reach ${connection.host}: ${e.message}", NetworkProviderException.Kind.UNREACHABLE, e)
        }

        val loggedIn = if (connection.username.isNotEmpty()) {
            ftpClient.login(connection.username, connection.password)
        } else {
            ftpClient.login("anonymous", "anonymous@")
        }
        if (!loggedIn) {
            throw NetworkProviderException("Authentication failed", NetworkProviderException.Kind.AUTH)
        }

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
        if (connection.ftpPassiveMode) ftpClient.enterLocalPassiveMode() else ftpClient.enterLocalActiveMode()

        client = ftpClient
        return ftpClient
    }

    override fun rootId(): String = makeId(connection.remotePath.ifEmpty { "/" })

    override fun parentId(childId: String): String? {
        val path = pathOf(childId).trimEnd('/')
        val last = path.lastIndexOf('/')
        if (last < 0) return null
        val parent = if (last == 0) "/" else path.substring(0, last)
        return makeId(parent)
    }

    override fun displayName(id: String): String {
        val path = pathOf(id).trimEnd('/')
        return path.substringAfterLast('/').ifEmpty { connection.displayName }
    }

    override fun exists(id: String): Boolean = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            try {
                val c = ensureConnected()
                val path = pathOf(id)
                c.changeWorkingDirectory(path) || c.listFiles(path).isNotEmpty()
            } catch (_: Exception) { false }
        }
    }

    override suspend fun listChildren(id: String): List<UniversalFile> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val c = ensureConnected()
                val path = pathOf(id)
                c.listFiles(path).filter { it.name != "." && it.name != ".." }.map { it.toUniversalFile(id) }
            } catch (e: NetworkProviderException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw NetworkProviderException("Listing timed out", NetworkProviderException.Kind.TIMEOUT, e)
            } catch (e: Exception) {
                throw NetworkProviderException("List failed: ${e.message}", cause = e)
            }
        }
    }

    override fun getMetadata(id: String): FileMetadata = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val c = ensureConnected()
            val path = pathOf(id)
            val parent = path.substringBeforeLast('/').ifEmpty { "/" }
            val name = path.substringAfterLast('/')
            val file = c.listFiles(parent).firstOrNull { it.name == name }
                ?: throw NetworkProviderException("Not found: $path")
            FileMetadata(
                size = file.size,
                lastModified = file.timestamp?.timeInMillis ?: 0L,
                isDirectory = file.isDirectory,
                mimeType = null,
            )
        }
    }

    override fun findChild(parentId: String, name: String): UniversalFile? = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            try {
                val c = ensureConnected()
                c.listFiles(pathOf(parentId)).firstOrNull { it.name == name }?.toUniversalFile(parentId)
            } catch (_: Exception) { null }
        }
    }

    override fun openInput(id: String): InputStream = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val c = ensureConnected()
            val stream = c.retrieveFileStream(pathOf(id))
                ?: throw NetworkProviderException("Cannot open stream for ${pathOf(id)}")
            FtpManagedInputStream(c, mutex, stream)
        }
    }

    fun openRangeInput(id: String, offset: Long): InputStream = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val c = ensureConnected()
            c.restartOffset = offset
            val stream = c.retrieveFileStream(pathOf(id))
                ?: throw NetworkProviderException("Cannot open range stream for ${pathOf(id)}")
            FtpManagedInputStream(c, mutex, stream)
        }
    }

    override fun openReadFd(id: String): ParcelFileDescriptor? = null
    override fun getShareableUri(id: String): Uri? = null

    override fun createChild(parentId: String, name: String, isDirectory: Boolean): UniversalFile = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val c = ensureConnected()
            val path = joinPath(pathOf(parentId), name)
            if (isDirectory) {
                c.makeDirectory(path)
            } else {
                c.storeFile(path, "".byteInputStream())
            }
            UniversalFile(
                name = name, isDirectory = isDirectory,
                lastModified = System.currentTimeMillis(), length = 0,
                provider = this@FtpProvider, providerId = makeId(path), parentId = parentId
            )
        }
    }

    override fun createAndOpenOutput(parentId: String, name: String): Pair<UniversalFile, OutputStream> {
        val c = runBlocking(Dispatchers.IO) { mutex.runBlocking { ensureConnected() } }
        val path = joinPath(pathOf(parentId), name)
        val stream = c.storeFileStream(path)
            ?: throw NetworkProviderException("Cannot open output stream for $path")
        val uf = UniversalFile(
            name = name, isDirectory = false,
            lastModified = System.currentTimeMillis(), length = 0,
            provider = this, providerId = makeId(path), parentId = parentId
        )
        return uf to FtpManagedOutputStream(c, mutex, stream)
    }

    override suspend fun copyFrom(
        source: UniversalFile,
        destParentId: String,
        destName: String,
        onProgress: (Long) -> Unit,
    ): UniversalFile {
        // 'this' check must happen OUTSIDE withContext — inside a lambda 'this' is the CoroutineScope.
        if (source.provider !== this) {
            return super.copyFrom(source, destParentId, destName, onProgress)
        }
        // Same FTP connection: FTP allows only one data transfer at a time per control connection.
        // Buffer the source entirely before opening the upload stream.
        val bytes = withContext(Dispatchers.IO) {
            mutex.withLock {
                val c = ensureConnected()
                val stream = c.retrieveFileStream(pathOf(source.providerId))
                    ?: throw NetworkProviderException("Cannot read ${source.name}")
                stream.readBytes().also {
                    stream.close()
                    try { c.completePendingCommand() } catch (_: Exception) {}
                }
            }
        }
        val path = joinPath(pathOf(destParentId), destName)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val c = ensureConnected()
                c.storeFile(path, bytes.inputStream())
            }
        }
        onProgress(bytes.size.toLong())
        return UniversalFile(
            name = destName, isDirectory = false,
            lastModified = System.currentTimeMillis(), length = bytes.size.toLong(),
            provider = this, providerId = makeId(path), parentId = destParentId,
        )
    }

    override fun delete(id: String): Boolean = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val c = ensureConnected()
            c.deleteFile(pathOf(id)) || c.removeDirectory(pathOf(id))
        }
    }

    override fun rename(id: String, newName: String): UniversalFile? = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val c = ensureConnected()
            val oldPath = pathOf(id)
            val newPath = joinPath(oldPath.substringBeforeLast('/').ifEmpty { "/" }, newName)
            if (!c.rename(oldPath, newPath)) return@withLock null
            val pid = parentId(id)
            UniversalFile(
                name = newName, isDirectory = false,
                lastModified = System.currentTimeMillis(), length = 0,
                provider = this@FtpProvider, providerId = makeId(newPath), parentId = pid
            )
        }
    }

    override fun close() {
        client?.runCatching { if (isConnected) { logout(); disconnect() } }
        client = null
    }

    fun makeId(path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return "ftp://${connection.id}$p"
    }

    fun pathOf(id: String): String = id.removePrefix("ftp://${connection.id}").ifEmpty { "/" }

    private fun joinPath(parent: String, name: String): String {
        val p = parent.trimEnd('/')
        return "$p/$name"
    }

    private fun FTPFile.toUniversalFile(parentId: String): UniversalFile = UniversalFile(
        name = name,
        isDirectory = isDirectory,
        lastModified = timestamp?.timeInMillis ?: 0L,
        length = size,
        provider = this@FtpProvider,
        providerId = makeId(joinPath(pathOf(parentId), name)),
        parentId = parentId,
    )
}

private suspend fun <T> Mutex.runBlocking(block: suspend () -> T): T = withLock { block() }

class FtpManagedInputStream(
    private val client: FTPClient,
    private val mutex: Mutex,
    private val delegate: InputStream,
) : InputStream() {
    override fun read(): Int = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
    override fun close() {
        try { delegate.close() } catch (_: Exception) {}
        runBlocking(Dispatchers.IO) {
            mutex.withLock {
                try { client.completePendingCommand() } catch (_: Exception) {}
            }
        }
    }
}

class FtpManagedOutputStream(
    private val client: FTPClient,
    private val mutex: Mutex,
    private val delegate: OutputStream,
) : OutputStream() {
    override fun write(b: Int) = delegate.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
    override fun flush() = delegate.flush()
    override fun close() {
        try { delegate.close() } catch (_: Exception) {}
        runBlocking(Dispatchers.IO) {
            mutex.withLock {
                try { client.completePendingCommand() } catch (_: Exception) {}
            }
        }
    }
}
