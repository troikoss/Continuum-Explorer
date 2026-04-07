package com.troikoss.continuum_explorer.ui.activities

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import android.content.ClipboardManager
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.OperationType
import com.troikoss.continuum_explorer.utils.GlobalEvents
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.utils.FileScannerUtils
import com.troikoss.continuum_explorer.utils.contextMenuDetector
import com.troikoss.continuum_explorer.utils.deleteFiles
import com.troikoss.continuum_explorer.utils.renameFile
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.abs

class ImageViewerActivity : FullscreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri = intent.data?.toString() ?: intent.getStringExtra("IMAGE_URI")

        setContent {
            FileExplorerTheme {
                ImageViewerScreen(
                    initialImageUri = imageUri,
                    onToggleFullscreen = { toggleFullscreen() },
                    isFullscreen = isFullscreen
                )
            }
        }
    }
}

@Composable
fun ImageViewerScreen(
    initialImageUri: String?,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean
) {
    val activity = (LocalView.current.context as? Activity)
    val context = LocalContext.current
    val resources = LocalResources.current
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

    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(initialImageUri) {
        if (initialImageUri != null) {
            withContext(Dispatchers.IO) {
                val extensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
                val images = FileScannerUtils.getSiblingFiles(context, initialImageUri, extensions)
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
                                onToggleFullscreen()  // \u003c-- call the lambda here
                            }
                        )
                    }
                    .contextMenuDetector { clickOffset ->
                        menuOffset = clickOffset
                        showMenu = true
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
                                                animate(
                                                    initialValue = offset.x,
                                                    targetValue = screenWidthPx,
                                                    animationSpec = tween(
                                                        300
                                                    )
                                                ) { value, _ -> offset = Offset(value, offset.y) }

                                                // 2. Secretly swap to the previous image
                                                currentUri = siblingImages[idx - 1]
                                                // 3. Reset the position invisibly
                                                offset = Offset.Zero
                                            } else if (offset.x < -swipeThreshold && idx < siblingImages.size - 1) {
                                                // 1. Dragged left -> Animate it sliding fully to the left
                                                animate(
                                                    initialValue = offset.x,
                                                    targetValue = -screenWidthPx,
                                                    animationSpec = tween(
                                                        300
                                                    )
                                                ) { value, _ -> offset = Offset(value, offset.y) }

                                                // 2. Secretly swap to the next image
                                                currentUri = siblingImages[idx + 1]
                                                // 3. Reset the position invisibly
                                                offset = Offset.Zero
                                            } else {
                                                // Didn't drag far enough. Rubber-band snap back to the center!
                                                animate(
                                                    initialValue = offset.x,
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        300
                                                    )
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
                                    if (idx > 0 && direction < 0f) {
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
                            val newOffset =
                                (offset + pan) * zoomRatio + focalPoint * (1f - zoomRatio)

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
                        contentDescription = stringResource(R.string.media_previous),
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
                    contentDescription = stringResource(R.string.media_viewed),
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
                        contentDescription = stringResource(R.string.media_next),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                translationX = offset.x + screenWidthPx, // Pushed right by 1 full screen
                                translationY = offset.y
                            )
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }, // Close when clicking outside
                    offset = DpOffset(
                        x = with(LocalDensity.current) { menuOffset.x.toDp() },
                        y = with(LocalDensity.current) { menuOffset.y.toDp() }
                    )
                ) {
                    // --- GROUP 1: Open With ---
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_open_with)) },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.menu_open_with)) },
                        onClick = {
                            showMenu = false
                            currentUri?.let { uriString ->
                                try {
                                    // 1. Use your new helper function!
                                    val contentUri = getSecureContentUri(context, uriString)

                                    // 2. Do the action
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(contentUri, "image/*")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(Intent.createChooser(intent, resources.getString(R.string.menu_open_with)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, resources.getString(R.string.msg_failed_open_image), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    HorizontalDivider() // A visual line separating the groups

                    // --- GROUP 2: Zoom Controls ---
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_zoom_in))},
                        leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = stringResource(R.string.menu_zoom_in)) },
                        onClick = {
                            showMenu = false
                            scale = (scale * 1.5f).coerceIn(1f, 10f)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_reset_zoom)) },
                        leadingIcon = { Icon(Icons.Default.FitScreen, contentDescription = stringResource(R.string.menu_reset_zoom)) },
                        onClick = {
                            showMenu = false
                            scale = 1f
                            offset = Offset.Zero
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_zoom_out))},
                        leadingIcon = { Icon(Icons.Default.ZoomOut, contentDescription = stringResource(R.string.menu_zoom_out)) },
                        onClick = {
                            showMenu = false
                            scale = (scale / 1.5f).coerceIn(1f, 10f)
                            if (scale == 1f) {
                                offset = Offset.Zero // Snap back to center if fully zoomed out
                            }
                        }
                    )

                    HorizontalDivider()

                    // --- GROUP 3: File Operations ---
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy_image)) },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = stringResource(R.string.menu_copy_image)) },
                        onClick = {
                            showMenu = false
                            currentUri?.let { uriString ->
                                try {
                                    val uri = Uri.parse(uriString)

                                    // Android needs a secure "content://" link to share images.
                                    // If your image is a standard file, we convert it securely here:
                                    val contentUri: Uri = if (uri.scheme == "file" || uri.scheme == null) {
                                        val file = File(uri.path ?: uriString)
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )
                                    } else {
                                        uri
                                    }

                                    // Give this secure link to the Android Clipboard
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newUri(context.contentResolver, "Copied Image", contentUri)
                                    clipboard.setPrimaryClip(clip)

                                    Toast.makeText(context, resources.getString(R.string.menu_copy_image), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, resources.getString(R.string.msg_failed_copy_image), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_set_wallpaper)) },
                        leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.menu_set_wallpaper)) },
                        onClick = {
                            showMenu = false
                            currentUri?.let { uriString ->
                                try {
                                    val contentUri = getSecureContentUri(context, uriString)

                                    // ACTION_ATTACH_DATA is Android's built-in way to say "Use this file for something"
                                    val wallpaperIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                        setDataAndType(contentUri, "image/*")
                                        putExtra("mimeType", "image/*")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(Intent.createChooser(wallpaperIntent, resources.getString(R.string.menu_set_wallpaper)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, resources.getString(R.string.menu_set_wallpaper_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_share)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.menu_share)) },
                        onClick = {
                            showMenu = false
                            currentUri?.let { uriString ->
                                try {
                                    val contentUri = getSecureContentUri(context, uriString)

                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/*"
                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.msg_share_image_via)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, resources.getString(R.string.share_image_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.menu_rename)) },
                        onClick = {
                            showMenu = false
                            withImageFile(currentUri) { file ->
                                val target = file.toUniversal()
                                FileOperationsManager.openRename(target, context) { newName ->
                                    FileOperationsManager.start()
                                    FileOperationsManager.update(0, 1, operationType= OperationType.RENAME)
                                    FileOperationsManager.currentFileName.value = target.name

                                    startPopUpActivity(context)

                                    coroutineScope.launch {
                                        val success = renameFile(target, newName)
                                        if (success) {
                                            val newFile = File(file.parentFile, newName)
                                            val newUriString = Uri.fromFile(newFile).toString()
                                            val index = siblingImages.indexOf(currentUri)
                                            if (index != -1) {
                                                siblingImages = siblingImages.toMutableList().apply { set(index, newUriString) }
                                                currentUri = newUriString
                                            }
                                            GlobalEvents.triggerRefresh()
                                        } else {
                                            withContext(Dispatchers.Main) { Toast.makeText(context, resources.getString(R.string.msg_rename_failed), Toast.LENGTH_SHORT).show() }
                                        }
                                        FileOperationsManager.finish()
                                    }
                                }
                                startPopUpActivity(context)
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.menu_delete)) },
                        onClick = {
                            showMenu = false
                            withImageFile(currentUri) { file ->
                                FileOperationsManager.start()
                                startPopUpActivity(context)

                                coroutineScope.launch {
                                    deleteFiles(context, listOf(file.toUniversal()))

                                    if (!file.exists()) {
                                        val index = siblingImages.indexOf(currentUri)
                                        val newList = siblingImages.filter { it != currentUri }
                                        if (newList.isEmpty()) {
                                            activity?.finish()
                                        } else {
                                            siblingImages = newList
                                            currentUri = if (index < newList.size) newList[index] else newList.last()
                                        }
                                        GlobalEvents.triggerRefresh()
                                    }
                                }
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_properties)) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = stringResource(R.string.menu_properties)) },
                        onClick = {
                            showMenu = false
                            withImageFile(currentUri) { file ->
                                FileOperationsManager.showProperties(listOf(file.toUniversal()))
                                startPopUpActivity(context)
                            }
                        }
                    )

                    HorizontalDivider()

                    // --- GROUP 4: Window Controls ---
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_fullscreen)) },
                        leadingIcon = { Icon(Icons.Default.Fullscreen, contentDescription = stringResource(R.string.menu_fullscreen)) },
                        onClick = {
                            showMenu = false
                            onToggleFullscreen()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.close)) },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) },
                        onClick = {
                            showMenu = false
                            activity?.finish()
                        }
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
                            val distance = abs(itemCenter - centerOffset)
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
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                        )
                        .padding(vertical = 8.dp), // Inner padding
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(siblingImages) { imgUri ->
                        val isSelected = imgUri == currentUri
                        AsyncImage(
                            model = imgUri,
                            contentDescription = stringResource(R.string.media_thumbnail),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size (itemSizeDp)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent
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

private fun withImageFile(uriString: String?, action: (File) -> Unit) {
    uriString?.let {
        val uri = Uri.parse(it)
        val path = if (uri.scheme == "file" || uri.scheme == null) uri.path ?: it else null
        path?.let { filePath ->
            val file = File(filePath)
            if (file.exists()) {
                action(file)
            }
        }
    }
}

private fun startPopUpActivity(context: Context) {
    val intent = Intent(context, PopUpActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun getSecureContentUri(context: Context, uriString: String): Uri {
    val uri = Uri.parse(uriString)

    // If it's already a secure content:// link, just return it
    if (uri.scheme == "content") {
        return uri
    }

    // Otherwise, it's a raw file, so convert it securely
    val file = File(uri.path ?: uriString)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
}