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
    var debugMarqueeRect: Rect? = null
        private set
    var debugIntersectedRects: List<Rect> = emptyList()
        private set

    /**
     * Resets the logical grid boundaries when a new drag starts.
     */
    fun reset() {
        debugMarqueeRect = null
        debugIntersectedRects = emptyList()
    }

    /**
     * Calculates the logical grid bounds and returns the selected files.
     */
    fun updateSelection(
        start: Offset?,
        end: Offset?,
        gridState: LazyGridState,
        allFiles: List<UniversalFile>,
        columnCount: Int,
        xOffset: Float,
        yOffset: Float,
        onSelectionChange: (Set<UniversalFile>) -> Unit
    ) {
        if (start == null || end == null) return

        // 1. Shift drag coords into inner box coords to match the layout
        val adjStartX = start.x
        val adjStartY = start.y - yOffset
        val adjEndX = end.x
        val adjEndY = end.y - yOffset

        val left = minOf(adjStartX, adjEndX) - 1f
        val right = maxOf(adjStartX, adjEndX) + 1f
        val top = minOf(adjStartY, adjEndY) - 1f
        val bottom = maxOf(adjStartY, adjEndY) + 1f
        val marqueeRect = Rect(left, top, right, bottom)
        debugMarqueeRect = marqueeRect

        val selectedFiles = mutableSetOf<UniversalFile>()
        val intersectedRects = mutableListOf<Rect>()

        val visibleItems = gridState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            debugIntersectedRects = intersectedRects
            onSelectionChange(selectedFiles)
            return
        }

        var referenceW = 0f
        var referenceH = 0f
        for (item in visibleItems) {
            if (item.size.width > referenceW) referenceW = item.size.width.toFloat()
            if (item.size.height > referenceH) referenceH = item.size.height.toFloat()
        }

        // Base the virtual grid strictly on live internal layout info to guarantee 0-lag synchronization
        val anchorItem = visibleItems.firstOrNull {
            it.size.width >= referenceW * 0.9f && it.size.height >= referenceH * 0.9f
        } ?: visibleItems.firstOrNull()

        if (anchorItem != null) {
            val anchorIndex = anchorItem.index

            // Exact layout top-left relative to the MarqueeRenderer canvas
            val anchorRectLeft = anchorItem.offset.x.toFloat() + xOffset
            val anchorRectTop = anchorItem.offset.y.toFloat()

            val anchorR = anchorIndex / columnCount
            val anchorC = anchorIndex % columnCount

            var stepX = referenceW
            var stepY = referenceH

            val rawAnchorX = anchorItem.offset.x.toFloat()

            // Determine true horizontal and vertical step sizes
            for (item in visibleItems) {
                val index = item.index
                val r = index / columnCount
                val c = index % columnCount

                if (c != anchorC) {
                    val calcX = kotlin.math.abs((item.offset.x.toFloat() - rawAnchorX) / (c - anchorC))
                    if (calcX > stepX) stepX = calcX
                }
                if (r != anchorR) {
                    val calcY = kotlin.math.abs((item.offset.y.toFloat() - anchorRectTop) / (r - anchorR))
                    if (calcY > stepY) stepY = calcY
                }
            }

            for (i in allFiles.indices) {
                val file = allFiles[i]

                val visibleItem = visibleItems.firstOrNull { it.index == i }
                val isFullyVisible = visibleItem != null && visibleItem.size.width >= referenceW * 0.9f && visibleItem.size.height >= referenceH * 0.9f

                // If physically visible natively on screen, use the completely exact runtime layout bounds
                // If scrolled off-screen, mathematically extrapolate using the guaranteed safe steps!
                val rect = if (isFullyVisible) {
                    val vLeft = visibleItem!!.offset.x.toFloat() + xOffset
                    val vTop = visibleItem.offset.y.toFloat()
                    Rect(vLeft, vTop, vLeft + visibleItem.size.width, vTop + visibleItem.size.height)
                } else {
                    val r = i / columnCount
                    val c = i % columnCount
                    val estLeft = anchorRectLeft + (c - anchorC) * stepX
                    val estTop = anchorRectTop + (r - anchorR) * stepY
                    Rect(estLeft, estTop, estLeft + referenceW, estTop + referenceH)
                }

                // Deflate physical target to avoid invisible padding collisions
                // For off-screen mathematically predicted items, inflate slightly (-2f) instead of deflating
                // to completely eliminate floating-point precision misses for small items during autoscroll!
                val insetX = if (isFullyVisible) minOf(rect.width * 0.05f, 2f) else -2f
                val insetY = if (isFullyVisible) minOf(rect.height * 0.05f, 2f) else -2f
                val coreRect = Rect(
                    left = rect.left + insetX,
                    top = rect.top + insetY,
                    right = rect.right - insetX,
                    bottom = rect.bottom - insetY
                )

                if (marqueeRect.overlaps(coreRect)) {
                    selectedFiles.add(file)
                    intersectedRects.add(rect)
                }
            }
        }

        debugIntersectedRects = intersectedRects
        onSelectionChange(selectedFiles)
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
    dragEnd: Offset?,
    marquee: Marquee? = null
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

            if (marquee != null) {
                // Draw actual bounds of all intersecting files in translucent red
                marquee.debugIntersectedRects.forEach { rect ->
                    drawRect(
                        color = Color(0x33FF0000), // Red overlay
                        topLeft = rect.topLeft,
                        size = rect.size
                    )
                    drawRect(
                        color = Color.Red,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 1f)
                    )
                }
            }
        }
    }
}