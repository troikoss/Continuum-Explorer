package com.troikoss.continuum_explorer.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.utils.FileScannerUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class VideoPlayerActivity : FullscreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUri = intent.data?.toString() ?: intent.getStringExtra("VIDEO_URI")

        setContent {
            FileExplorerTheme {
                VideoPlayerScreen(
                    initialVideoUri = videoUri,
                    onToggleFullscreen = { toggleFullscreen() }
                )
            }
        }
    }
}
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VideoPlayerScreen(
    initialVideoUri: String?,
    onToggleFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    var isPlaying by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }
    var hasPrev by remember { mutableStateOf(false) }

    var optionsMenuExpanded by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }

    var isUiVisible by remember { mutableStateOf(true) }
    var hideTimerTrigger by remember { mutableStateOf(0) }
    var isHoveringControls by remember { mutableStateOf(false) }

    var showSkipBackIndicator by remember { mutableStateOf(false) }
    var showSkipForwardIndicator by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onEvents(player: Player, events: Player.Events) {
                hasNext = player.hasNextMediaItem()
                hasPrev = player.hasPreviousMediaItem()
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Load the "Siblings" (Other videos in the same folder)
    LaunchedEffect(initialVideoUri) {
        if (initialVideoUri != null) {
            val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov")

            // Get all videos in that folder
            val allVideos = FileScannerUtils.getSiblingFiles(context, initialVideoUri, videoExtensions)

            // Tell ExoPlayer to load ALL of them
            allVideos.forEach { uri ->
                exoPlayer.addMediaItem(MediaItem.fromUri(uri))
            }

            // Find which index the clicked video is at, so we start there
            val startIndex = allVideos.indexOf(initialVideoUri).coerceAtLeast(0)
            exoPlayer.seekTo(startIndex, 0L)

            exoPlayer.prepare()
            exoPlayer.playWhenReady = true

            hasNext = exoPlayer.hasNextMediaItem()
            hasPrev = exoPlayer.hasPreviousMediaItem()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                currentPosition = exoPlayer.currentPosition
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                delay(500) // Update every half-second
            }
        }
    }

    LaunchedEffect(isUiVisible, hideTimerTrigger, isPlaying, isHoveringControls) {
        if (isUiVisible && isPlaying && !isHoveringControls) {
            delay(3000)
            isUiVisible = false
        }
    }

    LaunchedEffect(showSkipBackIndicator) {
        if (showSkipBackIndicator) {
            delay(600)
            showSkipBackIndicator = false
        }
    }

    LaunchedEffect(showSkipForwardIndicator) {
        if (showSkipForwardIndicator) {
            delay(600)
            showSkipForwardIndicator = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // The UI
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
        ) {

            // The Video
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .videoGestures(
                        isUiVisible = isUiVisible,
                        isPlaying = isPlaying,
                        exoPlayer = exoPlayer,
                        onUiVisibilityChange = { isUiVisible = it },
                        onHideTimerTrigger = { hideTimerTrigger++ },
                        onToggleFullscreen = onToggleFullscreen
                    )

            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Left skip hitbox
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .align(Alignment.CenterStart)
                    .skipGesture(onDoubleTap = {
                        val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                        exoPlayer.seekTo(newPos)
                        currentPosition = newPos
                        showSkipBackIndicator = true
                    })
            )

            // Right skip hitbox
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .align(Alignment.CenterEnd)
                    .skipGesture(onDoubleTap = {
                        val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                        exoPlayer.seekTo(newPos)
                        currentPosition = newPos
                        showSkipForwardIndicator = true
                    })
            )

            // Skip back indicator
            AnimatedVisibility(
                visible = showSkipBackIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .align(Alignment.CenterStart)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Skip forward indicator
            AnimatedVisibility(
                visible = showSkipForwardIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .align(Alignment.CenterEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }


            // Center play/pause button
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }


            // Bottom Controls
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.75f)
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.type == PointerType.Mouse }) {
                                        isHoveringControls = when (event.type) {
                                            PointerEventType.Enter -> true
                                            PointerEventType.Exit -> false
                                            else -> isHoveringControls
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { hideTimerTrigger++ })
                        }
                ) {
                    // Seekbar
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { newPosition ->
                            currentPosition = newPosition.toLong()
                            exoPlayer.seekTo(currentPosition) // Moves the video when you drag
                        },
                        valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Red,      // The little circle
                            activeTrackColor = Color.Red // The line behind the circle
                        )
                    )


                    // Controls
                    Row(modifier = Modifier
                        .padding(8.dp)
                    ) {

                        // Play/Pause Button
                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(64.dp)
                            )
                        }

                        // Previous Button
                        IconButton(
                            onClick = { if (hasPrev) exoPlayer.seekToPreviousMediaItem() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Video",
                                tint =  if (hasPrev) Color.White else Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        // Next Button
                        IconButton(
                            onClick = { if (hasNext) exoPlayer.seekToNextMediaItem() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Video",
                                tint = if (hasNext) Color.White else Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        val currentPositionFormatted = formatTime(currentPosition)
                        val totalDurationFormatted = formatTime(totalDuration)

                        Text(
                            text = "$currentPositionFormatted / $totalDurationFormatted",
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(start = 8.dp)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Options Button
                        Box {
                            IconButton(
                                onClick = { optionsMenuExpanded = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(64.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = optionsMenuExpanded,
                                onDismissRequest = { optionsMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Subtitles, null) },
                                    text = { Text("Subtitles") },
                                    onClick = { optionsMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Audiotrack, null) },
                                    text = { Text("Audio Track") },
                                    onClick = { optionsMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.FastForward, null) },
                                    text = { Text("Speed") },
                                    onClick = { optionsMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.AspectRatio, null) },
                                    text = { Text("Aspect Ratio") },
                                    onClick = { optionsMenuExpanded = false }
                                )
                            }
                        }

                        // Fullscreen Button
                        IconButton(
                            onClick = { onToggleFullscreen() }
                        ){
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(64.dp)
                            )
                        }

                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun Modifier.videoGestures(
    isUiVisible: Boolean,
    isPlaying: Boolean,
    exoPlayer: ExoPlayer,
    onUiVisibilityChange: (Boolean) -> Unit,
    onHideTimerTrigger: () -> Unit,
    onToggleFullscreen: () -> Unit
): Modifier = composed {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var pendingClickJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val currentIsUiVisible by rememberUpdatedState(isUiVisible)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentOnUiVisibilityChange by rememberUpdatedState(onUiVisibilityChange)
    val currentOnHideTimerTrigger by rememberUpdatedState(onHideTimerTrigger)
    val currentOnToggleFullscreen by rememberUpdatedState(onToggleFullscreen)

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val firstChange = event.changes.firstOrNull() ?: continue
                    val isTouch = event.changes.any { it.type == PointerType.Touch }
                    val isStylus = event.changes.any { it.type == PointerType.Stylus }

                    if ((isTouch || isStylus) && event.type == PointerEventType.Press) {
                        firstChange.consume()
                        if (currentIsUiVisible) {
                            currentOnUiVisibilityChange(false)
                        } else {
                            currentOnUiVisibilityChange(true)
                            currentOnHideTimerTrigger()
                        }
                    }
                }
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val firstChange = event.changes.firstOrNull() ?: continue
                    val isMouse = event.changes.any { it.type == PointerType.Mouse }
                    val isPrimary = event.buttons.isPrimaryPressed

                    if (firstChange.type == PointerType.Mouse && event.type == PointerEventType.Move) {
                        currentOnUiVisibilityChange(true)
                        currentOnHideTimerTrigger()
                    }

                    if (isMouse && event.type == PointerEventType.Press && isPrimary) {
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - lastClickTime < 300) {
                            pendingClickJob?.cancel()
                            pendingClickJob = null
                            currentOnToggleFullscreen()
                        } else {
                            pendingClickJob?.cancel()
                            pendingClickJob = scope.launch {
                                delay(300)
                                currentOnUiVisibilityChange(true)
                                currentOnHideTimerTrigger()
                                if (currentIsPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                        lastClickTime = currentTime
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
}

fun Modifier.skipGesture(
    onDoubleTap: () -> Unit
): Modifier = composed {
    var lastTapTime by remember { mutableLongStateOf(0L) }

    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val firstChange = event.changes.firstOrNull() ?: continue
                val isTouch = firstChange.type == PointerType.Touch
                val isStylus = firstChange.type == PointerType.Stylus

                if ((isTouch || isStylus) && event.type == PointerEventType.Press) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < 300) {
                        // Double tap - consume and skip
                        firstChange.consume()
                        onDoubleTap()
                    }
                    // Single tap - do NOT consume, let it fall through
                    lastTapTime = currentTime
                }
            }
        }
    }
}

