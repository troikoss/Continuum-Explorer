package com.example.continuum_explorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.continuum_explorer.utils.IconHelper
import com.example.continuum_explorer.utils.getFileType
import com.example.continuum_explorer.utils.getImageResolution
import com.example.continuum_explorer.utils.getMediaDuration
import com.example.continuum_explorer.utils.getVideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.text.style.TextOverflow


@Composable
fun DetailsPane (
    appState: FileExplorerState
) {
    val context = LocalContext.current
    val selectedItems = appState.selectionManager.selectedItems
    val selectionCount = selectedItems.size
    val totalFiles = appState.files.size
    val currentPath = appState.currentUniversalPath

    val fileType = if (selectionCount >= 1) getFileType(selectedItems.first()) else null

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.0F)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                if (selectedItems.isEmpty()) {
                    if (currentPath != null) {
                        Icon(
                            imageVector = IconHelper.getIconForItem(currentPath),
                            contentDescription = null,
                            modifier = Modifier.size(100.dp)
                        )
                    } else {
                        Text("Invalid Path")
                    }
                } else {
                    if (IconHelper.isMimeTypePreviewable(selectedItems.first())) {
                        IconHelper.FileThumbnail(selectedItems.first())
                    } else {
                        Icon(
                            imageVector = IconHelper.getIconForItem(selectedItems.first()),
                            contentDescription = null,
                            modifier = Modifier.size(100.dp)
                        )
                    }
                }
            }

            val infoLabel = when {
                selectionCount == 1 -> selectedItems.first().name
                selectionCount > 1 -> "$selectionCount items selected"
                else -> if (totalFiles == 1) "1 item" else "$totalFiles items"
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = infoLabel, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(4.dp))
                if (selectionCount == 1) {

                    Text(
                        text = getFileType(selectedItems.first()),
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val date = appState.formatDate(selectedItems.first().lastModified)
                    Text(
                        text = "Date Modified: $date",
                        fontSize = 14.sp
                    )
                    if (!selectedItems.first().isDirectory) {
                        val size = appState.formatSize(selectedItems.first().length)
                        Text(
                            text = "Size: $size",
                            fontSize = 14.sp
                        )
                    }
                    // 1. Create state variables to hold the text. They start as null.
                    var resolution by remember { mutableStateOf<String?>(null) }
                    var duration by remember { mutableStateOf<String?>(null) }

                    // 2. Use LaunchedEffect to run the heavy work in the background.
                    // The "key1 = selectedFile" tells Compose to run this block again if the user selects a different file.
                    LaunchedEffect(key1 = selectedItems.first()) {
                        // Reset them to null immediately when a new file is selected
                        resolution = null
                        duration = null

                        // Switch to the IO dispatcher (Background Thread) for file reading
                        withContext(Dispatchers.IO) {
                            if (fileType == "Video" || fileType == "Audio") {
                                duration =
                                    getMediaDuration(context, selectedItems.first())
                            }

                            when (fileType) {
                                "Video" -> resolution =
                                    getVideoResolution(context, selectedItems.first())

                                "Image" -> resolution =
                                    getImageResolution(context, selectedItems.first())
                            }
                        }
                    }

                    if (resolution != null) {
                        Text(
                            text = "Resolution: $resolution",
                            fontSize = 14.sp
                        )
                    }

                    if (duration != null) {
                        Text(
                            text = "Duration: $duration",
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

    val fileType = if (selectionCount >= 1) getFileType(selectedItems.first()) else null

    Row(modifier = Modifier.height(80.dp)  ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 24.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            if (selectedItems.isEmpty()) {
                if (currentPath != null) {
                    Icon(
                        imageVector = IconHelper.getIconForItem(currentPath),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp)
                    )
                } else {
                    Text("Invalid Path")
                }
            } else {
                if (IconHelper.isMimeTypePreviewable(selectedItems.first())) {
                    IconHelper.FileThumbnail(selectedItems.first(), modifier = Modifier.padding(8.dp))
                } else {
                    Icon(
                        imageVector = IconHelper.getIconForItem(selectedItems.first()),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }
        }
        val infoLabel = when {
            selectionCount == 1 -> selectedItems.first().name
            selectionCount > 1 -> "$selectionCount items selected"
            else -> if (totalFiles == 1) "1 item" else "$totalFiles items"
        }

        Column (
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
                    text = getFileType(selectedItems.first()),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (selectionCount == 1) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {


                val date = appState.formatDate(selectedItems.first().lastModified)

                Text(
                    text = "Date Modified: $date",
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )

                if (!selectedItems.first().isDirectory) {
                    val size = appState.formatSize(selectedItems.first().length)
                    Text(
                        text = "Size: $size",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                // 1. Create state variables to hold the text. They start as null.
                var resolution by remember { mutableStateOf<String?>(null) }
                var duration by remember { mutableStateOf<String?>(null) }

                // 2. Use LaunchedEffect to run the heavy work in the background.
                // The "key1 = selectedFile" tells Compose to run this block again if the user selects a different file.
                LaunchedEffect(key1 = selectedItems.first()) {
                    // Reset them to null immediately when a new file is selected
                    resolution = null
                    duration = null

                    // Switch to the IO dispatcher (Background Thread) for file reading
                    withContext(Dispatchers.IO) {
                        if (fileType == "Video" || fileType == "Audio") {
                            duration =
                                getMediaDuration(context, selectedItems.first())
                        }

                        when (fileType) {
                            "Video" -> resolution =
                                getVideoResolution(context, selectedItems.first())

                            "Image" -> resolution =
                                getImageResolution(context, selectedItems.first())
                        }
                    }
                }

                if (resolution != null) {
                    Text(
                        text = "Resolution: $resolution",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)

                    )
                }

                if (duration != null) {
                    Text(
                        text = "Duration: $duration",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }


        }
    }
}