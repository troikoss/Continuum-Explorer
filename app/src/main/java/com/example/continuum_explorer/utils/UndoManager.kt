package com.example.continuum_explorer.utils

import androidx.compose.runtime.mutableStateOf
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

    private fun performUndo(action: UndoAction): Boolean {
        return when (action) {
            is UndoAction.Rename -> {
                val currentFile = File(action.parentDir, action.newName)
                val oldFile = File(action.parentDir, action.oldName)
                currentFile.exists() && currentFile.renameTo(oldFile)
            }
            is UndoAction.Recycle -> {
                val trashDir = File(android.os.Environment.getExternalStorageDirectory(), ".Trash")
                var success = true
                action.recycledItems.forEach { (recycledName, originalPath) ->
                    val recycledFile = File(trashDir, recycledName)
                    val target = File(originalPath)
                    if (recycledFile.exists()) {
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
                            val sourceFile = File(sourcePath)
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

    private fun performRedo(action: UndoAction): Boolean {
        return when (action) {
            is UndoAction.Rename -> {
                val oldFile = File(action.parentDir, action.oldName)
                val newFile = File(action.parentDir, action.newName)
                oldFile.exists() && oldFile.renameTo(newFile)
            }
            is UndoAction.Recycle -> {
                val trashDir = File(android.os.Environment.getExternalStorageDirectory(), ".Trash")
                var success = true
                action.recycledItems.forEach { (recycledName, originalPath) ->
                    val originalFile = File(originalPath)
                    val target = File(trashDir, recycledName)
                    if (originalFile.exists()) {
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
                    val destFile = File(destPath)
                    if (action.isMove) {
                        if (sourceFile.exists()) {
                            destFile.parentFile?.mkdirs()
                            if (!sourceFile.renameTo(destFile)) success = false
                        } else success = false
                    } else {
                        // For Copy Redo, it's complex to re-copy without context.
                        // We will skip re-copying for now as it's a non-destructive redo.
                        // In a real app, you'd trigger a background copy task here.
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
