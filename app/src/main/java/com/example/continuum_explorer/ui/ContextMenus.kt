package com.example.continuum_explorer.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.R
import com.example.continuum_explorer.model.FileColumnType
import com.example.continuum_explorer.model.ScreenSize
import com.example.continuum_explorer.model.ViewMode
import com.example.continuum_explorer.utils.ZipUtils
import com.example.continuum_explorer.utils.openWith
import com.example.continuum_explorer.utils.shareFiles
import com.example.continuum_explorer.utils.IconHelper

/**
 * Context menu shown when right-clicking or long-pressing a specific file or folder.
 */
@Composable
fun ItemContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    appState: FileExplorerState
) {
    val context = LocalContext.current
    val selectionManager = appState.selectionManager
    val isInRecycleBin = appState.isInRecycleBin

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (isInRecycleBin) {
            DropdownMenuItem(
                text = { Text("Restore") },
                onClick = {
                    appState.restoreSelection()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Restore, null) }
            )

            DropdownMenuItem(
                text = { Text("Delete Permanently") },
                onClick = {
                    appState.deleteSelection(forcePermanent = true)
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Delete, null) }
            )

            DropdownMenuItem(
                text = { Text("Properties") },
                onClick = {
                    onDismiss()
                    appState.showProperties()
                },
                leadingIcon = { Icon(Icons.Default.Info, null) }
            )
        } else {
            val selectedItems = selectionManager.selectedItems.toList()
            val onlyOneSelected = selectedItems.size == 1
            val hasDirectories = selectedItems.any { it.isDirectory }
            val hasArchive = selectedItems.any { ZipUtils.isArchive(it) }

            if (onlyOneSelected) {
                val item = selectedItems.first()
                DropdownMenuItem(
                    text = { Text("Open") },
                    onClick = {
                        onDismiss()
                        appState.open(item)
                    },
                    leadingIcon = { Icon(IconHelper.getIconForItem(item), null) },
                    trailingIcon = { Icon(
                        Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = null,
                        modifier = Modifier
                            .size(12.dp)
                        )
                    }
                )

                if (!hasDirectories) {
                    DropdownMenuItem(
                        text = { Text("Open With...") },
                        onClick = {
                            onDismiss()
                            openWith(context, selectedItems.first())
                        },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
                    )
                }
                
                HorizontalDivider()
            }

            if (hasDirectories || hasArchive) {
                DropdownMenuItem(
                    text = { Text("Open in New Tab") },
                    onClick = {
                        onDismiss()
                        appState.openInNewTab(selectedItems)
                    },
                    leadingIcon = { Icon(Icons.Default.Tab, null) },
                    trailingIcon = {
                        Text(
                            text = "MMB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                DropdownMenuItem(
                    text = { Text("Open in New Window") },
                    onClick = {
                        onDismiss()
                        appState.openInNewWindow(selectedItems)
                    },
                    leadingIcon = { Icon(Icons.Default.Splitscreen, null) },
                    trailingIcon = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.shift),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "+ MMB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                HorizontalDivider()
            }
            
            if (onlyOneSelected && selectedItems.first().isDirectory && selectedItems.first().fileRef != null) {
                val path = selectedItems.first().fileRef!!.absolutePath
                val isFav = appState.appConfigs.isFavorite(path)
                
                DropdownMenuItem(
                    text = { Text(if (isFav) "Remove from Favorites" else "Add to Favorites") },
                    onClick = {
                        if (isFav) appState.appConfigs.removeFavorite(path)
                        else appState.appConfigs.addFavorite(path)
                        onDismiss()
                    },
                    leadingIcon = { Icon(if (isFav) Icons.Default.StarOutline else Icons.Default.Star, null) }
                )
                HorizontalDivider()
            }

            if (hasArchive) {
                DropdownMenuItem(
                    text = { Text("Extract...") },
                    onClick = {
                        onDismiss()
                        appState.extractSelection()
                    },
                    leadingIcon = { Icon(Icons.Default.Unarchive, null) }
                )
            }

            DropdownMenuItem(
                text = { Text("Compress...") },
                onClick = {
                    onDismiss()
                    appState.compressSelection()
                },
                leadingIcon = { Icon(Icons.Default.Archive, null) }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Cut") },
                onClick = {
                    appState.cutSelection()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.ContentCut, null) },
                trailingIcon = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.control),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "+ X",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            DropdownMenuItem(
                text = { Text("Copy") },
                onClick = {
                    appState.copySelection()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.CopyAll, null) },
                trailingIcon = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.control),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "+ C",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            DropdownMenuItem(
                text = { Text("Paste") },
                onClick = {
                    appState.paste()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                trailingIcon = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.control),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "+ V",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    appState.renameSelection()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                trailingIcon = {
                    Text(
                        text = "F2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            if ( !hasDirectories ) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        shareFiles(context, selectedItems)
                        onDismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.Share, null) }
                )
            }

            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    appState.deleteSelection()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                trailingIcon = {
                    Text(
                        text = "DEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            DropdownMenuItem(
                text = { Text("Properties") },
                onClick = {
                    onDismiss()
                    appState.showProperties()
                },
                leadingIcon = { Icon(Icons.Default.Info, null) }
            )
        }
    }
}

/**
 * Context menu shown when right-clicking the empty background area of the file explorer.
 */
@Composable
fun BackgroundContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    appState: FileExplorerState
) {
    var currentScreen by remember { mutableStateOf("MAIN") }
    val isInRecycleBin = appState.isInRecycleBin

    LaunchedEffect(expanded) {
        if (!expanded) {
            currentScreen = "MAIN"
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        when (currentScreen) {
            "MAIN" -> {
                if (!isInRecycleBin) {
                    DropdownMenuItem(
                        text = { Text("New") },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        onClick = { currentScreen = "NEW" }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Sort") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    onClick = { currentScreen = "SORT" }
                )

                DropdownMenuItem(
                    text = { Text("View") },
                    leadingIcon = { Icon(Icons.Default.ViewModule, null) },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    onClick = { currentScreen = "VIEW" }
                )

                HorizontalDivider()

                if (isInRecycleBin) {
                    DropdownMenuItem(
                        text = { Text("Empty Recycle Bin") },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, null) },
                        onClick = { appState.emptyRecycleBin() }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Refresh") },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = {
                        appState.refresh()
                        onDismiss()
                    },
                    trailingIcon = {
                        Text(
                            text = "F5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                if (!isInRecycleBin) {
                    DropdownMenuItem(
                        text = { Text("Paste") },
                        leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                        onClick = {
                            appState.paste()
                            onDismiss()
                        },
                        trailingIcon = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.control),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "+ V",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }

            "NEW" -> {
                DropdownMenuItem(
                    text = { Text("Back", color = MaterialTheme.colorScheme.primary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { currentScreen = "MAIN" }
                )
                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("Folder") },
                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                    onClick = {
                        appState.createNewFolder()
                        onDismiss()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Text Document") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                    onClick = {
                        appState.createNewFile()
                        onDismiss()
                    }
                )
            }

            "SORT" -> {
                DropdownMenuItem(
                    text = { Text("Back", color = MaterialTheme.colorScheme.primary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { currentScreen = "MAIN" }
                )
                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("By Name") },
                    leadingIcon = { Icon(Icons.Default.TextFormat, null) },
                    trailingIcon = { appState.folderConfigs.SortArrow(FileColumnType.NAME) },
                    onClick = {
                        appState.folderConfigs.toggleSort(FileColumnType.NAME, appState.getCurrentStorageKey()) { appState.refresh() }
                        onDismiss()
                    }
                )
                DropdownMenuItem(
                    text = { Text("By Date") },
                    leadingIcon = { Icon(Icons.Default.DateRange, null) },
                    trailingIcon = { appState.folderConfigs.SortArrow(FileColumnType.DATE) },
                    onClick = {
                        appState.folderConfigs.toggleSort(FileColumnType.DATE, appState.getCurrentStorageKey()) { appState.refresh() }
                        onDismiss()
                    }
                )
                DropdownMenuItem(
                    text = { Text("By Size") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    trailingIcon = { appState.folderConfigs.SortArrow(FileColumnType.SIZE) },
                    onClick = {
                        appState.folderConfigs.toggleSort(FileColumnType.SIZE, appState.getCurrentStorageKey()) { appState.refresh() }
                        onDismiss()
                    }
                )
            }

            "VIEW" -> {
                DropdownMenuItem(
                    text = { Text("Back", color = MaterialTheme.colorScheme.primary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { currentScreen = "MAIN" }
                )
                HorizontalDivider()

                if (appState.getScreenSize() != ScreenSize.SMALL) {
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ListAlt, null) },
                        trailingIcon = { if (appState.activeViewMode == ViewMode.DETAILS) { Icon(Icons.Default.Done, null) }},
                        onClick = {
                            appState.folderConfigs.updateViewMode(ViewMode.DETAILS, appState.getCurrentStorageKey())
                            onDismiss()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Grid") },
                    leadingIcon = { Icon(Icons.Default.ViewModule, null) },
                    trailingIcon = { if (appState.activeViewMode == ViewMode.GRID) { Icon(Icons.Default.Done, null) }},
                    onClick = {
                        appState.folderConfigs.updateViewMode(ViewMode.GRID, appState.getCurrentStorageKey())
                        onDismiss()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Content") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    trailingIcon = { if (appState.activeViewMode == ViewMode.CONTENT) { Icon(Icons.Default.Done, null) }},
                    onClick = {
                        appState.folderConfigs.updateViewMode(ViewMode.CONTENT, appState.getCurrentStorageKey())
                        onDismiss()
                    }
                )
            }
        }
    }
}
