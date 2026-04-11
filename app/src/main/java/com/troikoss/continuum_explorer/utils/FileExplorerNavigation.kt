package com.troikoss.continuum_explorer.utils

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.model.NavLocation
import com.troikoss.continuum_explorer.model.SpecialMode
import java.io.File

fun FileExplorerState.navigateTo(
    newPath: File?,
    newUri: Uri?,
    newRoot: File? = null,
    addToHistory: Boolean = true,
    archiveFile: File? = null,
    archiveUri: Uri? = null,
    archivePath: String? = null,
    specialMode: SpecialMode = SpecialMode.None
) {
    val targetArchivePath = archivePath ?: ""

    if (newPath == currentPath &&
        newUri == currentSafUri &&
        archiveFile == currentArchiveFile &&
        archiveUri == currentArchiveUri &&
        targetArchivePath == currentArchivePath &&
        specialMode == this.specialMode) {

        if (newRoot != null && newRoot != storageRoot) {
            storageRoot = newRoot
        }
        return
    }

    if (addToHistory) {
        backStack.add(NavLocation(currentPath, currentSafUri, currentArchiveFile, currentArchiveUri, currentArchivePath, ArrayList(safStack), this.specialMode))
        forwardStack.clear()
    }

    val isNewArchive = (archiveFile != null && archiveFile != currentArchiveFile) ||
            (archiveUri != null && archiveUri != currentArchiveUri)
    if (isNewArchive) {
        archiveCache = null
    }

    if (newRoot != null) {
        storageRoot = newRoot
    }
    currentPath = newPath
    currentSafUri = newUri
    currentArchiveFile = archiveFile
    currentArchiveUri = archiveUri
    currentArchivePath = targetArchivePath
    this.specialMode = specialMode
    isSearchMode = false

    scrollToItemIndex = null
    selectionManager.reset()
    triggerLoad()
}

fun FileExplorerState.goBack() {
    if (backStack.isNotEmpty()) {
        val leavingPath = currentPath
        val leavingUri = currentSafUri

        val lastLocation = backStack.removeAt(backStack.size - 1)
        forwardStack.add(NavLocation(currentPath, currentSafUri, currentArchiveFile, currentArchiveUri, currentArchivePath, ArrayList(safStack), this.specialMode))

        navigateTo(
            newPath = lastLocation.path,
            newUri = lastLocation.uri,
            addToHistory = false,
            archiveFile = lastLocation.archiveFile,
            archiveUri = lastLocation.archiveUri,
            archivePath = lastLocation.archivePath,
            specialMode = lastLocation.specialMode
        )

        if (lastLocation.safStack != null) {
            safStack.clear()
            safStack.addAll(lastLocation.safStack)
        }

        focusItemInList(leavingPath, leavingUri)
    }
}

fun FileExplorerState.goForward() {
    if (forwardStack.isNotEmpty()) {
        val nextLocation = forwardStack.removeAt(forwardStack.size - 1)
        backStack.add(NavLocation(currentPath, currentSafUri, currentArchiveFile, currentArchiveUri, currentArchivePath, ArrayList(safStack), this.specialMode))

        navigateTo(
            newPath = nextLocation.path,
            newUri = nextLocation.uri,
            addToHistory = false,
            archiveFile = nextLocation.archiveFile,
            archiveUri = nextLocation.archiveUri,
            archivePath = nextLocation.archivePath,
            specialMode = nextLocation.specialMode
        )

        if (nextLocation.safStack != null) {
            safStack.clear()
            safStack.addAll(nextLocation.safStack)
        }
    }
}

fun FileExplorerState.getLocationName(location: NavLocation): String {
    return if (location.specialMode == SpecialMode.Recent) {
        context.getString(R.string.nav_recent)
    } else if (location.specialMode == SpecialMode.Gallery) {
        context.getString(R.string.nav_gallery)
    } else if (location.archiveFile != null) {
        val base = location.archiveFile.name
        val inner = location.archivePath ?: ""
        if (inner.isEmpty()) base else "$base/${inner.removeSuffix("/")}"
    } else if (location.archiveUri != null) {
        val base = context.getString(R.string.archive)
        val inner = location.archivePath ?: ""
        if (inner.isEmpty()) base else "$base/${inner.removeSuffix("/")}"
    } else if (location.path != null) {
        if (location.path.absolutePath == storageRoot.absolutePath) {
            if (storageRoot.absolutePath == Environment.getExternalStorageDirectory().absolutePath) context.getString(R.string.nav_internal_storage)
            else context.getString(R.string.nav_sd_card)
        } else {
            location.path.name
        }
    } else if (location.uri != null) {
        if (location.safStack != null && location.safStack.isNotEmpty()) {
            val doc = DocumentFile.fromTreeUri(context, location.uri)
            doc?.name ?: context.getString(R.string.nav_unknown_folder)
        } else {
            getSafDisplayName(location.uri)
        }
    } else {
        context.getString(R.string.new_tab)
    }
}

fun FileExplorerState.jumpToHistory(index: Int) {
    val allLocations = backStack.toList() +
            listOf(NavLocation(currentPath, currentSafUri, currentArchiveFile, currentArchiveUri, currentArchivePath, ArrayList(safStack), this.specialMode)) +
            forwardStack.asReversed()

    if (index < 0 || index >= allLocations.size) return

    val target = allLocations[index]
    val currentIndex = backStack.size

    if (index == currentIndex) return

    if (index < currentIndex) {
        val toForward = allLocations.subList(index + 1, currentIndex + 1)
        forwardStack.addAll(toForward.asReversed())
        repeat(currentIndex - index) {
            backStack.removeAt(backStack.size - 1)
        }
    } else {
        val toBack = allLocations.subList(currentIndex, index)
        backStack.addAll(toBack)
        repeat(index - currentIndex) {
            forwardStack.removeAt(forwardStack.size - 1)
        }
    }

    navigateTo(
        newPath = target.path,
        newUri = target.uri,
        addToHistory = false,
        archiveFile = target.archiveFile,
        archiveUri = target.archiveUri,
        archivePath = target.archivePath,
        specialMode = target.specialMode
    )

    if (target.safStack != null) {
        safStack.clear()
        safStack.addAll(target.safStack)
    }
}

fun FileExplorerState.goUp() {
    var destinationPath: File? = null
    var destinationUri: Uri? = null
    var leavingPath: File? = null
    var leavingUri: Uri? = null
    var canPerformGoUp = false

    var nextArchiveFile: File? = null
    var nextArchiveUri: Uri? = null
    var nextArchivePath: String? = null

    if (currentArchiveFile != null || currentArchiveUri != null) {
        if (currentArchivePath.isEmpty()) {
            if (currentArchiveFile != null) {
                destinationPath = currentArchiveFile?.parentFile
                leavingPath = currentArchiveFile
            }
            if (destinationPath != null) {
                canPerformGoUp = true
                nextArchiveFile = null
                nextArchiveUri = null
            }
        } else {
            val parentPath = File(currentArchivePath).parent?.replace("\\", "/") ?: ""
            nextArchivePath = if (parentPath.isEmpty() || parentPath == ".") "" else "$parentPath/"
            nextArchiveFile = currentArchiveFile
            nextArchiveUri = currentArchiveUri
            canPerformGoUp = true
        }
    } else if (currentPath != null) {
        if (currentPath?.absolutePath == storageRoot.absolutePath) {
            Toast.makeText(context, context.getString(R.string.msg_already_at_storage_root), Toast.LENGTH_SHORT).show()
            return
        }

        val parent = currentPath?.parentFile
        if (parent != null && parent.exists()) {
            leavingPath = currentPath
            destinationPath = parent
            canPerformGoUp = true
        }
    } else if (currentSafUri != null) {
        if (safStack.isNotEmpty()) {
            leavingUri = currentSafUri
            destinationUri = safStack.last()
            canPerformGoUp = true
        } else {
            Toast.makeText(context, context.getString(R.string.msg_already_at_saf_root), Toast.LENGTH_SHORT).show()
        }
    }

    if (canPerformGoUp) {
        navigateTo(
            newPath = destinationPath,
            newUri = destinationUri,
            archiveFile = nextArchiveFile,
            archiveUri = nextArchiveUri,
            archivePath = nextArchivePath
        )
        if (leavingUri != null && destinationUri != null && safStack.isNotEmpty()) {
            safStack.removeAt(safStack.size - 1)
        }
        focusItemInList(leavingPath, leavingUri)
    }
}

fun FileExplorerState.focusItemInList(path: File?, uri: Uri?) {
    if (path == null && uri == null) return
    pendingFocusPath = path
    pendingFocusUri = uri
}
