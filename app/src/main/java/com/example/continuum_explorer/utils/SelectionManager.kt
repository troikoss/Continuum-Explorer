package com.example.continuum_explorer.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.continuum_explorer.model.UniversalFile

/**
 * Manages complex selection logic including range selection (Shift),
 * toggle selection (Ctrl), and keyboard navigation.
 */
class SelectionManager {

    // The current set of files available for selection
    var allFiles by mutableStateOf(emptyList<UniversalFile>())

    // The currently selected files
    var selectedItems by mutableStateOf(setOf<UniversalFile>())
        private set

    // The start point of a range selection
    var anchorItem by mutableStateOf<UniversalFile?>(null)
        private set

    // The item that currently has the focus/cursor
    var leadItem by mutableStateOf<UniversalFile?>(null)
        private set

    fun selectAll() {
        selectedItems = allFiles.toSet()
        anchorItem = allFiles.lastOrNull()
    }

    /**
     * Handles mouse/click-based selection logic.
     */
    fun handleRowClick(item: UniversalFile, isShiftPressed: Boolean, isCtrlPressed: Boolean) {
        if (isShiftPressed && anchorItem != null) {
            // SHIFT: Select range between anchor and clicked item
            val start = allFiles.indexOf(anchorItem)
            val end = allFiles.indexOf(item)

            if (start != -1 && end != -1){
                val range = if (start < end) {
                    allFiles.subList(start, end + 1)
                } else {
                    allFiles.subList(end, start + 1)
                }
                selectedItems = range.toSet()
                leadItem = item
            }
        } else if (isCtrlPressed) {
            // CTRL: Add/Remove single item without clearing others
            select(item)
        } else {
            // Regular Click: Clear all and select only this item
            selectSingle(item)
        }
    }

    /**
     * Clears all selections and selects only the specified item.
     */
    fun selectSingle(item: UniversalFile) {
        selectedItems = setOf(item)
        anchorItem = item
        leadItem = item
    }

    fun select(item: UniversalFile) {
        selectedItems = if (selectedItems.contains(item)) {
            selectedItems - item
        } else {
            selectedItems + item
        }
        anchorItem = item
        leadItem = item
    }

    fun selectInverse() {
        // 1. Create a set of files that are NOT currently selected
        val inverseSet = allFiles.filter { it !in selectedItems }.toSet()

        // 2. Update the selected items
        selectedItems = inverseSet

        // 3. Optional: Move the focus/anchor to the first item in the new selection
        if (inverseSet.isNotEmpty()) {
            val firstNewItem = inverseSet.first()
            anchorItem = firstNewItem
            leadItem = firstNewItem
        } else {
            anchorItem = null
            leadItem = null
        }
    }

    /**
     * Updates only the anchor and lead items without selecting the file.
     * Useful for focusing an item when navigating back or up.
     */
    fun setFocus(item: UniversalFile) {
        anchorItem = item
        leadItem = item
    }

    fun touchToggle(item: UniversalFile) {
        selectedItems = if (selectedItems.contains(item)) {
            selectedItems - item
        } else {
            selectedItems + item
        }
    }

    fun updateSelectionFromDrag(touchedItems: Set<UniversalFile>) {
        selectedItems = touchedItems
    }

    fun isSelected(item: UniversalFile): Boolean { return selectedItems.contains(item) }

    /**
     * Clears the current selection.
     * @param keepFocus If true, keeps the anchor and lead items.
     */
    fun clear(keepFocus: Boolean = false) {
        selectedItems = emptySet()
        if (!keepFocus) {
            anchorItem = null
            leadItem = null
        }
    }

    /**
     * Completely resets the selection state.
     */
    fun reset() = clear(keepFocus = false)

    fun isInSelectionMode(): Boolean { return selectedItems.isNotEmpty() }

    /**
     * Updates selection based on keyboard arrow keys.
     */
    fun moveSelection(direction: Int, isShiftPressed: Boolean, isCtrlPressed: Boolean) {
        if (allFiles.isEmpty()) return

        val currentIndex = if (leadItem != null) allFiles.indexOf(leadItem) else -1
        val newIndex = (currentIndex + direction).coerceIn(0, allFiles.size - 1)

        val newFile = allFiles[newIndex]

        if (isCtrlPressed) {
            leadItem = newFile
        } else if (isShiftPressed && anchorItem != null) {
            // Expand selection from Anchor to New Lead
            val start = allFiles.indexOf(anchorItem)
            val end = newIndex

            val range = if (start < end) {
                allFiles.subList(start, end + 1)
                } else {
                allFiles.subList(end, start + 1)
            }
            selectedItems = range.toSet()
            leadItem = newFile
        } else {
            // Single selection move
            selectedItems = setOf(newFile)
            anchorItem = newFile
            leadItem = newFile
        }
    }
}

/**
 * A modifier that applies selection highlights and focus borders to an item.
 */
fun Modifier.selectionBackground(
    item: UniversalFile,
    selectionManager: SelectionManager,
    isHovered: Boolean,
    shape: Shape = RectangleShape
): Modifier = composed {
    val isSelected = selectionManager.isSelected(item)
    val isLead = selectionManager.leadItem == item

    val baseSelectedColor = MaterialTheme.colorScheme.primaryContainer
    val darkenedSelectedColor = lerp(baseSelectedColor, Color.Black, 0.1f)
    val hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    val backgroundColor = when {
        isSelected && isHovered -> darkenedSelectedColor
        isSelected && isLead -> darkenedSelectedColor
        isSelected -> baseSelectedColor
        isHovered -> hoverColor
        else -> Color.Transparent
    }

    this
        .background(backgroundColor, shape)
        .border(
            width = if (isLead && !isSelected) 1.dp else 0.dp,
            color = if (isLead && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = shape
        )
}

@Composable
fun rememberSelectionManager(): SelectionManager {
    return remember { SelectionManager() }
}
