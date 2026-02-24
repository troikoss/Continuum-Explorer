package com.example.continuum_explorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.continuum_explorer.model.UniversalFile
import com.example.continuum_explorer.utils.IconHelper

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
                    .horizontalScroll(rememberScrollState()),
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
                        .padding(top = 4.dp, start = 4.dp, end = 4.dp )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Tab",
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

    val density = LocalDensity.current
    val customShape = remember {
        BrowserTabShape(with(density) { 8.dp.toPx() })
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .clip(customShape)
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
                contentDescription = "Close Tab",
                modifier = Modifier
                    .size(14.dp)

                    .clickable { onClose() },
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )


        }
    }
}

class BrowserTabShape(private val cornerRadius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val r = cornerRadius

            // Start at bottom left
            moveTo(0f, h)
            // Outer flare left
            quadraticTo(r * 0.5f, h, r * 0.5f, h - r * 0.5f)
            // Up to top curve
            lineTo(r * 0.5f, r)
            quadraticTo(r * 0.5f, 0f, r * 1.5f, 0f)
            // Across
            lineTo(w - r * 1.5f, 0f)
            // Top right curve
            quadraticTo(w - r * 0.5f, 0f, w - r * 0.5f, r)
            // Down
            lineTo(w - r * 0.5f, h - r * 0.5f)
            // Outer flare right
            quadraticTo(w - r * 0.5f, h, w, h)
            close()
        }
        return Outline.Generic(path)
    }
}
