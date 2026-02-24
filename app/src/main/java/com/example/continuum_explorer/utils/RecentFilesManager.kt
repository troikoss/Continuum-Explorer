package com.example.continuum_explorer.utils

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import com.example.continuum_explorer.model.UniversalFile
import java.io.File

object RecentFilesManager {
    fun getRecentFiles(context: Context): List<UniversalFile> {
        val recentFiles = mutableListOf<UniversalFile>()
        
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        
        // Exclude directories and common system folders
        // We use MIME_TYPE IS NOT NULL to exclude folders
        val selection = "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND " +
                        "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND " +
                        "${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL"

        val selectionArgs = arrayOf(
            "%/Android/%",
            "%/.%" // Exclude hidden files
        )

        try {
            val queryUri = MediaStore.Files.getContentUri("external")
            
            // Optimization: use Bundle for limit and sorting (Targeting API 26+)
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
                putInt(ContentResolver.QUERY_ARG_LIMIT, 50)
            }
            
            context.contentResolver.query(queryUri, projection, queryArgs, null)?.use { c ->
                val dataIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeIndex = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (c.moveToNext()) {
                    val path = c.getString(dataIndex) ?: continue
                    val file = File(path)
                    
                    // Final safety check for existence
                    if (file.exists() && !file.isDirectory) {
                        recentFiles.add(
                            UniversalFile(
                                name = c.getString(nameIndex) ?: file.name,
                                isDirectory = false,
                                lastModified = c.getLong(dateIndex) * 1000,
                                length = c.getLong(sizeIndex),
                                fileRef = file,
                                absolutePath = path
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return recentFiles
    }
}
