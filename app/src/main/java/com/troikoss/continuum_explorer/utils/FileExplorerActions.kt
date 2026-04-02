package com.troikoss.continuum_explorer.utils

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.managers.UndoManager
import com.troikoss.continuum_explorer.ui.activities.MainActivity
import com.troikoss.continuum_explorer.ui.activities.PopUpActivity
import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun FileExplorerState.open(item: UniversalFile) {
    if (item.isDirectory) {
        if (item.isArchiveEntry) {
            navigateTo(
                newPath = null,
                newUri = null,
                archiveFile = currentArchiveFile,
                archiveUri = currentArchiveUri,
                archivePath = item.archivePath
            )
        } else if (item.fileRef != null) {
            safStack.clear()
            navigateTo(item.fileRef, null)
        } else if (item.documentFileRef != null) {
            val oldUri = currentSafUri
            navigateTo(null, item.documentFileRef.uri)
            oldUri?.let { safStack.add(it) }
        }
    } else {
        if (ZipUtils.isArchive(item) && item.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) {
            navigateTo(
                newPath = null,
                newUri = null,
                archiveFile = item.fileRef,
                archivePath = ""
            )
        } else if (item.isArchiveEntry) {
            Toast.makeText(context, context.getString(R.string.msg_not_supported_archive), Toast.LENGTH_SHORT).show()
        } else {
            openFile(context, item)
        }
    }
}

fun FileExplorerState.openInNewTab(items: List<UniversalFile>) {
    items.filter { it.isDirectory || (ZipUtils.isArchive(it) && it.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) }.forEach { item ->
        onOpenInNewTab?.invoke(item)
    }
}

fun FileExplorerState.openInNewWindow(items: List<UniversalFile>) {
    if (items.isEmpty()) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            if (currentPath != null) {
                putExtra("path", currentPath?.absolutePath)
            } else if (currentSafUri != null) {
                putExtra("uri", currentSafUri.toString())
            }
        }
        context.startActivity(intent)
    } else {
        items.filter { it.isDirectory || (ZipUtils.isArchive(it) && it.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) }.forEach { item ->
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                if (ZipUtils.isArchive(item) && item.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) {
                    putExtra("archivePath", item.fileRef.absolutePath)
                } else if (item.fileRef != null) {
                    putExtra("path", item.fileRef.absolutePath)
                } else if (item.documentFileRef != null) {
                    putExtra("uri", item.documentFileRef.uri.toString())
                }
            }
            context.startActivity(intent)
        }
    }
}

fun FileExplorerState.copySelection() {
    copyToClipboard(context, selectionManager.selectedItems.toList())
}

fun FileExplorerState.cutSelection() {
    cutToClipboard(context, selectionManager.selectedItems.toList())
}

fun FileExplorerState.paste() {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = clipboard.primaryClip

    FileOperationsManager.start()
    val intent = Intent(context, PopUpActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)

    scope.launch {
        val pastedNames = pasteFromClipboard(context, currentPath, currentSafUri, clipData)
        refresh()
        if (pastedNames.isNotEmpty()) {
            val pastedFiles = files.filter { pastedNames.contains(it.name) }
            if (pastedFiles.isNotEmpty()) {
                selectionManager.clear()
                pastedFiles.forEach { selectionManager.select(it) }
            }
        }
        if (PendingCut.isActive) GlobalEvents.triggerRefresh()
    }
}

fun FileExplorerState.deleteSelection(forcePermanent: Boolean = false) {
    FileOperationsManager.start()
    val intent = Intent(context, PopUpActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)

    scope.launch {
        deleteFiles(context, selectionManager.selectedItems.toList(), forcePermanent)
        refresh()
        selectionManager.clear()
        GlobalEvents.triggerRefresh()
    }
}

fun FileExplorerState.restoreSelection() {
    scope.launch {
        restoreFiles(context, selectionManager.selectedItems.toList())
        refresh()
        selectionManager.clear()
        GlobalEvents.triggerRefresh()
    }
}

fun FileExplorerState.undo() {
    scope.launch {
        UndoManager.undo()
        refresh()
        GlobalEvents.triggerRefresh()
    }
}

fun FileExplorerState.redo() {
    scope.launch {
        UndoManager.redo()
        refresh()
        GlobalEvents.triggerRefresh()
    }
}

fun FileExplorerState.extractSelection() {
    val selectedArchives = selectionManager.selectedItems.filter { ZipUtils.isArchive(it) && it.fileRef != null }
    if (selectedArchives.isEmpty()) return

    scope.launch {
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val displayTitle = if (selectedArchives.size == 1) selectedArchives[0].name else context.getString(R.string.delete_items_count, selectedArchives.size)
        val settings = FileOperationsManager.requestExtractOptions(displayTitle)
        if (settings.isCancelled) return@launch

        val parentFile = selectedArchives[0].fileRef?.parentFile ?: return@launch
        ZipUtils.extractArchives(context, selectedArchives, parentFile, settings.toSeparateFolder)
        if (settings.deleteSource) {
            deleteFiles(context, selectedArchives, forcePermanent = true, silent = true)
        }
        refresh()
        GlobalEvents.triggerRefresh()
    }
}

fun FileExplorerState.compressSelection() {
    val selected = selectionManager.selectedItems.toList()
    if (selected.isEmpty()) return
    val targetFolder = currentPath ?: return
    val defaultName = if (selected.size == 1) "${selected[0].name}.zip" else "Archive.zip"

    scope.launch {
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val settings = FileOperationsManager.requestArchiveOptions(defaultName)
        if (settings.isCancelled) return@launch

        ZipUtils.compressFiles(context, selected, targetFolder, settings)
        if (settings.deleteSource) {
            deleteFiles(context, selected, forcePermanent = true, silent = true)
        }
        refresh()
        GlobalEvents.triggerRefresh()
    }
}

fun FileExplorerState.renameSelection() {
    val selected = selectionManager.selectedItems.toList()
    if (selected.size == 1) {
        val target = selected[0]
        FileOperationsManager.openRename(target) { newName ->
            confirmRename(target, newName)
        }
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } else if (selected.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.msg_select_rename), Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, context.getString(R.string.msg_select_one_rename), Toast.LENGTH_SHORT).show()
    }
}

fun FileExplorerState.confirmRename(target: UniversalFile, newName: String) {
    FileOperationsManager.start()
    FileOperationsManager.update(0, 1, context.getString(R.string.op_renaming, target.name))
    FileOperationsManager.currentFileName.value = target.name

    val intent = Intent(context, PopUpActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)

    scope.launch {
        delay(500)
        val success = renameFile(target, newName)
        withContext(Dispatchers.Main) {
            if (success) {
                refresh()
                selectionManager.clear()
                val newFile = files.find { it.name == newName }
                if (newFile != null) selectionManager.select(newFile)
                GlobalEvents.triggerRefresh()
            } else {
                Toast.makeText(context, context.getString(R.string.msg_rename_failed), Toast.LENGTH_SHORT).show()
            }
            FileOperationsManager.finish()
        }
    }
}

fun FileExplorerState.createNewFolder() {
    FileOperationsManager.openCreateFolder { name ->
        scope.launch {
            val success = createDirectory(context, currentPath, currentSafUri, name)
            withContext(Dispatchers.Main) {
                if (success) {
                    refresh()
                    val newFile = files.find { it.name == name }
                    if (newFile != null) selectionManager.select(newFile)
                    GlobalEvents.triggerRefresh()
                } else {
                    Toast.makeText(context, context.getString(R.string.msg_failed_create_folder), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val intent = Intent(context, PopUpActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun FileExplorerState.createNewFile() {
    FileOperationsManager.openCreateFile { name ->
        scope.launch {
            val success = createFile(context, currentPath, currentSafUri, name)
            withContext(Dispatchers.Main) {
                if (success) {
                    refresh()
                    val newFile = files.find { it.name == name }
                    if (newFile != null) selectionManager.select(newFile)
                    GlobalEvents.triggerRefresh()
                } else {
                    Toast.makeText(context, context.getString(R.string.msg_failed_create_file), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val intent = Intent(context, PopUpActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun FileExplorerState.showProperties(items: List<UniversalFile>? = null) {
    val targets = items ?: selectionManager.selectedItems.toList()
    if (targets.isNotEmpty()) {
        FileOperationsManager.showProperties(targets)
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

fun FileExplorerState.emptyRecycleBin() {
    scope.launch {
        val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
        val filesToDelete = trashDir.listFiles()?.map { it.toUniversal() } ?: emptyList()
        if (filesToDelete.isNotEmpty()) {
            FileOperationsManager.start()
            val intent = Intent(context, PopUpActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            deleteFiles(context, filesToDelete, forcePermanent = true)
            refresh()
            GlobalEvents.triggerRefresh()
        } else {
            Toast.makeText(context, context.getString(R.string.msg_recycle_bin_empty), Toast.LENGTH_SHORT).show()
        }
    }
}

fun FileExplorerState.pinSelectionToHome() {
    val selected = selectionManager.selectedItems.toList()
    if (selected.size == 1) {
        ShortcutHelper.addToHome(context, scope, selected[0])
    } else if (selected.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.msg_select_pin), Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, context.getString(R.string.msg_select_one_pin), Toast.LENGTH_SHORT).show()
    }
}
