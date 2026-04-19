package com.troikoss.continuum_explorer.utils

import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object RemoteCache {
    private const val MAX_BYTES = 500L * 1024 * 1024

    fun cacheDir(context: android.content.Context): File =
        File(context.cacheDir, "network").apply { mkdirs() }

    fun cachedFileFor(context: android.content.Context, file: UniversalFile): File {
        val hash = sha1("${file.providerId}_${file.length}_${file.lastModified}")
        return File(File(cacheDir(context), hash), file.name)
    }

    suspend fun cache(
        context: android.content.Context,
        file: UniversalFile,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val target = cachedFileFor(context, file)
        if (target.exists() && target.length() == file.length) return@withContext target
        target.parentFile?.mkdirs()
        file.provider.openInput(file.providerId).use { input ->
            FileOutputStream(target).use { output ->
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    copied += n
                    onProgress(copied, file.length)
                }
            }
        }
        evictIfOverCap(context)
        target
    }

    fun evictFor(context: android.content.Context, providerIdPrefix: String) {
        cacheDir(context).listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.name.contains(providerIdPrefix)) {
                        file.delete()
                        dir.delete()
                    }
                }
            }
        }
    }

    fun evictAll(context: android.content.Context) {
        cacheDir(context).deleteRecursively()
    }

    private fun evictIfOverCap(context: android.content.Context) {
        val cDir = cacheDir(context)
        val allFiles = cDir.walkBottomUp().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var totalSize = allFiles.sumOf { it.length() }
        var i = 0
        while (totalSize > MAX_BYTES && i < allFiles.size) {
            val file = allFiles[i]
            totalSize -= file.length()
            file.delete()
            file.parentFile?.let { if (it.listFiles()?.isEmpty() == true) it.delete() }
            i++
        }
    }

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
