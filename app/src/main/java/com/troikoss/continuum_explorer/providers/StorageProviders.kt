package com.troikoss.continuum_explorer.providers

import android.content.Context
import android.net.Uri
import com.troikoss.continuum_explorer.model.NetworkConnection
import com.troikoss.continuum_explorer.model.NetworkProtocol
import com.troikoss.continuum_explorer.model.ProviderKind
import com.troikoss.continuum_explorer.model.StorageProvider
import java.io.Closeable
import java.io.File

object StorageProviders {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        LocalProvider.init(context)
        SafProvider.init(context)
    }

    private val archiveCache = java.util.concurrent.ConcurrentHashMap<String, ArchiveProvider>()

    fun archive(context: Context, source: Any): ArchiveProvider {
        val key = when (source) {
            is File -> source.absolutePath
            is Uri -> source.toString()
            else -> source.toString()
        }
        return archiveCache.getOrPut(key) { ArchiveProvider(source, context.applicationContext) }
    }

    fun evictArchive(source: Any) {
        val key = when (source) {
            is File -> (source as File).absolutePath
            is Uri -> source.toString()
            else -> source.toString()
        }
        archiveCache.remove(key)
    }

    fun providerFor(kind: ProviderKind): StorageProvider = when (kind) {
        ProviderKind.LOCAL -> LocalProvider
        ProviderKind.SAF -> SafProvider
        else -> throw IllegalArgumentException("No singleton provider for $kind — use specific factory")
    }

    private val networkCache = java.util.concurrent.ConcurrentHashMap<String, StorageProvider>()

    fun network(connection: NetworkConnection): StorageProvider =
        networkCache.getOrPut(connection.id) {
            when (connection.protocol) {
                NetworkProtocol.FTP -> FtpProvider(connection, appContext)
                NetworkProtocol.WEBDAV -> WebDavProvider(connection, appContext)
                else -> throw UnsupportedOperationException("Protocol ${connection.protocol} not implemented")
            }
        }

    fun evictNetwork(connectionId: String) {
        networkCache.remove(connectionId)?.let { (it as? Closeable)?.runCatching { close() } }
    }
}
