package com.troikoss.continuum_explorer.providers

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.troikoss.continuum_explorer.model.FileMetadata
import com.troikoss.continuum_explorer.model.ProviderCapabilities
import com.troikoss.continuum_explorer.model.ProviderKind
import com.troikoss.continuum_explorer.model.StorageProvider
import com.troikoss.continuum_explorer.model.UniversalFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object LocalProvider : StorageProvider {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override val kind = ProviderKind.LOCAL
    override val capabilities = ProviderCapabilities(
        canWrite = true,
        canRename = true,
        canDelete = true,
        canRandomAccessRead = true,
        canGetShareableUri = true,
        supportsChildEnumeration = true,
        isRemote = false,
    )

    override fun rootId() = File.separator

    override fun parentId(childId: String): String? {
        val parent = File(childId).parentFile ?: return null
        return parent.absolutePath
    }

    override fun displayName(id: String) = File(id).name.ifEmpty { id }

    override fun exists(id: String) = File(id).exists()

    override suspend fun listChildren(id: String): List<UniversalFile> {
        return File(id).listFiles()
            ?.map { it.toUniversalFile() }
            ?: emptyList()
    }

    override fun getMetadata(id: String): FileMetadata {
        val f = File(id)
        return FileMetadata(f.length(), f.lastModified(), f.isDirectory, null)
    }

    override fun findChild(parentId: String, name: String): UniversalFile? {
        val f = File(parentId, name)
        return if (f.exists()) f.toUniversalFile() else null
    }

    override fun openInput(id: String): InputStream = FileInputStream(id)

    override fun openReadFd(id: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(id), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun getShareableUri(id: String): Uri? {
        return try {
            FileProvider.getUriForFile(
                appContext,
                appContext.packageName + ".provider",
                File(id)
            )
        } catch (_: Exception) { null }
    }

    override fun createChild(parentId: String, name: String, isDirectory: Boolean): UniversalFile {
        val f = File(parentId, name)
        if (isDirectory) f.mkdirs() else f.createNewFile()
        return f.toUniversalFile()
    }

    override fun createAndOpenOutput(parentId: String, name: String): Pair<UniversalFile, OutputStream> {
        val f = File(parentId, name)
        return f.toUniversalFile() to FileOutputStream(f)
    }

    override fun delete(id: String): Boolean {
        val f = File(id)
        return if (f.isDirectory) f.deleteRecursively() else f.delete()
    }

    override fun rename(id: String, newName: String): UniversalFile? {
        val f = File(id)
        val dest = File(f.parent, newName)
        return if (f.renameTo(dest)) dest.toUniversalFile() else null
    }

    fun File.toUniversalFile(): UniversalFile = UniversalFile(
        name = this.name,
        isDirectory = this.isDirectory,
        lastModified = this.lastModified(),
        length = this.length(),
        provider = LocalProvider,
        providerId = this.absolutePath,
        parentId = this.parentFile?.absolutePath,
    )
}
