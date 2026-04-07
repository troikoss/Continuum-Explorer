package com.troikoss.continuum_explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.R
import kotlinx.coroutines.launch

/**
 * A TabBar component styled to match a modern browser-like interface.
 */
@Composable
fun TabBar(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    appState: FileExplorerState
) {

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val delta = event.changes.first().scrollDelta
                                    coroutineScope.launch {
                                        // Map vertical scroll (delta.y) to horizontal scroll
                                        scrollState.scrollBy(delta.y * 60f)
                                    }
                                }
                            }
                        }
                    },
                verticalAlignment = Alignment.Bottom
            ) {
                tabs.forEachIndexed { index, title ->
                    val universalFile: UniversalFile? = when {
                        // If we are inside an archive file (local)
                        appState.currentArchiveFile != null -> {
                            appState.currentArchiveFile?.let { file ->
                                UniversalFile(
                                    name = file.name,
                                    isDirectory = false, // It's a file acting like a folder
                                    lastModified = file.lastModified(),
                                    length = file.length(),
                                    fileRef = file,
                                    isArchiveEntry = true // Tell the system this is an archive!
                                )
                            }
                        }
                        // If we are in a regular folder
                        appState.currentPath != null -> {
                            appState.currentPath?.let { file ->
                                UniversalFile(
                                    name = file.name,
                                    isDirectory = file.isDirectory,
                                    lastModified = file.lastModified(),
                                    length = file.length(),
                                    fileRef = file,
                                    absolutePath = file.absolutePath
                                )
                            }
                        }
                        else -> null
                    }

                    // 2. Get the icon (with a fallback)
                    val tabIcon = if (universalFile != null) {
                        IconHelper.getIconForItem(universalFile)
                    } else {
                        Icons.Default.Folder // Default icon
                    }

                    TabItem(
                        text = title,
                        icon = tabIcon,
                        selected = (selectedTabIndex == index),
                        onClick = { onTabSelected(index) },
                        onClose = { onCloseTab(index) }
                    )
                }

                IconButton(
                    onClick = onAddTab,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(32.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.new_tab),
                        tint = Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF9AA0A6)

    Box(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .height(38.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val event = awaitPointerEvent()
                    val isMouse = event.changes.any { it.type == PointerType.Mouse }
                    val isTertiary = event.buttons.isTertiaryPressed

                    if (isMouse && event.type == PointerEventType.Press && isTertiary) {
                        onClose()
                    }
                }
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = textColor
            )

            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                modifier = Modifier
                    .size(14.dp)

                    .clickable { onClose() },
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )


        }
    }
}
