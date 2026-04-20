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
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPConnectionClosedException
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class FtpProvider(
    private val connection: NetworkConnection,
    @Suppress("UNUSED_PARAMETER") appContext: Context,
) : StorageProvider, Closeable {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val CONTROL_TIMEOUT_MS = 120_000
        private const val DATA_TIMEOUT_SECONDS = 120L
        private const val CONTROL_KEEP_ALIVE_SECONDS = 15L
        private const val CONTROL_KEEP_ALIVE_REPLY_TIMEOUT_MS = 5_000
    }

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
        if (existing != null && existing.isConnected) {
            try {
                if (existing.sendNoOp()) return existing
            } catch (_: Exception) {}
            existing.runCatching { logout(); disconnect() }
            client = null
        }

        val ftpClient = if (connection.useTls) FTPSClient(true) else FTPClient()

        // Force UNIX parser to avoid "unknown parser type: ok" errors on non-compliant servers
        ftpClient.configure(FTPClientConfig(FTPClientConfig.SYST_UNIX))

        ftpClient.connectTimeout = CONNECT_TIMEOUT_MS
        ftpClient.defaultTimeout = CONTROL_TIMEOUT_MS
        ftpClient.dataTimeout = java.time.Duration.ofSeconds(DATA_TIMEOUT_SECONDS)
        // Keep control connection alive while data socket is busy on long transfers.
        @Suppress("DEPRECATION")
        ftpClient.setControlKeepAliveTimeout(CONTROL_KEEP_ALIVE_SECONDS)
        @Suppress("DEPRECATION")
        ftpClient.setControlKeepAliveReplyTimeout(CONTROL_KEEP_ALIVE_REPLY_TIMEOUT_MS)

        val port = if (connection.port == 0) connection.protocol.defaultPort else connection.port
        try {
            ftpClient.connect(connection.host, port)
        } catch (e: SocketTimeoutException) {
            throw NetworkProviderException("Connection timed out: ${e.message}", NetworkProviderException.Kind.TIMEOUT, e)
        } catch (e: IOException) {
            throw NetworkProviderException("Cannot reach ${connection.host}: ${e.message}", NetworkProviderException.Kind.UNREACHABLE, e)
        }
        if (!FTPReply.isPositiveCompletion(ftpClient.replyCode)) {
            ftpClient.runCatching { disconnect() }
            throw NetworkProviderException(
                "FTP server rejected connection (reply ${ftpClient.replyCode})",
                NetworkProviderException.Kind.UNREACHABLE,
            )
        }

        val loggedIn = if (connection.username.isNotEmpty()) {
            ftpClient.login(connection.username, connection.password)
        } else {
            ftpClient.login("anonymous", "anonymous@")
        }
        if (!loggedIn) {
            ftpClient.runCatching { disconnect() }
            throw NetworkProviderException("Authentication failed", NetworkProviderException.Kind.AUTH)
        }

        if (ftpClient is FTPSClient) {
            ftpClient.runCatching {
                execPBSZ(0)
                execPROT("P")
            }
        }

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
        if (connection.ftpPassiveMode) {
            ftpClient.enterLocalPassiveMode()
            @Suppress("DEPRECATION")
            ftpClient.setPassiveNatWorkaround(true)
        } else {
            ftpClient.enterLocalActiveMode()
        }

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

    private fun forceReconnect() {
        client?.runCatching { abort(); logout(); disconnect() }
        client = null
    }

    private fun isRecoverableConnectionError(error: Throwable): Boolean {
        return error is SocketTimeoutException ||
            error is FTPConnectionClosedException ||
            error is IOException
    }

    private fun <T> withDataConnection(block: (FTPClient) -> T): T {
        return try {
            block(ensureConnected())
        } catch (e: Exception) {
            if (!isRecoverableConnectionError(e)) throw e
            forceReconnect()
            block(ensureConnected())
        }
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
                val path = pathOf(id)
                withDataConnection { c -> c.listFiles(path).filter { it.name != "." && it.name != ".." }.map { it.toUniversalFile(id) } }
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
            val path = pathOf(id)
            val parent = path.substringBeforeLast('/').ifEmpty { "/" }
            val name = path.substringAfterLast('/')
            val file = withDataConnection { c -> c.listFiles(parent).firstOrNull { it.name == name } }
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
                withDataConnection { c -> c.listFiles(pathOf(parentId)).firstOrNull { it.name == name }?.toUniversalFile(parentId) }
            } catch (_: Exception) { null }
        }
    }

    override fun openInput(id: String): InputStream = runBlocking(Dispatchers.IO) {
        mutex.lock()
        try {
            val stream = withDataConnection { c ->
                c.retrieveFileStream(pathOf(id)) ?: throw NetworkProviderException("Cannot open stream for ${pathOf(id)}")
            }
            FtpManagedInputStream(client!!, mutex, stream, lockHeld = true)
        } catch (e: Exception) {
            mutex.unlock()
            throw e
        }
    }

    fun openRangeInput(id: String, offset: Long): InputStream = runBlocking(Dispatchers.IO) {
        mutex.lock()
        try {
            val stream = withDataConnection { c ->
                c.restartOffset = offset
                c.retrieveFileStream(pathOf(id)) ?: throw NetworkProviderException("Cannot open range stream for ${pathOf(id)}")
            }
            FtpManagedInputStream(client!!, mutex, stream, lockHeld = true)
        } catch (e: Exception) {
            mutex.unlock()
            throw e
        }
    }

    override fun openReadFd(id: String): ParcelFileDescriptor? = null
    override fun getShareableUri(id: String): Uri? = null

    override fun createChild(parentId: String, name: String, isDirectory: Boolean): UniversalFile = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val path = joinPath(pathOf(parentId), name)
            withDataConnection { c ->
                if (isDirectory) c.makeDirectory(path) else c.storeFile(path, "".byteInputStream())
            }
            UniversalFile(
                name = name, isDirectory = isDirectory,
                lastModified = System.currentTimeMillis(), length = 0,
                provider = this@FtpProvider, providerId = makeId(path), parentId = parentId
            )
        }
    }

    override fun createAndOpenOutput(parentId: String, name: String): Pair<UniversalFile, OutputStream> {
        val path = joinPath(pathOf(parentId), name)
        val stream = runBlocking(Dispatchers.IO) {
            mutex.lock()
            try {
                withDataConnection { c -> c.storeFileStream(path) }
            } catch (e: Exception) {
                mutex.unlock()
                throw e
            }
        } ?: throw NetworkProviderException("Cannot open output stream for $path")
        val uf = UniversalFile(
            name = name, isDirectory = false,
            lastModified = System.currentTimeMillis(), length = 0,
            provider = this, providerId = makeId(path), parentId = parentId
        )
        return uf to FtpManagedOutputStream(client!!, mutex, stream, lockHeld = true)
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
                    try {
                        if (!c.completePendingCommand()) {
                            c.disconnect()
                        }
                    } catch (_: Exception) {
                        try { c.disconnect() } catch (_: Exception) {}
                    }
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

class FtpManagedInputStream(
    private val client: FTPClient,
    private val mutex: Mutex,
    private val delegate: InputStream,
    private val lockHeld: Boolean,
) : InputStream() {
    private val closed = AtomicBoolean(false)

    override fun read(): Int = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { delegate.close() } catch (_: Exception) {}
        runBlocking(Dispatchers.IO) {
            try {
                if (!client.completePendingCommand()) {
                    client.disconnect()
                }
            } catch (_: Exception) {
                try { client.disconnect() } catch (_: Exception) {}
            } finally {
                if (lockHeld) {
                    mutex.unlock()
                }
            }
        }
    }
}

class FtpManagedOutputStream(
    private val client: FTPClient,
    private val mutex: Mutex,
    private val delegate: OutputStream,
    private val lockHeld: Boolean,
) : OutputStream() {
    private val closed = AtomicBoolean(false)

    override fun write(b: Int) = delegate.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
    override fun flush() = delegate.flush()
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { delegate.close() } catch (_: Exception) {}
        runBlocking(Dispatchers.IO) {
            try {
                if (!client.completePendingCommand()) {
                    client.disconnect()
                }
            } catch (_: Exception) {
                try { client.disconnect() } catch (_: Exception) {}
            } finally {
                if (lockHeld) {
                    mutex.unlock()
                }
            }
        }
    }
}
