package com.troikoss.continuum_explorer.providers

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.Session
import com.troikoss.continuum_explorer.model.FileMetadata
import com.troikoss.continuum_explorer.model.NetworkConnection
import com.troikoss.continuum_explorer.model.ProviderCapabilities
import com.troikoss.continuum_explorer.model.ProviderKind
import com.troikoss.continuum_explorer.model.StorageProvider
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.NetworkProviderException
import com.troikoss.continuum_explorer.utils.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.Base64
import java.util.Vector
import java.util.concurrent.atomic.AtomicBoolean

class SftpProvider(
    private val connection: NetworkConnection,
    private val appContext: Context,
) : StorageProvider, Closeable {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val SESSION_TIMEOUT_MS = 120_000
        private const val TRUST_PREFIX = "sftp_hostkey_"
    }

    override val kind = ProviderKind.NETWORK_SFTP
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
    private var session: Session? = null
    private var channel: ChannelSftp? = null

    private fun trustKey(): String {
        val host = connection.host.trim().lowercase()
        val port = if (connection.port == 0) connection.protocol.defaultPort else connection.port
        return "$TRUST_PREFIX$host:$port"
    }

    private fun fingerprint(hostKeyBase64: String): String {
        val decoded = runCatching { Base64.getDecoder().decode(hostKeyBase64) }
            .getOrElse { hostKeyBase64.toByteArray(Charsets.UTF_8) }
        val digest = MessageDigest.getInstance("SHA-256").digest(decoded)
        return Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    private fun verifyAndPersistHostKey(s: Session) {
        val hostKey = s.hostKey ?: throw NetworkProviderException("Server did not provide host key")
        val pinned = "${hostKey.type}:${hostKey.key}"
        val prefs = SecurePrefs.get(appContext)
        val key = trustKey()
        val existing = prefs.getString(key, null)
        if (existing == null) {
            prefs.edit().putString(key, pinned).apply()
            return
        }
        if (existing != pinned) {
            val fp = fingerprint(hostKey.key)
            throw NetworkProviderException(
                "SFTP host key mismatch for ${connection.host}. New fingerprint: SHA256:$fp",
                NetworkProviderException.Kind.AUTH,
            )
        }
    }

    private fun readPrivateKeyBytes(): ByteArray? {
        val uriValue = connection.sftpPrivateKeyUri.trim()
        if (uriValue.isNotEmpty()) {
            val uri = try {
                Uri.parse(uriValue)
            } catch (e: Exception) {
                throw NetworkProviderException("Invalid private key URI", NetworkProviderException.Kind.AUTH, e)
            }
            try {
                appContext.contentResolver.openInputStream(uri).use { stream ->
                    if (stream == null) throw NetworkProviderException("Cannot read private key URI", NetworkProviderException.Kind.AUTH)
                    return stream.readBytes()
                }
            } catch (e: Exception) {
                throw NetworkProviderException("Cannot read private key URI: ${e.message}", NetworkProviderException.Kind.AUTH, e)
            }
        }
        val pasted = connection.sftpPrivateKey.trim()
        if (pasted.isNotEmpty()) return pasted.toByteArray(Charsets.UTF_8)
        return null
    }

    private fun forceReconnect() {
        channel?.runCatching { disconnect() }
        session?.runCatching { disconnect() }
        channel = null
        session = null
    }

    private fun mapConnectFailure(e: Exception): NetworkProviderException {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Auth fail", ignoreCase = true) ||
                msg.contains("authentication", ignoreCase = true) ->
                NetworkProviderException("Authentication failed", NetworkProviderException.Kind.AUTH, e)
            msg.contains("timeout", ignoreCase = true) ->
                NetworkProviderException("Connection timed out: $msg", NetworkProviderException.Kind.TIMEOUT, e)
            else -> NetworkProviderException("Cannot reach ${connection.host}: $msg", NetworkProviderException.Kind.UNREACHABLE, e)
        }
    }

    private fun ensureConnected(): ChannelSftp {
        val existingSession = session
        val existingChannel = channel
        if (existingSession?.isConnected == true && existingChannel?.isConnected == true) {
            return existingChannel
        }
        forceReconnect()

        val username = connection.username.trim()
        if (username.isEmpty()) {
            throw NetworkProviderException("SFTP requires a username", NetworkProviderException.Kind.AUTH)
        }

        val jsch = JSch()
        val privateKey = readPrivateKeyBytes()
        val passphrase = connection.sftpPrivateKeyPassphrase.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
        if (privateKey != null) {
            try {
                jsch.addIdentity("sftp-${connection.id}", privateKey, null, passphrase)
            } catch (e: Exception) {
                throw NetworkProviderException("Invalid private key: ${e.message}", NetworkProviderException.Kind.AUTH, e)
            }
        }

        val port = if (connection.port == 0) connection.protocol.defaultPort else connection.port
        val s = try {
            jsch.getSession(username, connection.host, port)
        } catch (e: JSchException) {
            throw mapConnectFailure(e)
        }

        val hasPassword = connection.password.isNotEmpty()
        val preferredAuth = when {
            privateKey != null && hasPassword -> "publickey,password"
            privateKey != null -> "publickey"
            hasPassword -> "password"
            else -> "publickey,password"
        }
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", preferredAuth)
        s.timeout = SESSION_TIMEOUT_MS
        if (hasPassword) s.setPassword(connection.password)

        try {
            s.connect(CONNECT_TIMEOUT_MS)
            verifyAndPersistHostKey(s)
            val ch = (s.openChannel("sftp") as ChannelSftp).also { it.connect(CONNECT_TIMEOUT_MS) }
            session = s
            channel = ch
            return ch
        } catch (e: SocketTimeoutException) {
            s.runCatching { disconnect() }
            throw NetworkProviderException("Connection timed out: ${e.message}", NetworkProviderException.Kind.TIMEOUT, e)
        } catch (e: Exception) {
            s.runCatching { disconnect() }
            throw mapConnectFailure(e)
        }
    }

    private fun isNotFound(error: Throwable): Boolean =
        error is SftpException && error.id == ChannelSftp.SSH_FX_NO_SUCH_FILE

    private fun <T> withSftpConnection(block: (ChannelSftp) -> T): T {
        return try {
            block(ensureConnected())
        } catch (e: Exception) {
            if (e is NetworkProviderException) throw e
            forceReconnect()
            block(ensureConnected())
        }
    }

    override fun rootId(): String = makeId(connection.remotePath.ifEmpty { "/" })

    override fun parentId(childId: String): String? {
        val path = pathOf(childId).trimEnd('/').ifEmpty { "/" }
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
                withSftpConnection { c -> c.lstat(pathOf(id)); true }
            } catch (e: Exception) {
                if (isNotFound(e)) false else false
            }
        }
    }

    override suspend fun listChildren(id: String): List<UniversalFile> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val path = pathOf(id)
                withSftpConnection { c ->
                    @Suppress("UNCHECKED_CAST")
                    val entries = c.ls(path) as Vector<ChannelSftp.LsEntry>
                    entries.asSequence()
                        .filter { it.filename != "." && it.filename != ".." }
                        .map { it.toUniversalFile(id) }
                        .toList()
                }
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
            val attrs = withSftpConnection { c -> c.lstat(pathOf(id)) }
            FileMetadata(
                size = attrs.size,
                lastModified = attrs.mTime.toLong() * 1000L,
                isDirectory = attrs.isDir,
                mimeType = null,
            )
        }
    }

    override fun findChild(parentId: String, name: String): UniversalFile? = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val childPath = joinPath(pathOf(parentId), name)
            try {
                val attrs = withSftpConnection { c -> c.lstat(childPath) }
                UniversalFile(
                    name = name,
                    isDirectory = attrs.isDir,
                    lastModified = attrs.mTime.toLong() * 1000L,
                    length = attrs.size,
                    provider = this@SftpProvider,
                    providerId = makeId(childPath),
                    parentId = parentId,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun openInput(id: String): InputStream = runBlocking(Dispatchers.IO) {
        mutex.lock()
        try {
            val stream = withSftpConnection { c -> c.get(pathOf(id)) }
            SftpManagedInputStream(mutex, stream, lockHeld = true)
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
            withSftpConnection { c ->
                if (isDirectory) {
                    c.mkdir(path)
                } else {
                    c.put(ByteArrayInputStream(ByteArray(0)), path)
                }
            }
            UniversalFile(
                name = name,
                isDirectory = isDirectory,
                lastModified = System.currentTimeMillis(),
                length = 0,
                provider = this@SftpProvider,
                providerId = makeId(path),
                parentId = parentId,
            )
        }
    }

    override fun createAndOpenOutput(parentId: String, name: String): Pair<UniversalFile, OutputStream> {
        val path = joinPath(pathOf(parentId), name)
        val stream = runBlocking(Dispatchers.IO) {
            mutex.lock()
            try {
                withSftpConnection { c -> c.put(path) }
            } catch (e: Exception) {
                mutex.unlock()
                throw e
            }
        }
        val uf = UniversalFile(
            name = name,
            isDirectory = false,
            lastModified = System.currentTimeMillis(),
            length = 0,
            provider = this,
            providerId = makeId(path),
            parentId = parentId,
        )
        return uf to SftpManagedOutputStream(mutex, stream, lockHeld = true)
    }

    override suspend fun copyFrom(
        source: UniversalFile,
        destParentId: String,
        destName: String,
        onProgress: (Long) -> Unit,
    ): UniversalFile {
        if (source.provider !== this) {
            return super.copyFrom(source, destParentId, destName, onProgress)
        }
        val bytes = withContext(Dispatchers.IO) {
            mutex.withLock {
                withSftpConnection { c -> c.get(pathOf(source.providerId)).use { it.readBytes() } }
            }
        }
        val path = joinPath(pathOf(destParentId), destName)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                withSftpConnection { c -> c.put(ByteArrayInputStream(bytes), path) }
            }
        }
        onProgress(bytes.size.toLong())
        return UniversalFile(
            name = destName,
            isDirectory = false,
            lastModified = System.currentTimeMillis(),
            length = bytes.size.toLong(),
            provider = this,
            providerId = makeId(path),
            parentId = destParentId,
        )
    }

    override fun delete(id: String): Boolean = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val path = pathOf(id)
            withSftpConnection { c ->
                try {
                    c.rm(path)
                    true
                } catch (_: Exception) {
                    c.rmdir(path)
                    true
                }
            }
        }
    }

    override fun rename(id: String, newName: String): UniversalFile? = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            val oldPath = pathOf(id)
            val newPath = joinPath(oldPath.substringBeforeLast('/').ifEmpty { "/" }, newName)
            val wasDir = runCatching { withSftpConnection { c -> c.lstat(oldPath).isDir } }.getOrDefault(false)
            withSftpConnection { c -> c.rename(oldPath, newPath) }
            val pid = parentId(id)
            UniversalFile(
                name = newName,
                isDirectory = wasDir,
                lastModified = System.currentTimeMillis(),
                length = 0,
                provider = this@SftpProvider,
                providerId = makeId(newPath),
                parentId = pid,
            )
        }
    }

    override fun close() {
        forceReconnect()
    }

    private fun makeId(path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return "sftp://${connection.id}$p"
    }

    private fun pathOf(id: String): String = id.removePrefix("sftp://${connection.id}").ifEmpty { "/" }

    private fun joinPath(parent: String, name: String): String {
        val p = parent.trimEnd('/').ifEmpty { "/" }
        return if (p == "/") "/$name" else "$p/$name"
    }

    private fun ChannelSftp.LsEntry.toUniversalFile(parentId: String): UniversalFile {
        val attrs: SftpATTRS = attrs
        return UniversalFile(
            name = filename,
            isDirectory = attrs.isDir,
            lastModified = attrs.mTime.toLong() * 1000L,
            length = attrs.size,
            provider = this@SftpProvider,
            providerId = makeId(joinPath(pathOf(parentId), filename)),
            parentId = parentId,
        )
    }
}

class SftpManagedInputStream(
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
        if (lockHeld) mutex.unlock()
    }
}

class SftpManagedOutputStream(
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
        if (lockHeld) mutex.unlock()
    }
}


