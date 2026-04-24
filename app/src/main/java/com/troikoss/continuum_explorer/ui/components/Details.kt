package com.troikoss.continuum_explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.utils.IconHelper.FileThumbnail
import com.troikoss.continuum_explorer.utils.IconHelper.FolderPreview
import com.troikoss.continuum_explorer.utils.getFileType
import com.troikoss.continuum_explorer.utils.getImageResolution
import com.troikoss.continuum_explorer.utils.getMediaDuration
import com.troikoss.continuum_explorer.utils.getVideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun rememberMediaMetadata(
    file: UniversalFile,
    fileType: String?,
    context: android.content.Context
): Pair<String?, String?> {
    var resolution by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        resolution = null
        duration = null
        withContext(Dispatchers.IO) {
            if (fileType == "Video" || fileType == "Audio") {
                duration = getMediaDuration(context, file)
            }
            when (fileType) {
                "Video" -> resolution = getVideoResolution(context, file)
                "Image" -> resolution = getImageResolution(context, file)
            }
        }
    }

    return Pair(resolution, duration)
}

@Composable
fun DetailsPane(
    appState: FileExplorerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedItems = appState.selectionManager.selectedItems
    val selectionCount = selectedItems.size
    val totalFiles = appState.files.size
    val currentPath = appState.currentUniversalPath

    val fileType = if (selectionCount >= 1) getFileType(selectedItems.first(), context) else null

    val detailsShape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(detailsShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.0F)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (currentPath != null) {
                    val file = if (selectedItems.isEmpty()) currentPath else selectedItems.first()
                    Box {
                        FileThumbnail(
                            file = file,
                            isDetailView = true,
                            iconSize = 100.dp
                        )
                        FolderPreview(
                            folder = file,
                            thumbSize = 50.dp,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                } else {
                    Text(stringResource(R.string.msg_invalid_path))
                }
            }

            val infoLabel = when {
                selectionCount == 1 -> selectedItems.first().name
                selectionCount > 1 -> stringResource(R.string.prop_selected_count, selectionCount)
                else -> if (totalFiles == 1) stringResource(R.string.details_item_count_singular)
                        else stringResource(R.string.details_item_count_plural, totalFiles)
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = infoLabel, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(4.dp))
                if (selectionCount == 1) {
                    val file = selectedItems.first()
                    Text(text = getFileType(file, context), fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.details_date_modified, appState.formatDate(file.lastModified)),
                        fontSize = 14.sp
                    )
                    val meta = appState.recycleBinMetadata[file.name]
                    if (meta?.deletedAt != null) {
                        Text(
                            text = stringResource(R.string.details_date_deleted, appState.formatDate(meta.deletedAt)),
                            fontSize = 14.sp
                        )
                    }
                    if (meta?.deletedFrom != null) {
                        Text(
                            text = stringResource(R.string.details_deleted_from, meta.deletedFrom),
                            fontSize = 14.sp
                        )
                    }
                    if (!file.isDirectory) {
                        Text(
                            text = stringResource(R.string.details_size, appState.formatSize(file.length)),
                            fontSize = 14.sp
                        )
                    } else {
                        val childCount = remember(file) { file.fileRef?.listFiles()?.size }
                        if (childCount != null) {
                            Text(
                                text = if (childCount == 1) stringResource(R.string.details_item_count_singular)
                                       else stringResource(R.string.details_item_count_plural, childCount),
                                fontSize = 14.sp
                            )
                        }
                    }

                    val (resolution, duration) = rememberMediaMetadata(file, fileType, context)

                    if (resolution != null) {
                        Text(
                            text = stringResource(R.string.details_resolution, resolution),
                            fontSize = 14.sp
                        )
                    }
                    if (duration != null) {
                        Text(
                            text = stringResource(R.string.details_duration, duration),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsBar(
    appState: FileExplorerState
) {
    val context = LocalContext.current
    val selectedItems = appState.selectionManager.selectedItems
    val currentPath = appState.currentUniversalPath
    val selectionCount = selectedItems.size
    val totalFiles = appState.files.size

    val fileType = if (selectionCount >= 1) getFileType(selectedItems.first(), context) else null

    Row(modifier = Modifier.height(80.dp)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 180.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (currentPath != null) {
                val file = if (selectedItems.isEmpty()) currentPath else selectedItems.first()
                Box {
                    FileThumbnail(
                        file = file,
                        isDetailView = true,
                        modifier = Modifier.padding(8.dp),
                        iconSize = 50.dp
                    )
                    FolderPreview(
                        folder = file,
                        thumbSize = 25.dp,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            } else {
                Text(stringResource(R.string.msg_invalid_path))
            }
        }

        val infoLabel = when {
            selectionCount == 1 -> selectedItems.first().name
            selectionCount > 1 -> stringResource(R.string.prop_selected_count, selectionCount)
            else -> if (totalFiles == 1) stringResource(R.string.details_item_count_singular)
                    else stringResource(R.string.details_item_count_plural, totalFiles)
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 250.dp)
                .padding(12.dp)
        ) {
            Text(
                text = infoLabel,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selectionCount == 1) {
                Text(
                    text = getFileType(selectedItems.first(), context),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (selectionCount == 1) {
            val file = selectedItems.first()

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.details_date_modified, appState.formatDate(file.lastModified)),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                val meta = appState.recycleBinMetadata[file.name]
                if (meta?.deletedAt != null) {
                    Text(
                        text = stringResource(R.string.details_date_deleted, appState.formatDate(meta.deletedAt)),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (meta?.deletedFrom != null) {
                    Text(
                        text = stringResource(R.string.details_deleted_from, meta.deletedFrom),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!file.isDirectory) {
                    Text(
                        text = stringResource(R.string.details_size, appState.formatSize(file.length)),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val childCount = remember(file) { file.fileRef?.listFiles()?.size }
                    if (childCount != null) {
                        Text(
                            text = if (childCount == 1) stringResource(R.string.details_item_count_singular)
                                   else stringResource(R.string.details_item_count_plural, childCount),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                val (resolution, duration) = rememberMediaMetadata(file, fileType, context)

                if (resolution != null) {
                    Text(
                        text = stringResource(R.string.details_resolution, resolution),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (duration != null) {
                    Text(
                        text = stringResource(R.string.details_duration, duration),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}