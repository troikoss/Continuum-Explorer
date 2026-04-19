package com.troikoss.continuum_explorer.utils

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.OperationType
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.managers.UndoManager
import com.troikoss.continuum_explorer.ui.activities.MainActivity
import com.troikoss.continuum_explorer.ui.activities.PopUpActivity
import com.troikoss.continuum_explorer.model.LibraryItem
import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun FileExplorerState.open(item: UniversalFile) {
    if (item.isDirectory) {
        if (item.provider.capabilities.isRemote) {
            navigateTo(null, null, networkProvider = item.provider, networkId = item.providerId)
            return
        }
        if (item.isArchiveEntry) {
            navigateTo(
                newPath = null,
                newUri = null,
                archiveFile = currentArchiveFile,
                archiveUri = currentArchiveUri,
                archivePath = item.archivePath
            )
        } else {
            val itemFileRef = item.fileRef
            val itemDocRef = item.documentFileRef
            if (itemFileRef != null) {
                if (libraryItem == LibraryItem.Gallery) {
                    navigateTo(itemFileRef, null, libraryItem = LibraryItem.Gallery)
                } else {
                    safStack.clear()
                    navigateTo(itemFileRef, null)
                }
            } else if (itemDocRef != null) {
                val oldUri = currentSafUri
                navigateTo(null, itemDocRef.uri)
                oldUri?.let { safStack.add(it) }
            }
        }
    } else {
        val itemFileRef = item.fileRef
        if (ZipUtils.isArchive(item) && itemFileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) {
            navigateTo(
                newPath = null,
                newUri = null,
                archiveFile = itemFileRef,
                archivePath = ""
            )
        } else if (item.isArchiveEntry) {
            Toast.makeText(context, context.getString(R.string.msg_not_supported_archive), Toast.LENGTH_SHORT).show()
        } else if (item.provider.capabilities.isRemote) {
            openRemoteFile(context, scope, item)
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            when {
                libraryItem == LibraryItem.Recent -> putExtra("isRecent", true)
                libraryItem == LibraryItem.Gallery -> putExtra("isGallery", true)
                libraryItem == LibraryItem.RecycleBin -> putExtra("isRecycleBin", true)
                currentPath != null -> putExtra("path", currentPath?.absolutePath)
                currentSafUri != null -> putExtra("uri", currentSafUri.toString())
            }
        }
        context.startActivity(intent)
    } else {
        items.filter { it.isDirectory || (ZipUtils.isArchive(it) && it.fileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) }.forEach { item ->
            val itemFileRef = item.fileRef
            val itemDocRef = item.documentFileRef
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                if (ZipUtils.isArchive(item) && itemFileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value) {
                    putExtra("archivePath", itemFileRef.absolutePath)
                } else if (itemFileRef != null) {
                    putExtra("path", itemFileRef.absolutePath)
                } else if (itemDocRef != null) {
                    putExtra("uri", itemDocRef.uri.toString())
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
        val pastedNames = pasteFromClipboard(context, currentPath, currentSafUri, currentNetworkProvider, currentNetworkId, clipData)
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
    FileOperationsManager.start()
    val intent = Intent(context, PopUpActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)

    scope.launch {
        restoreFiles(context, selectionManager.selectedItems.toList())
        refresh()
        selectionManager.clear()
        GlobalEvents.triggerRefresh()
    }
}

fun FileExplorerState.undo() {
    FileOperationsManager.start()
    val intent = Intent(context, PopUpActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)

    scope.launch {
        UndoManager.undo(context)
        refresh()
        GlobalEvents.triggerRefresh()
        FileOperationsManager.finish()
    }
}

fun FileExplorerState.redo() {
    FileOperationsManager.start()
    val intent = Intent(context, PopUpActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)

    scope.launch {
        UndoManager.redo(context)
        refresh()
        GlobalEvents.triggerRefresh()
        FileOperationsManager.finish()
    }
}

fun FileExplorerState.extractSelection() {
    if (currentNetworkProvider != null || selectionManager.selectedItems.any { it.provider.capabilities.isRemote }) {
        Toast.makeText(context, context.getString(R.string.msg_archive_remote_unsupported), Toast.LENGTH_SHORT).show()
        return
    }
    val selectedArchives = selectionManager.selectedItems.filter { ZipUtils.isArchive(it) && it.fileRef != null }
    if (selectedArchives.isEmpty()) return

    scope.launch {
        FileOperationsManager.start()
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val displayTitle = if (selectedArchives.size == 1) selectedArchives[0].name else context.getString(R.string.delete_items_count, selectedArchives.size)
        val settings = FileOperationsManager.requestExtractOptions(displayTitle)
        if (settings.isCancelled) {
            FileOperationsManager.finish()
            return@launch
        }

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
    if (currentNetworkProvider != null || selected.any { it.provider.capabilities.isRemote }) {
        Toast.makeText(context, context.getString(R.string.msg_archive_remote_unsupported), Toast.LENGTH_SHORT).show()
        return
    }
    val targetFolder = currentPath ?: return
    val folderName = targetFolder.name.ifEmpty { context.getString(R.string.archive) }
    val defaultName = if (selected.size == 1) "${selected[0].name}.zip" else "$folderName.zip"

    scope.launch {
        FileOperationsManager.start()
        val intent = Intent(context, PopUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val settings = FileOperationsManager.requestArchiveOptions(defaultName)
        if (settings.isCancelled) {
            FileOperationsManager.finish()
            return@launch
        }

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
        FileOperationsManager.openRename(target, context) { newName ->
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
    FileOperationsManager.update(0, 1, operationType = OperationType.RENAME)
    FileOperationsManager.currentFileName.value = target.name
    // Don't start a new PopUpActivity here — the existing one (showing INPUT_TEXT) stays alive
    // and transitions to PROGRESS via the state change above, then to COLLISION if needed.

    scope.launch {
        val success = renameFile(target, newName, context)
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
    FileOperationsManager.openCreateFolder(context) { name ->
        scope.launch {
            val success = createDirectory(context, currentPath, currentSafUri, currentNetworkProvider, currentNetworkId, name)
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
    FileOperationsManager.openCreateFile(context) { name ->
        scope.launch {
            val success = createFile(context, currentPath, currentSafUri, currentNetworkProvider, currentNetworkId, name)
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
