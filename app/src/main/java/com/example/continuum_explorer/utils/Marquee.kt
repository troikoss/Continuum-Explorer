package com.example.continuum_explorer.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.delay

/**
 * Calculates which files are inside the drag-selection rectangle and updates the selection manager.
 */
fun updateMarqueeSelection(
    start: Offset?,
    end: Offset?,
    itemPositions: Map<UniversalFile, Rect>,
    selectionManager: SelectionManager
) {
    if (start != null && end != null) {
        val selectionRect = Rect(
            left = minOf(start.x, end.x),
            top = minOf(start.y, end.y),
            right = maxOf(start.x, end.x),
            bottom = maxOf(start.y, end.y)
        )
        // Find all file items whose bounding box overlaps with the selection rectangle
        val touchedFiles = itemPositions.filter { entry ->
            selectionRect.overlaps(entry.value)
        }.keys
        selectionManager.updateSelectionFromDrag(touchedFiles)
    }
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
    dragEnd: Offset?,
    containerCoordinates: LayoutCoordinates?
) {
    val density = LocalDensity.current
    if (dragStart != null && dragEnd != null) {
        val fullRect = Rect(
            left = minOf(dragStart.x, dragEnd.x),
            top = minOf(dragStart.y, dragEnd.y),
            right = maxOf(dragStart.x, dragEnd.x),
            bottom = maxOf(dragStart.y, dragEnd.y)
        )

        val containerSize = containerCoordinates?.size
        if (containerSize != null) {
            // Clip the selection rectangle to the visible area of the container
            val containerRect = Rect(0f, 0f, containerSize.width.toFloat(), containerSize.height.toFloat())
            val visibleRect = fullRect.intersect(containerRect)

            if (visibleRect.width > 0 && visibleRect.height > 0) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(visibleRect.left.toInt(), visibleRect.top.toInt()) }
                        .size(
                            width = with(density) { visibleRect.width.toDp() },
                            height = with(density) { visibleRect.height.toDp() }
                        )
                        .background(Color(0x332196F3)) // Semi-transparent blue
                        .border(1.dp, Color(0xFF2196F3))
                )
            }
        }
    }
}