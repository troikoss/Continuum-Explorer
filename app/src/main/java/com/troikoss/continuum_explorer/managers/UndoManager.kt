package com.troikoss.continuum_explorer.managers

import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import com.troikoss.continuum_explorer.utils.getUniqueName
import com.troikoss.continuum_explorer.utils.removeTrashMetadata
import com.troikoss.continuum_explorer.utils.saveTrashMetadata
import java.io.File
import java.util.Stack

sealed class UndoAction {
    data class Rename(val parentDir: File, val oldName: String, val newName: String) : UndoAction()
    data class Recycle(val recycledItems: List<Pair<String, String>>) : UndoAction() // RecycledName to OriginalPath
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

    suspend fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.pop()
        
        if (performUndo(action)) {
            redoStack.push(action)
        }
        updateStates()
    }

    suspend fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.pop()
        
        if (performRedo(action)) {
            undoStack.push(action)
        }
        updateStates()
    }

    private suspend fun performUndo(action: UndoAction): Boolean {
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
                    }
                }
                currentFile.exists() && currentFile.renameTo(targetFile)
            }
            is UndoAction.Recycle -> {
                val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                var success = true
                action.recycledItems.forEach { (recycledName, originalPath) ->
                    val recycledFile = File(trashDir, recycledName)
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
                            }
                        }
                        target.parentFile?.mkdirs()
                        if (recycledFile.renameTo(target)) {
                            removeTrashMetadata(recycledName)
                        } else success = false
                    } else success = false
                }
                success
            }
            is UndoAction.Paste -> {
                var success = true
                action.items.forEach { (sourcePath, destPath) ->
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
                                }
                            }
                            sourceFile.parentFile?.mkdirs()
                            if (!destFile.renameTo(sourceFile)) success = false
                        } else {
                            if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
                        }
                    } else if (action.isMove) success = false
                }
                success
            }
        }
    }

    private suspend fun performRedo(action: UndoAction): Boolean {
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
                    }
                }
                oldFile.exists() && oldFile.renameTo(newFile)
            }
            is UndoAction.Recycle -> {
                val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                var success = true
                action.recycledItems.forEach { (recycledName, originalPath) ->
                    val originalFile = File(originalPath)
                    var target = File(trashDir, recycledName)
                    
                    if (originalFile.exists()) {
                        if (target.exists()) {
                            val result = FileOperationsManager.resolveCollision(target.name)
                            when (result) {
                                CollisionResult.CANCEL -> { success = false; return@forEach }
                                CollisionResult.REPLACE -> {
                                    if (target.isDirectory) target.deleteRecursively() else target.delete()
                                }
                                CollisionResult.KEEP_BOTH -> {
                                    val newName = getUniqueName(target.name) { File(trashDir, it).exists() }
                                    target = File(trashDir, newName)
                                }
                            }
                        }
                        if (originalFile.renameTo(target)) {
                            saveTrashMetadata(recycledName, originalPath)
                        } else success = false
                    } else success = false
                }
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
                                }
                            }
                            destFile.parentFile?.mkdirs()
                            if (!sourceFile.renameTo(destFile)) success = false
                        } else success = false
                    } else {
                        // For Copy Redo, it remains complex.
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
