package com.troikoss.continuum_explorer.managers

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import java.io.File

object GalleryManager {
    // Returns only the media files directly in dirPath (no subdirectories).
    fun getAlbumContents(context: Context, dirPath: String): List<UniversalFile> {
        val mediaFiles = mutableListOf<UniversalFile>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )

        val normalizedDir = dirPath.trimEnd('/')

        // Match files directly in dirPath: path like 'dir/%' but NOT 'dir/%/%'
        val selection = "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?) AND " +
                "${MediaStore.Files.FileColumns.DATA} LIKE ? AND " +
                "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND " +
                "${MediaStore.Files.FileColumns.DATA} NOT LIKE ?"

        val selectionArgs = arrayOf("image/%", "video/%", "$normalizedDir/%", "$normalizedDir/%/%", "%/.%")

        try {
            val queryUri = MediaStore.Files.getContentUri("external")
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
            }

            context.contentResolver.query(queryUri, projection, queryArgs, null)?.use { c ->
                val dataIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (c.moveToNext()) {
                    val path = c.getString(dataIndex) ?: continue
                    mediaFiles.add(
                        UniversalFile(
                            name = c.getString(nameIndex) ?: File(path).name,
                            isDirectory = false,
                            lastModified = c.getLong(dateIndex) * 1000,
                            length = c.getLong(sizeIndex),
                            provider = LocalProvider,
                            providerId = path,
                            parentId = File(path).parentFile?.absolutePath,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return mediaFiles
    }

    fun getGalleryFiles(context: Context): List<UniversalFile> {
        val files = mutableListOf<UniversalFile>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val selection = "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?) AND " +
                "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND " +
                "${MediaStore.Files.FileColumns.DATA} NOT LIKE ?"

        val selectionArgs = arrayOf(
            "image/%",
            "video/%",
            "%/Android/%",
            "%/.%"
        )

        try {
            val queryUri = MediaStore.Files.getContentUri("external")

            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
            }

            context.contentResolver.query(queryUri, projection, queryArgs, null)?.use { c ->
                val dataIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (c.moveToNext()) {
                    val path = c.getString(dataIndex) ?: continue
                    val file = File(path)
                    if (file.exists() && !file.isDirectory) {
                        files.add(
                            UniversalFile(
                                name = c.getString(nameIndex) ?: file.name,
                                isDirectory = false,
                                lastModified = c.getLong(dateIndex) * 1000,
                                length = c.getLong(sizeIndex),
                                provider = LocalProvider,
                                providerId = path,
                                parentId = file.parentFile?.absolutePath,
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return files
    }

    fun getGalleryAlbums(context: Context): List<UniversalFile> {
        // Flat list of all folders containing media, keyed by bucket name.
        val albums = linkedMapOf<String, File>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val selection = "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?) AND " +
                "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND " +
                "${MediaStore.Files.FileColumns.DATA} NOT LIKE ?"

        val selectionArgs = arrayOf("image/%", "video/%", "%/Android/%", "%/.%")

        try {
            val queryUri = MediaStore.Files.getContentUri("external")
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }

            context.contentResolver.query(queryUri, projection, queryArgs, null)?.use { c ->
                val dataIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val bucketIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (c.moveToNext()) {
                    val path = c.getString(dataIndex) ?: continue
                    val bucketName = c.getString(bucketIndex) ?: continue
                    if (bucketName !in albums) {
                        val parentDir = File(path).parentFile
                        if (parentDir != null) {
                            albums[bucketName] = parentDir
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return albums.entries.sortedBy { it.key.lowercase() }.map { (name, dir) ->
            UniversalFile(
                name = name,
                isDirectory = true,
                lastModified = dir.lastModified(),
                length = 0,
                provider = LocalProvider,
                providerId = dir.absolutePath,
                parentId = dir.parentFile?.absolutePath,
            )
        }
    }
}