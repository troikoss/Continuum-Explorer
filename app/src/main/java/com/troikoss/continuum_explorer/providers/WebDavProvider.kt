package com.troikoss.continuum_explorer.providers

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GetContentLength
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetLastModified
import at.bitfire.dav4jvm.property.ResourceType
import com.troikoss.continuum_explorer.model.*
import com.troikoss.continuum_explorer.utils.NetworkProviderException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager
import okio.BufferedSink

class WebDavProvider(
    private val connection: NetworkConnection,
    @Suppress("UNUSED_PARAMETER") appContext: Context,
) : StorageProvider, Closeable {

    override val kind = ProviderKind.NETWORK_WEBDAV
    override val connectionId: String get() = connection.id
    override val capabilities = ProviderCapabilities(
        canWrite = true,
        canRename = true,
        canDelete = true,
        canRandomAccessRead = true,
        canStreamRead = true,
        canGetShareableUri = false,
        supportsChildEnumeration = true,
        isRemote = true,
    )

    private val baseUrl: HttpUrl = run {
        val rawRoot = connection.rootUrl.ifEmpty {
            val scheme = if (connection.useTls) "https" else "http"
            val port = if (connection.port == 0) connection.protocol.defaultPort else connection.port
            "$scheme://${connection.host}:$port${connection.remotePath}"
        }
        rawRoot.trimEnd('/').toHttpUrl()
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder().apply {
        if (connection.username.isNotEmpty()) {
            addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(connection.username, connection.password))
                    .build()
                chain.proceed(req)
            }
        }
        if (connection.acceptUntrustedCerts && connection.useTls) {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAll), SecureRandom())
            }
            sslSocketFactory(sslContext.socketFactory, trustAll)
            hostnameVerifier { _, _ -> true }
        }
        followRedirects(false)
        followSslRedirects(false)
        connectTimeout(15, TimeUnit.SECONDS)
        readTimeout(60, TimeUnit.SECONDS)
        writeTimeout(60, TimeUnit.SECONDS)
    }.build()

    override fun rootId(): String = "webdav://${connection.id}/"

    override fun parentId(childId: String): String? {
        val path = pathOf(childId).trimEnd('/')
        if (path.isEmpty() || path == "/") return null
        val last = path.lastIndexOf('/')
        return if (last <= 0) rootId()
        else makeId(path.substring(0, last) + "/")
    }

    override fun displayName(id: String): String {
        val path = pathOf(id).trimEnd('/')
        return path.substringAfterLast('/').ifEmpty { connection.displayName }
    }

    override fun exists(id: String): Boolean = runBlocking(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(urlOf(id)).method("PROPFIND", null)
                .header("Depth", "0").build()
            val code = httpClient.newCall(req).execute().use { it.code }
            code in 200..299 || code == 207
        } catch (_: Exception) { false }
    }

    override suspend fun listChildren(id: String): List<UniversalFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<UniversalFile>()
        try {
            DavCollection(httpClient, urlOf(id)).propfind(
                1,
                DisplayName.NAME, ResourceType.NAME, GetContentLength.NAME, GetLastModified.NAME, GetContentType.NAME
            ) { response, relation ->
                if (relation != Response.HrefRelation.SELF) {
                    out.add(response.toUniversalFile(id))
                }
            }
        } catch (e: HttpException) {
            when (e.code) {
                401, 403 -> throw NetworkProviderException("Authentication failed", NetworkProviderException.Kind.AUTH, e)
                else -> throw NetworkProviderException("Server error ${e.code}", cause = e)
            }
        } catch (e: UnknownHostException) {
            throw NetworkProviderException("Cannot reach ${connection.host}", NetworkProviderException.Kind.UNREACHABLE, e)
        } catch (e: SocketTimeoutException) {
            throw NetworkProviderException("Connection timed out", NetworkProviderException.Kind.TIMEOUT, e)
        } catch (e: SSLException) {
            throw NetworkProviderException("SSL error: ${e.message}", cause = e)
        }
        out
    }

    override fun getMetadata(id: String): FileMetadata = runBlocking(Dispatchers.IO) {
        var meta: FileMetadata? = null
        DavResource(httpClient, urlOf(id)).propfind(
            0,
            GetContentLength.NAME, GetLastModified.NAME, ResourceType.NAME, GetContentType.NAME
        ) { response, _ ->
            if (meta == null) meta = response.toFileMetadata()
        }
        meta ?: throw NetworkProviderException("Metadata not found for $id")
    }

    override fun findChild(parentId: String, name: String): UniversalFile? = runBlocking(Dispatchers.IO) {
        try {
            listChildren(parentId).firstOrNull { it.name == name }
        } catch (_: Exception) { null }
    }

    override fun openInput(id: String): InputStream = runBlocking(Dispatchers.IO) {
        val req = Request.Builder().url(urlOf(id)).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw NetworkProviderException("GET failed: ${resp.code}")
        resp.body?.byteStream() ?: throw NetworkProviderException("Empty response body")
    }

    fun openRangeInput(id: String, offset: Long): InputStream = runBlocking(Dispatchers.IO) {
        val req = Request.Builder().url(urlOf(id))
            .header("Range", "bytes=$offset-").get().build()
        val resp = httpClient.newCall(req).execute()
        if (resp.code != 206 && !resp.isSuccessful) {
            throw NetworkProviderException("Range GET failed: ${resp.code}")
        }
        resp.body?.byteStream() ?: throw NetworkProviderException("Empty response body")
    }

    override fun openReadFd(id: String): ParcelFileDescriptor? = null
    override fun getShareableUri(id: String): Uri? = null

    override fun createChild(parentId: String, name: String, isDirectory: Boolean): UniversalFile = runBlocking(Dispatchers.IO) {
        val url = urlOf(makeId(pathOf(parentId).trimEnd('/') + "/$name" + if (isDirectory) "/" else ""))
        if (isDirectory) {
            DavCollection(httpClient, url).mkCol(null) {}
        } else {
            val req = Request.Builder().url(url)
                .put(ByteArray(0).toRequestBody(null)).build()
            httpClient.newCall(req).execute().close()
        }
        UniversalFile(
            name = name, isDirectory = isDirectory,
            lastModified = System.currentTimeMillis(), length = 0,
            provider = this@WebDavProvider,
            providerId = makeId(pathOf(parentId).trimEnd('/') + "/$name" + if (isDirectory) "/" else ""),
            parentId = parentId
        )
    }

    override fun createAndOpenOutput(parentId: String, name: String): Pair<UniversalFile, OutputStream> {
        val pipedIn = PipedInputStream()
        val pipedOut = PipedOutputStream()
        pipedIn.connect(pipedOut)
        val url = urlOf(makeId(pathOf(parentId).trimEnd('/') + "/$name"))
        CoroutineScope(Dispatchers.IO).launch {
            val body = object : okhttp3.RequestBody() {
                override fun contentType() = null
                override fun writeTo(sink: BufferedSink) {
                    val buf = ByteArray(32 * 1024)
                    var n = pipedIn.read(buf)
                    while (n > 0) { sink.write(buf, 0, n); n = pipedIn.read(buf) }
                }
            }
            val req = Request.Builder().url(url).put(body).build()
            httpClient.newCall(req).execute().close()
        }
        val uf = UniversalFile(
            name = name, isDirectory = false,
            lastModified = System.currentTimeMillis(), length = 0,
            provider = this,
            providerId = makeId(pathOf(parentId).trimEnd('/') + "/$name"),
            parentId = parentId
        )
        return uf to pipedOut
    }

    override fun delete(id: String): Boolean = runBlocking(Dispatchers.IO) {
        try {
            DavResource(httpClient, urlOf(id)).delete(null, null) {}
            true
        } catch (_: Exception) { false }
    }

    override fun rename(id: String, newName: String): UniversalFile? = runBlocking(Dispatchers.IO) {
        try {
            val oldUrl = urlOf(id)
            val parentPath = pathOf(id).trimEnd('/').substringBeforeLast('/')
            val newPath = "$parentPath/$newName"
            val destUrl = urlOf(makeId(newPath))
            DavResource(httpClient, oldUrl).move(destUrl, true) {}
            val pid = parentId(id)
            UniversalFile(
                name = newName, isDirectory = false,
                lastModified = System.currentTimeMillis(), length = 0,
                provider = this@WebDavProvider, providerId = makeId(newPath), parentId = pid
            )
        } catch (_: Exception) { null }
    }

    override fun close() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    fun makeId(path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return "webdav://${connection.id}$p"
    }

    fun pathOf(id: String): String = id.removePrefix("webdav://${connection.id}").ifEmpty { "/" }

    private fun urlOf(id: String): HttpUrl {
        val path = pathOf(id)
        return baseUrl.newBuilder().apply {
            path.split("/").filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
            if (path.endsWith("/")) addPathSegment("")
        }.build()
    }

    private fun Response.toUniversalFile(parentId: String): UniversalFile {
        val href = this.href.toString()
        val nameProp = this.get(DisplayName::class.java)?.displayName
        val name = nameProp?.ifEmpty { null } ?: href.trimEnd('/').substringAfterLast('/')
        val rtypes = this.get(ResourceType::class.java)?.types ?: emptySet()
        val isDir = rtypes.any { it == ResourceType.COLLECTION }
        val size = this.get(GetContentLength::class.java)?.contentLength ?: 0L
        val modified = this.get(GetLastModified::class.java)?.lastModified ?: 0L
        val mime = this.get(GetContentType::class.java)?.type?.toString()
        val myPath = pathOf(parentId).trimEnd('/') + "/$name" + if (isDir) "/" else ""
        return UniversalFile(
            name = name, isDirectory = isDir,
            lastModified = modified, length = size,
            provider = this@WebDavProvider, providerId = makeId(myPath),
            parentId = parentId, mimeType = mime
        )
    }

    private fun Response.toFileMetadata(): FileMetadata {
        val rtypes = this.get(ResourceType::class.java)?.types ?: emptySet()
        val isDir = rtypes.any { it == ResourceType.COLLECTION }
        return FileMetadata(
            size = this.get(GetContentLength::class.java)?.contentLength ?: 0L,
            lastModified = this.get(GetLastModified::class.java)?.lastModified ?: 0L,
            isDirectory = isDir,
            mimeType = this.get(GetContentType::class.java)?.type?.toString(),
        )
    }
}
