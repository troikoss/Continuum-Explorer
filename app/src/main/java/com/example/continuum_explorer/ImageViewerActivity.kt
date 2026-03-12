package com.example.continuum_explorer

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.OutcomeReceiver
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.continuum_explorer.model.FileColumnType
import com.example.continuum_explorer.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.launch

class ImageViewerActivity : ComponentActivity() {

    private var isFullscreen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri = intent.data?.toString() ?: intent.getStringExtra("IMAGE_URI")

        setContent {
            ImageViewerScreen(
                initialImageUri = imageUri,
                onToggleFullscreen = { toggleFullscreen() },
                isFullscreen = isFullscreen
            )
        }
    }

    private fun toggleFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isInMultiWindowMode()) {
            // Freeform window: need to expand the window first, then hide bars
            val request = if (isFullscreen)
                FULLSCREEN_MODE_REQUEST_EXIT
            else
                FULLSCREEN_MODE_REQUEST_ENTER

            requestFullscreenMode(request, object : OutcomeReceiver<Void, Throwable> {
                override fun onResult(result: Void?) {
                    isFullscreen = !isFullscreen
                    updateSystemBars()
                }
                override fun onError(error: Throwable) {
                    // Still try to hide bars as fallback
                    isFullscreen = !isFullscreen
                    updateSystemBars()
                }
            })
        } else {
            // Maximized or older API: window already covers screen, just toggle bars
            isFullscreen = !isFullscreen
            updateSystemBars()
        }
    }

    private fun updateSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

fun getSiblingImages(context: Context, uriString: String): List<String> {
    if (uriString.isBlank()) return emptyList()
    val uri = Uri.parse(uriString)
    
    var realPath: String? = null
    
    if (uri.scheme == null || uri.scheme == "file") {
        realPath = uri.path ?: uriString
    } else if (uri.scheme == "content") {
        // Handle custom FileProvider scheme
        if (uri.authority == "${context.packageName}.provider") {
            val pathSegments = uri.pathSegments
            if (pathSegments.size >= 2) {
                val root = pathSegments[0]
                val rest = pathSegments.subList(1, pathSegments.size).joinToString("/")
                when (root) {
                    "external_files" -> realPath = Environment.getExternalStorageDirectory().absolutePath + "/" + rest
                    "root" -> realPath = "/$rest"
                    "my_files" -> realPath = context.filesDir.absolutePath + "/" + rest
                }
            }
        }
        // Handle SAF scheme
        if (realPath == null && DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            val path = if (split.size > 1) split[1] else ""
            realPath = if ("primary".equals(type, ignoreCase = true)) {
                Environment.getExternalStorageDirectory().absolutePath + "/" + path
            } else {
                "/storage/$type/$path"
            }
        }
        // Handle MediaStore
        if (realPath == null) {
            try {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        realPath = cursor.getString(columnIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageViewer", "Error querying MediaStore", e)
            }
        }
    }

    if (realPath != null) {
        val file = File(realPath)
        val parent = file.parentFile
        if (parent != null && parent.exists() && parent.isDirectory) {

            // Read sort params using the folder path as key, same as FolderConfigurations does
            val prefs = context.getSharedPreferences("folder_sort_params", Context.MODE_PRIVATE)
            val saved = prefs.getString(parent.absolutePath, null)
            val sortColumn: FileColumnType
            val sortOrder: SortOrder
            if (saved != null) {
                val split = saved.split(":")
                sortColumn = try { FileColumnType.valueOf(split[0]) } catch (e: Exception) { FileColumnType.NAME }
                sortOrder = try { SortOrder.valueOf(split[1]) } catch (e: Exception) { SortOrder.Ascending }
            } else {
                sortColumn = FileColumnType.NAME
                sortOrder = SortOrder.Ascending
            }

            val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
            val files = parent.listFiles()
            if (files != null) {
                val filtered = files.filter {
                    it.isFile && imageExtensions.contains(it.extension.lowercase())
                }
                val sorted = when (sortColumn) {
                    FileColumnType.NAME -> filtered.sortedBy { it.name.lowercase() }
                    FileColumnType.DATE -> filtered.sortedBy { it.lastModified() }
                    FileColumnType.SIZE -> filtered.sortedBy { it.length() }
                }
                val ordered = if (sortOrder == SortOrder.Descending) sorted.reversed() else sorted
                return ordered.map { Uri.fromFile(it).toString() }
            }
        }
    }

    return listOf(uriString)
}

@Composable
fun ImageViewerScreen(
    initialImageUri: String?,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean
) {
    val activity = (LocalView.current.context as? Activity)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }

    var currentUri by remember { mutableStateOf(initialImageUri) }
    var siblingImages by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(initialImageUri) {
        if (initialImageUri != null) {
            withContext(Dispatchers.IO) {
                val images = getSiblingImages(context, initialImageUri)
                withContext(Dispatchers.Main) {
                    siblingImages = images
                    val initialFileSegment = Uri.parse(initialImageUri).lastPathSegment
                    val match = images.find { it == initialImageUri || Uri.parse(it).lastPathSegment == initialFileSegment }
                    if (match != null) {
                        currentUri = match
                    } else if (images.isNotEmpty()) {
                        currentUri = images.first()
                    }
                }
            }
        }
    }

    LaunchedEffect(currentUri) {
        scale = 1f
        offset = Offset.Zero
    }

    Scaffold { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
                .focusRequester(focusRequester)
                .focusable() // Makes the box able to hear the keyboard
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            // 'F' key for Fullscreen
                            Key.F -> {
                                onToggleFullscreen()
                                true // means we handled the event
                            }
                            // 'Ctrl + W' to close
                            Key.W -> {
                                if (keyEvent.isCtrlPressed) {
                                    activity?.finish()
                                    true
                                } else false
                            }
                            // Left/Right arrow keys for navigation
                            Key.DirectionLeft -> {
                                val idx = siblingImages.indexOf(currentUri)
                                if (idx > 0) {
                                    currentUri = siblingImages[idx - 1]
                                }
                                true
                            }

                            Key.DirectionRight -> {
                                val idx = siblingImages.indexOf(currentUri)
                                if (idx >= 0 && idx < siblingImages.size - 1) {
                                    currentUri = siblingImages[idx + 1]
                                }
                                true
                            }

                            else -> false
                        }
                    } else false
                }
        ) {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }
            val screenCenter = Offset(screenWidthPx / 2f, screenHeightPx / 2f)

            fun calculatePanLimits(): Pair<Float, Float> {
                if (imageSize == Size.Zero || imageSize.width == 0f || imageSize.height == 0f) {
                    return Pair(0f, 0f)
                }
                val imageAspect = imageSize.width / imageSize.height
                val screenAspect = screenWidthPx / screenHeightPx
                val fittedWidth: Float
                val fittedHeight: Float
                if (imageAspect > screenAspect) {
                    fittedWidth = screenWidthPx
                    fittedHeight = screenWidthPx / imageAspect
                } else {
                    fittedHeight = screenHeightPx
                    fittedWidth = screenHeightPx * imageAspect
                }
                val maxX = maxOf(0f, (fittedWidth * scale - screenWidthPx) / 2f)
                val maxY = maxOf(0f, (fittedHeight * scale - screenHeightPx) / 2f)
                return Pair(maxX, maxY)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                onToggleFullscreen()  // <-- call the lambda here
                            }
                        )
                    }
                    .pointerInput(screenWidthPx, screenHeightPx) {
                        awaitPointerEventScope {

                            while (true) {
                                val event = awaitPointerEvent()

                                if (event.type == PointerEventType.Release) {
                                    // When finger is lifted, check how far we dragged the image
                                    if (scale == 1f && offset.x != 0f) {
                                        val swipeThreshold = screenWidthPx * 0.2f // 20% of screen
                                        val idx = siblingImages.indexOf(currentUri)

                                        coroutineScope.launch {
                                            if (offset.x > swipeThreshold && idx > 0) {
                                                // 1. Dragged right -> Animate it sliding fully to the right
                                                androidx.compose.animation.core.animate(
                                                    initialValue = offset.x,
                                                    targetValue = screenWidthPx,
                                                    animationSpec = androidx.compose.animation.core.tween(300)
                                                ) { value, _ -> offset = Offset(value, offset.y) }

                                                // 2. Secretly swap to the previous image
                                                currentUri = siblingImages[idx - 1]
                                                // 3. Reset the position invisibly
                                                offset = Offset.Zero
                                            }
                                            else if (offset.x < -swipeThreshold && idx < siblingImages.size - 1) {
                                                // 1. Dragged left -> Animate it sliding fully to the left
                                                androidx.compose.animation.core.animate(
                                                    initialValue = offset.x,
                                                    targetValue = -screenWidthPx,
                                                    animationSpec = androidx.compose.animation.core.tween(300)
                                                ) { value, _ -> offset = Offset(value, offset.y) }

                                                // 2. Secretly swap to the next image
                                                currentUri = siblingImages[idx + 1]
                                                // 3. Reset the position invisibly
                                                offset = Offset.Zero
                                            }
                                            else {
                                                // Didn't drag far enough. Rubber-band snap back to the center!
                                                androidx.compose.animation.core.animate(
                                                    initialValue = offset.x,
                                                    targetValue = 0f,
                                                    animationSpec = androidx.compose.animation.core.tween(300)
                                                ) { value, _ -> offset = Offset(value, offset.y) }
                                            }
                                        }
                                    }
                                }

                                if (event.type == PointerEventType.Press && event.buttons.isTertiaryPressed) {
                                    activity?.finish()
                                }
                                if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                                    val change = event.changes.first()
                                    val delta = change.scrollDelta.y

                                    val oldScale = scale

                                    val zoomFactor = if (delta < 0) 1.1f else 0.9f
                                    scale = (scale * zoomFactor).coerceIn(1f, 10f)

                                    val zoomRatio = scale / oldScale
                                    val focalPoint = change.position - screenCenter
                                    val newOffset =
                                        offset * zoomRatio + focalPoint * (1f - zoomRatio)

                                    val (maxX, maxY) = calculatePanLimits()
                                    offset = Offset(
                                        x = newOffset.x.coerceIn(-maxX, maxX),
                                        y = newOffset.y.coerceIn(-maxY, maxY)
                                    )
                                }
                                if (event.type == PointerEventType.Scroll && !event.keyboardModifiers.isCtrlPressed) {
                                    val change = event.changes.first()
                                    var direction = change.scrollDelta.y

                                    val idx = siblingImages.indexOf(currentUri)
                                    if (idx > 0  && direction < 0f) {
                                        currentUri = siblingImages[idx - 1]
                                        direction = 0f
                                    }
                                    if (idx >= 0 && idx < siblingImages.size - 1 && direction > 0f) {
                                        currentUri = siblingImages[idx + 1]
                                        direction = 0f
                                    }
                                }
                                if (event.type == PointerEventType.Move &&
                                    event.keyboardModifiers.isCtrlPressed &&
                                    event.buttons.isPrimaryPressed
                                ) {
                                    val change = event.changes.first()
                                    val pan = change.position - change.previousPosition
                                    val (maxX, maxY) = calculatePanLimits()
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                        y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                    )
                                }
                            }
                        }
                    }
                    .pointerInput(screenWidthPx, screenHeightPx) {
                        detectTransformGestures { centroid, pan, zoomMultiplier, _ ->
                            val oldScale = scale
                            scale = (scale * zoomMultiplier).coerceIn(1f, 10f)

                            val zoomRatio = scale / oldScale
                            val focalPoint = centroid - screenCenter
                            val newOffset = (offset + pan) * zoomRatio + focalPoint * (1f - zoomRatio)

                            val (maxX, maxY) = calculatePanLimits()

                            if (scale == 1f) {
                                // When fully zoomed out, remove the left/right walls!
                                // This allows the image to follow your finger as you swipe.
                                offset = Offset(
                                    x = newOffset.x,
                                    y = newOffset.y.coerceIn(-maxY, maxY)
                                )
                            } else {
                                // When zoomed in, strictly lock the image inside its borders
                                offset = Offset(
                                    x = newOffset.x.coerceIn(-maxX, maxX),
                                    y = newOffset.y.coerceIn(-maxY, maxY)
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val currentIndex = siblingImages.indexOf(currentUri)

                // 1. The Previous Image (Hidden just off-screen to the left)
                if (currentIndex > 0 && scale == 1f) {
                    AsyncImage(
                        model = siblingImages[currentIndex - 1],
                        contentDescription = "Previous",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                translationX = offset.x - screenWidthPx, // Pushed left by 1 full screen
                                translationY = offset.y
                            )
                    )
                }

                // 2. The Current Image (Center)
                AsyncImage(
                    model = currentUri,
                    contentDescription = "Viewed Image",
                    contentScale = ContentScale.Fit,
                    onSuccess = { state -> imageSize = state.painter.intrinsicSize },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )

                // 3. The Next Image (Hidden just off-screen to the right)
                if (currentIndex < siblingImages.size - 1 && scale == 1f) {
                    AsyncImage(
                        model = siblingImages[currentIndex + 1],
                        contentDescription = "Next",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                translationX = offset.x + screenWidthPx, // Pushed right by 1 full screen
                                translationY = offset.y
                            )
                    )
                }
            }

            // When the viewed image changes (e.g. via keyboard keys), scroll the filmstrip to it
            LaunchedEffect(currentUri) {
                val index = siblingImages.indexOf(currentUri)
                if (index >= 0 && !listState.isScrollInProgress) {
                    // Use scrollToItem instead of animateScrollToItem for instant speed!
                    listState.scrollToItem(index)
                }
            }

            // Filmstrip overlay at the bottom
            if (siblingImages.size > 1 && !isFullscreen) {

                // Calculate padding to center the focused thumbnail
                val configuration = LocalConfiguration.current
                val screenWidthDp = configuration.screenWidthDp.dp
                val itemSizeDp = 64.dp
                // Subtracting 32.dp to account for the start/end margins on the background box
                val horizontalPadding = (screenWidthDp - 32.dp - itemSizeDp) / 2

                // Keep the centered item synced with the currently viewed image
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val layoutInfo = listState.layoutInfo
                        if (layoutInfo.visibleItemsInfo.isEmpty()) return@snapshotFlow -1

                        val viewportStart = layoutInfo.viewportStartOffset
                        val viewportEnd = layoutInfo.viewportEndOffset
                        val centerOffset = viewportStart + (viewportEnd - viewportStart) / 2

                        var closestIndex = -1
                        var minDistance = Int.MAX_VALUE

                        // Find which thumbnail is physically closest to the center
                        for (item in layoutInfo.visibleItemsInfo) {
                            val itemCenter = item.offset + item.size / 2
                            val distance = kotlin.math.abs(itemCenter - centerOffset)
                            if (distance < minDistance) {
                                minDistance = distance
                                closestIndex = item.index
                            }
                        }
                        closestIndex
                    }.collect { index ->
                        if (index in siblingImages.indices) {
                            val newUri = siblingImages[index]
                            if (currentUri != newUri) {
                                currentUri = newUri // Update the main image
                            }
                        }
                    }
                }

                LazyRow(
                    state = listState,
                    // Automatically snap to the nearest thumbnail when scrolling stops
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                    // Apply our calculated padding so items align in the center
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 8.dp), // Inner padding
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(siblingImages) { imgUri ->
                        val isSelected = imgUri == currentUri
                        AsyncImage(
                            model = imgUri,
                            contentDescription = "Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(if (isSelected) itemSizeDp else 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    // Clicking a thumbnail gently scrolls it to the center
                                    coroutineScope.launch {
                                        val idx = siblingImages.indexOf(imgUri)
                                        if (idx >= 0) {
                                            listState.animateScrollToItem(idx)
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}