package com.troikoss.continuum_explorer.managers

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.utils.getUniqueName
import com.troikoss.continuum_explorer.utils.removeTrashMetadata
import com.troikoss.continuum_explorer.utils.saveTrashMetadata
import java.io.File
import java.util.Stack

sealed class UndoAction {
    data class Rename(val parentDir: File, val oldName: String, val newName: String) : UndoAction()
    data class RenameSaf(val parentUri: String, val oldName: String, val newName: String) : UndoAction()
    data class Recycle(var recycledItems: List<Triple<String, String, String>>) : UndoAction() // (uuid, originalPath, innerName)
    data class Paste(val isMove: Boolean, val items: List<Pair<String, String>>) : UndoAction() // SourcePath to DestPath
}

object UndoManager {
    private val undoStack = Stack<UndoAction>()
    private val redoStack = Stack<UndoAction>()
    
    val canUndo = mutableStateOf(false)
    val canRedo = mutableStateOf(false)

    fun record(action: UndoAction) {
        undoStack.push(action)
        redoStack.clear()
        updateStates()
    }

    private fun updateStates() {
        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }

    suspend fun undo(context: Context) {
        if (undoStack.isEmpty()) return
        val action = undoStack.pop()
        
        if (performUndo(action, context)) {
            redoStack.push(action)
        }
        updateStates()
    }

    suspend fun redo(context: Context) {
        if (redoStack.isEmpty()) return
        val action = redoStack.pop()
        
        if (performRedo(action, context)) {
            undoStack.push(action)
        }
        updateStates()
    }

    private suspend fun performUndo(action: UndoAction, context: Context): Boolean {
        return when (action) {
            is UndoAction.Rename -> {
                val currentFile = File(action.parentDir, action.newName)
                var targetFile = File(action.parentDir, action.oldName)
                
                if (targetFile.exists() && currentFile.exists()) {
                    val result = FileOperationsManager.resolveCollision(targetFile.name)
                    when (result) {
                        CollisionResult.CANCEL -> return false
                        CollisionResult.REPLACE -> {
                            if (targetFile.isDirectory) targetFile.deleteRecursively() else targetFile.delete()
                        }
                        CollisionResult.KEEP_BOTH -> {
                            val newName = getUniqueName(targetFile.name) { File(action.parentDir, it).exists() }
                            targetFile = File(action.parentDir, newName)
                        }
                        CollisionResult.MERGE -> {
                            // No-op: fall through to recurse into existing directory
                        }
                    }
                }
                currentFile.exists() && currentFile.renameTo(targetFile)
            }
            is UndoAction.RenameSaf -> {
                val parentDoc = DocumentFile.fromTreeUri(context, action.parentUri.toUri()) ?: return false
                val currentFile = parentDoc.findFile(action.newName) ?: return false
                var targetName = action.oldName

                val existing = parentDoc.findFile(targetName)
                if (existing != null) {
                    val result = FileOperationsManager.resolveCollision(targetName)
                    when (result) {
                        CollisionResult.CANCEL -> return false
                        CollisionResult.REPLACE -> {
                            existing.delete()
                        }
                        CollisionResult.KEEP_BOTH -> {
                            targetName = getUniqueName(targetName) { parentDoc.findFile(it) != null }
                        }
                        CollisionResult.MERGE -> {}
                    }
                }
                currentFile.renameTo(targetName)
            }
            is UndoAction.Recycle -> {
                val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                var success = true
                action.recycledItems.forEach { (uuid, originalPath, innerName) ->
                    val wrapper = File(trashDir, uuid)
                    val recycledFile = File(wrapper, innerName)
                    var target = File(originalPath)
                    if (recycledFile.exists()) {
                        if (target.exists()) {
                            val result = FileOperationsManager.resolveCollision(target.name)
                            when (result) {
                                CollisionResult.CANCEL -> { success = false; return@forEach }
                                CollisionResult.REPLACE -> {
                                    if (target.isDirectory) target.deleteRecursively() else target.delete()
                                }
                                CollisionResult.KEEP_BOTH -> {
                                    val parent = target.parentFile
                                    val newName = getUniqueName(target.name) { File(parent, it).exists() }
                                    target = File(parent, newName)
                                }
                                CollisionResult.MERGE -> {}
                            }
                        }
                        target.parentFile?.mkdirs()
                        if (recycledFile.renameTo(target)) {
                            removeTrashMetadata(uuid)
                            wrapper.delete()
                        } else success = false
                    } else success = false
                }
                success
            }
            is UndoAction.Paste -> {
                var success = true
                action.items.forEach { (sourcePath, destPath) ->
                    // destPath may be a local file path or a content:// URI (SAF destination)
                    if (destPath.startsWith("content://")) {
                        val destDoc = DocumentFile.fromSingleUri(context, destPath.toUri())
                        if (destDoc != null && destDoc.exists()) {
                            if (!destDoc.delete()) success = false
                        } else if (action.isMove) success = false
                        // Move undo for SAF destinations is not recoverable without a tree URI — skip source restore
                    } else {
                        val destFile = File(destPath)
                        if (destFile.exists()) {
                            if (action.isMove) {
                                var sourceFile = File(sourcePath)
                                if (sourceFile.exists()) {
                                    val result = FileOperationsManager.resolveCollision(sourceFile.name)
                                    when (result) {
                                        CollisionResult.CANCEL -> { success = false; return@forEach }
                                        CollisionResult.REPLACE -> {
                                            if (sourceFile.isDirectory) sourceFile.deleteRecursively() else sourceFile.delete()
                                        }
                                        CollisionResult.KEEP_BOTH -> {
                                            val parent = sourceFile.parentFile
                                            val newName = getUniqueName(sourceFile.name) { File(parent, it).exists() }
                                            sourceFile = File(parent, newName)
                                        }
                                        CollisionResult.MERGE -> {
                                            // No-op: fall through to recurse into existing directory
                                        }
                                    }
                                }
                                sourceFile.parentFile?.mkdirs()
                                if (!destFile.renameTo(sourceFile)) success = false
                            } else {
                                if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
                            }
                        } else if (action.isMove) success = false
                    }
                }
                success
            }
        }
    }

    private suspend fun performRedo(action: UndoAction, context: Context): Boolean {
        return when (action) {
            is UndoAction.Rename -> {
                val oldFile = File(action.parentDir, action.oldName)
                var newFile = File(action.parentDir, action.newName)
                
                if (newFile.exists() && oldFile.exists()) {
                    val result = FileOperationsManager.resolveCollision(newFile.name)
                    when (result) {
                        CollisionResult.CANCEL -> return false
                        CollisionResult.REPLACE -> {
                            if (newFile.isDirectory) newFile.deleteRecursively() else newFile.delete()
                        }
                        CollisionResult.KEEP_BOTH -> {
                            val newName = getUniqueName(newFile.name) { File(action.parentDir, it).exists() }
                            newFile = File(action.parentDir, newName)
                        }
                        CollisionResult.MERGE -> {
                            // No-op: fall through to recurse into existing directory
                        }
                    }
                }
                oldFile.exists() && oldFile.renameTo(newFile)
            }
            is UndoAction.RenameSaf -> {
                val parentDoc = DocumentFile.fromTreeUri(context, action.parentUri.toUri()) ?: return false
                val currentFile = parentDoc.findFile(action.oldName) ?: return false
                var targetName = action.newName

                val existing = parentDoc.findFile(targetName)
                if (existing != null) {
                    val result = FileOperationsManager.resolveCollision(targetName)
                    when (result) {
                        CollisionResult.CANCEL -> return false
                        CollisionResult.REPLACE -> {
                            existing.delete()
                        }
                        CollisionResult.KEEP_BOTH -> {
                            targetName = getUniqueName(targetName) { parentDoc.findFile(it) != null }
                        }
                        CollisionResult.MERGE -> {}
                    }
                }
                currentFile.renameTo(targetName)
            }
            is UndoAction.Recycle -> {
                val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                var success = true
                val rewritten = mutableListOf<Triple<String, String, String>>()
                action.recycledItems.forEach { (_, originalPath, innerName) ->
                    val originalFile = File(originalPath)
                    if (originalFile.exists()) {
                        val newUuid = java.util.UUID.randomUUID().toString()
                        val wrapper = File(trashDir, newUuid).apply { mkdirs() }
                        val target = File(wrapper, innerName)
                        if (originalFile.renameTo(target)) {
                            saveTrashMetadata(newUuid, originalPath)
                            rewritten.add(Triple(newUuid, originalPath, innerName))
                        } else { wrapper.delete(); success = false }
                    } else success = false
                }
                if (rewritten.isNotEmpty()) action.recycledItems = rewritten
                success
            }
            is UndoAction.Paste -> {
                var success = true
                action.items.forEach { (sourcePath, destPath) ->
                    val sourceFile = File(sourcePath)
                    var destFile = File(destPath)
                    if (action.isMove) {
                        if (sourceFile.exists()) {
                            if (destFile.exists()) {
                                val result = FileOperationsManager.resolveCollision(destFile.name)
                                when (result) {
                                CollisionResult.CANCEL -> { success = false; return@forEach }
                                CollisionResult.REPLACE -> {
                                if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
                                }
                                CollisionResult.KEEP_BOTH -> {
                                val parent = destFile.parentFile
                                val newName = getUniqueName(destFile.name) { File(parent, it).exists() }
                                destFile = File(parent, newName)
                                }
                                    CollisionResult.MERGE -> {
                                    // No-op: fall through to recurse into existing directory
                                }
                            }
                            }
                            destFile.parentFile?.mkdirs()
                            if (!sourceFile.renameTo(destFile)) success = false
                        } else success = false
                    } else {
                        // Copy redo is not supported: the original source data may no longer exist.
                        success = false
                        return@forEach
                    }
                }
                success
            }
        }
    }
    
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateStates()
    }
}
