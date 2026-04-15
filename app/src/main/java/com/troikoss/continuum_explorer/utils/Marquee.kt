package com.troikoss.continuum_explorer.utils

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * State holder for 2D Marquee selections.
 */
class Marquee {
    var startRow by mutableIntStateOf(-1)
        private set
    var startCol by mutableIntStateOf(-1)
        private set
    var currentRow by mutableIntStateOf(-1)
        private set
    var currentCol by mutableIntStateOf(-1)
        private set

    /**
     * Resets the logical grid boundaries when a new drag starts.
     */
    fun reset() {
        startRow = -1
        startCol = -1
        currentRow = -1
        currentCol = -1
    }

    /**
     * Calculates the logical grid bounds and returns the selected files.
     */
    fun updateSelection(
        start: Offset?,
        end: Offset?,
        itemPositions: Map<UniversalFile, Rect>,
        allFiles: List<UniversalFile>,
        columnCount: Int,
        onSelectionChange: (Set<UniversalFile>) -> Unit
    ) {
        if (start == null || end == null) return

        // 1. Create the physical bounding box
        val left = minOf(start.x, end.x)
        val right = maxOf(start.x, end.x)
        val top = minOf(start.y, end.y)
        val bottom = maxOf(start.y, end.y)
        val marqueeRect = Rect(left, top, right, bottom)

        // 2. Lock the start anchor safely (bypasses padding issues)
        if (startRow == -1) {
            val startItem = itemPositions.minByOrNull { (_, rect) ->
                val dx = rect.center.x - start.x
                val dy = rect.center.y - start.y
                (dx * dx) + (dy * dy)
            }?.key

            val startIndex = if (startItem != null) allFiles.indexOf(startItem) else -1

            if (startIndex != -1) {
                startRow = startIndex / columnCount
                startCol = startIndex % columnCount
            }
        }

        // 3. Find the current leading edge of the drag
        val intersectingIndices = itemPositions.entries
            .filter { marqueeRect.overlaps(it.value) }
            .map { allFiles.indexOf(it.key) }
            .filter { it != -1 }

        if (intersectingIndices.isNotEmpty()) {
            val draggingDown = start.y <= end.y
            val draggingRight = start.x <= end.x

            currentRow = if (draggingDown) {
                intersectingIndices.maxOf { it / columnCount }
            } else {
                intersectingIndices.minOf { it / columnCount }
            }

            currentCol = if (draggingRight) {
                intersectingIndices.maxOf { it % columnCount }
            } else {
                intersectingIndices.minOf { it % columnCount }
            }
        } else {
            currentRow = -1
            currentCol = -1
            onSelectionChange(emptySet())
        }

        // 4. Apply the flawless 2D conversion math
        if (startRow != -1 && currentRow != -1) {
            val finalMinRow = kotlin.comparisons.minOf(startRow, currentRow)
            val finalMaxRow = kotlin.comparisons.maxOf(startRow, currentRow)
            val finalMinCol = kotlin.comparisons.minOf(startCol, currentCol)
            val finalMaxCol = kotlin.comparisons.maxOf(startCol, currentCol)

            val selectedFiles = mutableSetOf<UniversalFile>()
            for (i in allFiles.indices) {
                val r = i / columnCount
                val c = i % columnCount

                if (r in finalMinRow..finalMaxRow && c in finalMinCol..finalMaxCol) {
                    selectedFiles.add(allFiles[i])
                }
            }

            // Pass the perfectly calculated list back to the UI
            onSelectionChange(selectedFiles)
        }
    }
}

/**
 * Remembers the Marquee across recompositions.
 */
@Composable
fun rememberMarquee(): Marquee {
    return remember { Marquee() }
}

/**
 * Automatically scrolls the file list if the user drags the marquee selection near the top or bottom edges.
 */
@Composable
fun MarqueeAutoScroller(
    dragStart: Offset?,
    dragEnd: Offset?,
    containerCoordinates: LayoutCoordinates?,
    gridState: LazyGridState,
    onDragStartChange: (Offset?) -> Unit,
    onSelectionChange: (Offset?, Offset?) -> Unit
) {
    val density = LocalDensity.current
    val currentDragStart by rememberUpdatedState(dragStart)
    val currentDragEnd by rememberUpdatedState(dragEnd)

    LaunchedEffect(dragStart != null, dragEnd) {
        if (currentDragStart != null && currentDragEnd != null) {
            while (true) {
                val container = containerCoordinates ?: break
                val containerHeight = container.size.height.toFloat()
                if (containerHeight == 0f) break

                val scrollThreshold = with(density) { 60.dp.toPx() }
                val maxScrollSpeed = with(density) { 20.dp.toPx() }

                var scrollDelta = 0f
                val endPos = currentDragEnd!!

                // Scroll up if mouse is near the top edge
                if (endPos.y < scrollThreshold) {
                    val ratio = (scrollThreshold - endPos.y) / scrollThreshold
                    scrollDelta = -maxScrollSpeed * ratio.coerceIn(0f, 1f)
                }
                // Scroll down if mouse is near the bottom edge
                else if (endPos.y > containerHeight - scrollThreshold) {
                    val ratio = (endPos.y - (containerHeight - scrollThreshold)) / scrollThreshold
                    scrollDelta = maxScrollSpeed * ratio.coerceIn(0f, 1f)
                }

                if (scrollDelta != 0f) {
                    val consumed = gridState.scrollBy(scrollDelta)
                    if (consumed != 0f) {
                        // When the list scrolls, we must adjust the drag start point to keep it pinned to the same file items
                        val newStart = currentDragStart?.let { it.copy(y = it.y - consumed) }
                        onDragStartChange(newStart)
                        onSelectionChange(newStart, endPos)
                    } else {
                        break
                    }
                } else {
                    break
                }
                delay(10) // Smooth scroll loop
            }
        }
    }
}

/**
 * Renders the semi-transparent blue selection rectangle on top of the file explorer.
 */
@Composable
fun MarqueeRenderer(
    dragStart: Offset?,
    dragEnd: Offset?
) {
    if (dragStart != null && dragEnd != null) {
        // Canvas takes up the exact size of your File Explorer box
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = minOf(dragStart.x, dragEnd.x)
            val top = minOf(dragStart.y, dragEnd.y)
            val right = maxOf(dragStart.x, dragEnd.x)
            val bottom = maxOf(dragStart.y, dragEnd.y)

            val width = right - left
            val height = bottom - top

            if (width > 0f && height > 0f) {
                val rectTopLeft = Offset(left, top)
                val rectSize = Size(width, height)

                // 1. Paint the semi-transparent blue fill
                drawRect(
                    color = Color(0x332196F3),
                    topLeft = rectTopLeft,
                    size = rectSize
                )

                // 2. Paint the solid blue border
                drawRect(
                    color = Color(0xFF2196F3),
                    topLeft = rectTopLeft,
                    size = rectSize,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}