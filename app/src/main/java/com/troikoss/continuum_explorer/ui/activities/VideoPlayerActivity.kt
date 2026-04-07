package com.troikoss.continuum_explorer.ui.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.ui.theme.FileExplorerTheme
import com.troikoss.continuum_explorer.utils.FileScannerUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


// ── Data classes ──────────────────────────────────────────────────────────────

/** Represents a selectable audio or subtitle track shown in the menu. */
data class TrackOption(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

/** All supported aspect-ratio resize modes with display names. */
@androidx.media3.common.util.UnstableApi
enum class ResizeMode(val labelRes: Int, val value: Int) {
    FIT(R.string.video_fit, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL(R.string.video_fill, AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM(R.string.video_zoom, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FIXED_WIDTH(R.string.video_fixed_w, AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FIXED_HEIGHT(R.string.video_fixed_h, AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
}


// ── Activity ──────────────────────────────────────────────────────────────────

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


// ── Main Composable ───────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VideoPlayerScreen(
    initialVideoUri: String?,
    onToggleFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = (LocalView.current.context as? Activity)
    val focusRequester = remember { FocusRequester() }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    // ── Playback state ──────────────────────────────────────────────────────
    var isPlaying by remember { mutableStateOf(false) }
    var hasNext  by remember { mutableStateOf(false) }
    var hasPrev  by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration   by remember { mutableStateOf(0L) }

    // ── UI visibility ───────────────────────────────────────────────────────
    var isUiVisible      by remember { mutableStateOf(true) }
    var hideTimerTrigger by remember { mutableStateOf(0) }
    var isHoveringControls by remember { mutableStateOf(false) }

    // ── Skip / play / pause indicators ─────────────────────────────────────
    var showSkipBackIndicator    by remember { mutableStateOf(false) }
    var showSkipForwardIndicator by remember { mutableStateOf(false) }
    var showPauseIndicator  by remember { mutableStateOf(false) }
    var showPlayIndicator   by remember { mutableStateOf(false) }

    // ── Options menu ────────────────────────────────────────────────────────
    var optionsMenuExpanded by remember { mutableStateOf(false) }
    var optionsScreen       by remember { mutableStateOf("MAIN") }

    // ── Volume ──────────────────────────────────────────────────────────────
    var volume by remember { mutableFloatStateOf(1f) }

    // ── Playback speed ──────────────────────────────────────────────────────
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    // ── Aspect ratio ────────────────────────────────────────────────────────
    var resizeMode by remember { mutableStateOf(ResizeMode.FIT) }

    // Capture PlayerView so we can change resizeMode imperatively
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // ── Loop ────────────────────────────────────────────────────────────────
    var loopEnabled by remember { mutableStateOf(false) }

    // ── Thumbnail preview ────────────────────────────────────────────────────
    val thumbnailCache = remember { mutableStateMapOf<Long, ImageBitmap>() }
    var seekbarHoverFraction by remember { mutableStateOf<Float?>(null) }
    var thumbnailAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var currentVideoUri by remember { mutableStateOf(initialVideoUri) }

    // ── Tracks ──────────────────────────────────────────────────────────────
    var audioTracks    by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }

    // Helper: rebuild track lists from current Tracks object
    fun buildTrackLists(tracks: Tracks) {
        val selectedAudioGroup    = exoPlayer.trackSelectionParameters
        val selectedTextGroup     = exoPlayer.trackSelectionParameters

        audioTracks = tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
            .mapIndexed { gi, group ->
                val fmt   = group.getTrackFormat(0)
                val lang  = fmt.language?.let { Locale(it).displayLanguage } ?: ""
                val label = when {
                    fmt.label    != null -> fmt.label!!
                    lang.isNotEmpty()    -> lang
                    else                 -> resources.getString(R.string.video_audio_track, gi + 1)
                }
                // A track group is selected when the player actually rendered it
                TrackOption(label, gi, 0, group.isSelected)
            }

        subtitleTracks = tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
            .mapIndexed { gi, group ->
                val fmt   = group.getTrackFormat(0)
                val lang  = fmt.language?.let { Locale(it).displayLanguage } ?: ""
                val label = when {
                    fmt.label    != null -> fmt.label!!
                    lang.isNotEmpty()    -> lang
                    else                 -> resources.getString(R.string.video_subtitle_track, gi + 1)
                }
                TrackOption(label, gi, 0, group.isSelected)
            }
    }

    // ── Player listeners ────────────────────────────────────────────────────
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onEvents(player: Player, events: Player.Events) {
                hasNext = player.hasNextMediaItem()
                hasPrev = player.hasPreviousMediaItem()
            }
            override fun onTracksChanged(tracks: Tracks) { buildTrackLists(tracks) }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentVideoUri = mediaItem?.localConfiguration?.uri?.toString()
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ── Apply loop setting ──────────────────────────────────────────────────
    LaunchedEffect(loopEnabled) {
        exoPlayer.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // ── Apply volume ────────────────────────────────────────────────────────
    LaunchedEffect(volume) {
        exoPlayer.volume = volume
    }

    // ── Apply playback speed ────────────────────────────────────────────────
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackParameters(PlaybackParameters(playbackSpeed))
    }

    // ── Apply resize mode ───────────────────────────────────────────────────
    LaunchedEffect(resizeMode) {
        playerViewRef?.resizeMode = resizeMode.value
    }

    // ── Load sibling videos ─────────────────────────────────────────────────
    LaunchedEffect(initialVideoUri) {
        if (initialVideoUri != null) {
            val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov")
            val allVideos = FileScannerUtils.getSiblingFiles(context, initialVideoUri, videoExtensions)
            allVideos.forEach { uri -> exoPlayer.addMediaItem(MediaItem.fromUri(uri)) }
            val targetName = Uri.parse(initialVideoUri).lastPathSegment
            val startIndex = if (targetName != null)
                allVideos.indexOfFirst { Uri.parse(it).lastPathSegment == targetName }.coerceAtLeast(0)
            else 0
            exoPlayer.seekTo(startIndex, 0L)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            hasNext = exoPlayer.hasNextMediaItem()
            hasPrev = exoPlayer.hasPreviousMediaItem()
        }
    }

    // ── Position polling ────────────────────────────────────────────────────
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                currentPosition = exoPlayer.currentPosition
                totalDuration   = exoPlayer.duration.coerceAtLeast(0L)
                delay(500)
            }
        }
    }

    // ── Auto-hide UI ────────────────────────────────────────────────────────
    LaunchedEffect(isUiVisible, hideTimerTrigger, isPlaying, isHoveringControls, optionsMenuExpanded) {
        if (isUiVisible && isPlaying && !isHoveringControls && !optionsMenuExpanded) {
            delay(3000)
            isUiVisible = false
        }
    }

    // ── Reset options screen when menu closes ───────────────────────────────
    LaunchedEffect(optionsMenuExpanded) {
        if (!optionsMenuExpanded) optionsScreen = "MAIN"
    }

    // ── Indicator timers ────────────────────────────────────────────────────
    LaunchedEffect(showSkipBackIndicator)    { if (showSkipBackIndicator)    { delay(600); showSkipBackIndicator    = false } }
    LaunchedEffect(showSkipForwardIndicator) { if (showSkipForwardIndicator) { delay(600); showSkipForwardIndicator = false } }
    LaunchedEffect(showPauseIndicator)       { if (showPauseIndicator)       { delay(600); showPauseIndicator       = false } }
    LaunchedEffect(showPlayIndicator)        { if (showPlayIndicator)        { delay(600); showPlayIndicator        = false } }

    // ── Thumbnail generation ────────────────────────────────────────────────
    // Re-runs whenever the active media item changes (next/prev skip).
    LaunchedEffect(currentVideoUri) {
        thumbnailCache.clear()
        thumbnailAspectRatio = 16f / 9f
        val uri = currentVideoUri ?: return@LaunchedEffect
        // Wait for ExoPlayer to report a valid duration (works paused or playing).
        while (exoPlayer.duration <= 0L) { delay(200) }
        val duration = exoPlayer.duration
        val count = 50
        val interval = duration / count
        val retriever = MediaMetadataRetriever()
        try {
            val (thumbW, thumbH) = withContext(Dispatchers.IO) {
                retriever.setDataSource(context, Uri.parse(uri))
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 160
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 90
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                // Swap dimensions for portrait-rotated videos
                if (rotation == 90 || rotation == 270) Pair(h, w) else Pair(w, h)
            }
            val aspect = thumbW.toFloat() / thumbH.toFloat().coerceAtLeast(1f)
            thumbnailAspectRatio = aspect
            val scaledW = 160
            val scaledH = (scaledW / aspect).toInt().coerceAtLeast(1)
            for (i in 0..count) {
                val ms = (i * interval).coerceAtMost(duration)
                val bmp = withContext(Dispatchers.IO) {
                    retriever.getFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (bmp != null) {
                    val scaled = withContext(Dispatchers.IO) {
                        val s = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true)
                        if (s !== bmp) bmp.recycle()
                        s
                    }
                    thumbnailCache[ms] = scaled.asImageBitmap()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        } finally {
            withContext(Dispatchers.IO) { retriever.release() }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    // ── UI ──────────────────────────────────────────────────────────────────
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
                .focusRequester(focusRequester)
                .focusable()
                .videoKeyboardControls(
                    exoPlayer             = exoPlayer,
                    volume                = volume,
                    onVolumeChange        = { volume = it },
                    onToggleFullscreen    = onToggleFullscreen,
                    onShowSkipBackIndicator    = { showSkipBackIndicator    = true },
                    onShowSkipForwardIndicator = { showSkipForwardIndicator = true },
                    onShowPauseIndicator  = { showPauseIndicator  = true },
                    onShowPlayIndicator   = { showPlayIndicator   = true },
                    onShowUi = { isUiVisible = true; hideTimerTrigger++ },
                    activity = activity
                )
        ) {

            // ── Video surface ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .videoGestures(
                        isUiVisible           = isUiVisible,
                        isPlaying             = isPlaying,
                        exoPlayer             = exoPlayer,
                        onUiVisibilityChange  = { isUiVisible = it },
                        onHideTimerTrigger    = { hideTimerTrigger++ },
                        onToggleFullscreen    = onToggleFullscreen,
                        onShowPauseIndicator  = { showPauseIndicator  = true },
                        onShowPlayIndicator   = { showPlayIndicator   = true },
                        onShowSkipBackIndicator    = { showSkipBackIndicator    = true },
                        onShowSkipForwardIndicator = { showSkipForwardIndicator = true },
                        onShowUi = { isUiVisible = true; hideTimerTrigger++ }
                    )
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).also { pv ->
                            pv.player        = exoPlayer
                            pv.useController = false
                            pv.resizeMode    = resizeMode.value
                            playerViewRef    = pv
                        }
                    },
                    update = { pv ->
                        pv.resizeMode = resizeMode.value
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ── Indicators ──────────────────────────────────────────────────
            AnimatedVisibility(visible = showPauseIndicator, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.PauseCircle, stringResource(R.string.video_paused),
                    tint = Color.White, modifier = Modifier.size(96.dp))
            }
            AnimatedVisibility(visible = showPlayIndicator, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.PlayCircle, stringResource(R.string.video_playing),
                    tint = Color.White, modifier = Modifier.size(96.dp))
            }
            AnimatedVisibility(visible = showSkipBackIndicator, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.3f).align(Alignment.CenterStart)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
            }
            AnimatedVisibility(visible = showSkipForwardIndicator, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.3f).align(Alignment.CenterEnd)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
            }

            // ── Bottom controls ─────────────────────────────────────────────
            AnimatedVisibility(
                visible  = isUiVisible,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.type == PointerType.Mouse }) {
                                        isHoveringControls = when (event.type) {
                                            PointerEventType.Enter -> true
                                            PointerEventType.Exit  -> false
                                            else -> isHoveringControls
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onTap = { hideTimerTrigger++ }) }
                ) {
                    // Preview card — zero layout height so it floats above the bar without
                    // pushing the Column upward. The layout modifier reports h=0 to the parent
                    // while placing the content at y=-contentHeight (above its anchor).
                    if (seekbarHoverFraction != null && totalDuration > 0L) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .layout { measurable, constraints ->
                                    val p = measurable.measure(constraints.copy(minHeight = 0))
                                    layout(constraints.maxWidth, 0) { p.place(0, -p.height) }
                                }
                        ) {
                            val fraction = seekbarHoverFraction!!
                            val hoverMs = (fraction * totalDuration).toLong()
                            val bitmap = thumbnailCache.keys
                                .minByOrNull { k -> if (k >= hoverMs) k - hoverMs else hoverMs - k }
                                ?.let { thumbnailCache[it] }
                            // Fit within a 240×135 box, never narrower than 80dp
                            val previewW = (135.dp * thumbnailAspectRatio).coerceIn(80.dp, 240.dp)
                            val previewImageH = previewW / thumbnailAspectRatio
                            val padDp = 8.dp
                            val thumbXDp = padDp + (maxWidth - padDp * 2) * fraction
                            val previewXDp = (thumbXDp - previewW / 2).coerceIn(0.dp, maxWidth - previewW)
                            Column(
                                modifier = Modifier
                                    .offset(x = previewXDp)
                                    .width(previewW)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xDD000000)),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(previewImageH)
                                    )
                                } else {
                                    Box(Modifier.fillMaxWidth().height(previewImageH).background(Color.DarkGray))
                                }
                                Text(
                                    text = formatTime(hoverMs),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Seekbar track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(horizontal = 8.dp)
                            .nonFocusable()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.type == PointerType.Mouse }) {
                                            when (event.type) {
                                                PointerEventType.Move -> seekbarHoverFraction =
                                                    (event.changes.first().position.x / size.width).coerceIn(0f, 1f)
                                                PointerEventType.Exit -> seekbarHoverFraction = null
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput(totalDuration) {
                                detectTapGestures { offset ->
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    currentPosition = (fraction * totalDuration).toLong()
                                    exoPlayer.seekTo(currentPosition)
                                    hideTimerTrigger++
                                }
                            }
                            .pointerInput("drag", totalDuration) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        seekbarHoverFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                        seekbarHoverFraction = fraction
                                        currentPosition = (fraction * totalDuration).toLong()
                                        exoPlayer.seekTo(currentPosition)
                                        hideTimerTrigger++
                                    },
                                    onDragEnd = { seekbarHoverFraction = null },
                                    onDragCancel = { seekbarHoverFraction = null }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val trackH = 3.dp.toPx()
                            val cy = size.height / 2f
                            val fraction = if (totalDuration > 0L) currentPosition.toFloat() / totalDuration else 0f
                            val activeX = (fraction * size.width).coerceIn(0f, size.width)
                            val thumbR = if (seekbarHoverFraction != null) 6.dp.toPx() else 4.dp.toPx()
                            drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, cy), Offset(size.width, cy), trackH, cap = StrokeCap.Round)
                            if (activeX > 0f) drawLine(Color.Red, Offset(0f, cy), Offset(activeX, cy), trackH, cap = StrokeCap.Round)
                            drawCircle(Color.White, thumbR, Offset(activeX, cy))
                        }
                    }

                    // Controls row
                    Row(modifier = Modifier.padding(8.dp)) {

                        // Play / Pause
                        IconButton(
                            onClick = {
                                if (isPlaying) { exoPlayer.pause(); showPauseIndicator = true }
                                else           { exoPlayer.play();  showPlayIndicator  = true }
                            },
                            modifier = Modifier.nonFocusable()
                        ) {
                            Icon(
                                imageVector     = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = stringResource(if (isPlaying) R.string.video_paused else R.string.video_playing),
                                tint   = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }

                        // Previous
                        IconButton(onClick = { if (hasPrev) exoPlayer.seekToPreviousMediaItem() }) {
                            Icon(Icons.Default.SkipPrevious, stringResource(R.string.media_previous),
                                tint = if (hasPrev) Color.White else Color.Gray,
                                modifier = Modifier.size(48.dp))
                        }

                        // Next
                        IconButton(onClick = { if (hasNext) exoPlayer.seekToNextMediaItem() }) {
                            Icon(Icons.Default.SkipNext, stringResource(R.string.media_next),
                                tint = if (hasNext) Color.White else Color.Gray,
                                modifier = Modifier.size(48.dp))
                        }

                        // Volume icon — tap to mute/unmute
                        IconButton(
                            onClick = { volume = if (volume > 0f) 0f else 1f },
                            modifier = Modifier.nonFocusable()
                        ) {
                            Icon(
                                imageVector = when {
                                    volume == 0f  -> Icons.AutoMirrored.Filled.VolumeOff
                                    volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                    else          -> Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = stringResource(R.string.video_volume),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Volume slider
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(24.dp)
                                .align(Alignment.CenterVertically)
                                .nonFocusable()
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        volume = (offset.x / size.width).coerceIn(0f, 1f)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, _ ->
                                        change.consume()
                                        volume = (change.position.x / size.width).coerceIn(0f, 1f)
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val trackH = 3.dp.toPx()
                                val cy = size.height / 2f
                                val activeX = (volume * size.width).coerceIn(0f, size.width)
                                val thumbR = 4.dp.toPx()
                                drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, cy), Offset(size.width, cy), trackH, cap = StrokeCap.Round)
                                if (activeX > 0f) drawLine(Color.White, Offset(0f, cy), Offset(activeX, cy), trackH, cap = StrokeCap.Round)
                                drawCircle(Color.White, thumbR, Offset(activeX, cy))
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        // Loop indicator
                        if (loopEnabled) {
                            Icon(Icons.Default.Repeat, stringResource(R.string.video_loop),
                                tint     = Color.Red,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.CenterVertically)
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        // Timestamp
                        Text(
                            text  = "${formatTime(currentPosition)} / ${formatTime(totalDuration)}",
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                        )

                        Spacer(Modifier.weight(1f))

                        // Speed badge (shown when not 1×)
                        if (playbackSpeed != 1f) {
                            Text(
                                text  = "${playbackSpeed}×",
                                color = Color.Red,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(end = 8.dp)
                            )
                        }

                        // ── Options button + multi-screen dropdown ──────────
                        Box {
                            IconButton(onClick = { optionsMenuExpanded = true }) {
                                Icon(Icons.Default.Settings, stringResource(R.string.options),
                                    tint = Color.White, modifier = Modifier.size(36.dp))
                            }

                            DropdownMenu(
                                expanded        = optionsMenuExpanded,
                                onDismissRequest = { optionsMenuExpanded = false }
                            ) {
                                when (optionsScreen) {

                                    // ── MAIN screen ───────────────────────
                                    "MAIN" -> {
                                        // Speed
                                        DropdownMenuItem(
                                            leadingIcon  = { Icon(Icons.Default.FastForward, null) },
                                            text = { Text(stringResource(R.string.menu_speed)) },
                                            trailingIcon = {
                                                Row {
                                                    Text("${playbackSpeed}×", color = Color.Gray)
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                                }
                                            },
                                            onClick = { optionsScreen = "SPEED" }
                                        )

                                        // Audio track
                                        DropdownMenuItem(
                                            leadingIcon  = { Icon(Icons.Default.Audiotrack, null) },
                                            text = { Text(stringResource(R.string.menu_audio_track)) },
                                            trailingIcon = {
                                                Row {
                                                    if (audioTracks.isNotEmpty()) {
                                                        Text(
                                                            audioTracks.firstOrNull { it.isSelected }?.label
                                                                ?: audioTracks.first().label,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                                }
                                            },
                                            onClick = { optionsScreen = "AUDIO" },
                                            enabled = audioTracks.size > 1
                                        )

                                        // Subtitles
                                        DropdownMenuItem(
                                            leadingIcon  = { Icon(Icons.Default.Subtitles, null) },
                                            text = { Text(stringResource(R.string.menu_subtitles)) },
                                            trailingIcon = {
                                                Row {
                                                    val sel = subtitleTracks.firstOrNull { it.isSelected }
                                                    Text(sel?.label ?: "Off", color = Color.Gray)
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                                }
                                            },
                                            onClick = { optionsScreen = "SUBTITLES" },
                                            enabled = subtitleTracks.isNotEmpty()
                                        )

                                        // Aspect ratio
                                        DropdownMenuItem(
                                            leadingIcon  = { Icon(Icons.Default.AspectRatio, null) },
                                            text = { Text(stringResource(R.string.menu_aspect_ratio)) },
                                            trailingIcon = {
                                                Row {
                                                    Text(stringResource(resizeMode.labelRes), color = Color.Gray)
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                                }
                                            },
                                            onClick = { optionsScreen = "ASPECT" }
                                        )

                                        HorizontalDivider()

                                        // Loop toggle
                                        DropdownMenuItem(
                                            leadingIcon  = { Icon(Icons.Default.Repeat, null,
                                                tint = if (loopEnabled) Color.Red else Color.Gray) },
                                            text = { Text(if (loopEnabled) stringResource(R.string.video_loop_on) else stringResource(R.string.video_loop_off)) },
                                            trailingIcon = {
                                                if (loopEnabled) Icon(Icons.Default.Check, null)
                                            },
                                            onClick = {
                                                loopEnabled = !loopEnabled
                                                optionsMenuExpanded = false
                                            }
                                        )
                                    }

                                    // ── SPEED screen ──────────────────────
                                    "SPEED" -> {
                                        // Back
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                            text = { Text(stringResource(R.string.back)) },
                                            onClick = { optionsScreen = "MAIN" }
                                        )
                                        HorizontalDivider()

                                        val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)
                                        speeds.forEach { speed ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(if (speed == 1f) stringResource(R.string.video_speed_normal) else "${speed}×")
                                                },
                                                trailingIcon = {
                                                    if (playbackSpeed == speed) Icon(Icons.Default.Check, null)
                                                },
                                                onClick = {
                                                    playbackSpeed       = speed
                                                    optionsMenuExpanded = false
                                                }
                                            )
                                        }
                                    }

                                    // ── AUDIO screen ──────────────────────
                                    "AUDIO" -> {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                            text = { Text(stringResource(R.string.back)) },
                                            onClick = { optionsScreen = "MAIN" }
                                        )
                                        HorizontalDivider()

                                        if (audioTracks.isEmpty()) {
                                            DropdownMenuItem(
                                                text    = { Text(stringResource(R.string.video_no_audio_tracks)) },
                                                enabled = false,
                                                onClick = {}
                                            )
                                        } else {
                                            audioTracks.forEachIndexed { i, track ->
                                                DropdownMenuItem(
                                                    text = { Text(track.label) },
                                                    trailingIcon = {
                                                        if (track.isSelected) Icon(Icons.Default.Check, null)
                                                    },
                                                    onClick = {
                                                        // Select this audio track group exclusively
                                                        val groups = exoPlayer.currentTracks.groups
                                                        val audioGroups = groups.filter {
                                                            it.type == C.TRACK_TYPE_AUDIO && it.isSupported
                                                        }
                                                        if (i < audioGroups.size) {
                                                            val override = androidx.media3.common.TrackSelectionOverride(
                                                                audioGroups[i].mediaTrackGroup, 0
                                                            )
                                                            exoPlayer.trackSelectionParameters =
                                                                exoPlayer.trackSelectionParameters
                                                                    .buildUpon()
                                                                    .setOverrideForType(override)
                                                                    .build()
                                                        }
                                                        optionsMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // ── SUBTITLES screen ──────────────────
                                    "SUBTITLES" -> {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                            text = { Text(stringResource(R.string.back)) },
                                            onClick = { optionsScreen = "MAIN" }
                                        )
                                        HorizontalDivider()

                                        // "Off" option — disable all text tracks
                                        val subtitlesOff = subtitleTracks.none { it.isSelected }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.off)) },
                                            trailingIcon = {
                                                if (subtitlesOff) Icon(Icons.Default.Check, null)
                                            },
                                            onClick = {
                                                exoPlayer.trackSelectionParameters =
                                                    exoPlayer.trackSelectionParameters
                                                        .buildUpon()
                                                        .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                                        .setPreferredTextLanguage(null)
                                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                        .build()
                                                optionsMenuExpanded = false
                                            }
                                        )

                                        subtitleTracks.forEachIndexed { i, track ->
                                            DropdownMenuItem(
                                                text = { Text(track.label) },
                                                trailingIcon = {
                                                    if (track.isSelected) Icon(Icons.Default.Check, null)
                                                },
                                                onClick = {
                                                    val groups = exoPlayer.currentTracks.groups
                                                    val textGroups = groups.filter {
                                                        it.type == C.TRACK_TYPE_TEXT && it.isSupported
                                                    }
                                                    if (i < textGroups.size) {
                                                        val override = androidx.media3.common.TrackSelectionOverride(
                                                            textGroups[i].mediaTrackGroup, 0
                                                        )
                                                        exoPlayer.trackSelectionParameters =
                                                            exoPlayer.trackSelectionParameters
                                                                .buildUpon()
                                                                .setOverrideForType(override)
                                                                .build()
                                                    }
                                                    optionsMenuExpanded = false
                                                }
                                            )
                                        }
                                    }

                                    // ── ASPECT screen ─────────────────────
                                    "ASPECT" -> {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                                            text = { Text(stringResource(R.string.back)) },
                                            onClick = { optionsScreen = "MAIN" }
                                        )
                                        HorizontalDivider()

                                        ResizeMode.entries.forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(mode.labelRes)) },
                                                trailingIcon = {
                                                    if (resizeMode == mode) Icon(Icons.Default.Check, null)
                                                },
                                                onClick = {
                                                    resizeMode          = mode
                                                    optionsMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Fullscreen button
                        IconButton(onClick = { onToggleFullscreen() }) {
                            Icon(Icons.Default.Fullscreen, stringResource(R.string.menu_fullscreen),
                                tint = Color.White, modifier = Modifier.size(64.dp))
                        }
                    }
                }
            }
        }
    }
}


// ── Utilities ─────────────────────────────────────────────────────────────────

@SuppressLint("DefaultLocale")
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}


// ── Gesture modifiers (unchanged) ─────────────────────────────────────────────

fun Modifier.videoGestures(
    isUiVisible: Boolean,
    isPlaying: Boolean,
    exoPlayer: ExoPlayer,
    onUiVisibilityChange: (Boolean) -> Unit,
    onHideTimerTrigger: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onShowPauseIndicator: () -> Unit,
    onShowPlayIndicator: () -> Unit,
    onShowSkipBackIndicator: () -> Unit,
    onShowSkipForwardIndicator: () -> Unit,
    onShowUi: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()

    var lastClickTime by remember { mutableStateOf(0L) }
    var pendingClickJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapX by remember { mutableStateOf(0f) }

    val currentIsUiVisible         by rememberUpdatedState(isUiVisible)
    val currentIsPlaying           by rememberUpdatedState(isPlaying)
    val currentOnUiVisibilityChange by rememberUpdatedState(onUiVisibilityChange)
    val currentOnHideTimerTrigger  by rememberUpdatedState(onHideTimerTrigger)
    val currentOnToggleFullscreen  by rememberUpdatedState(onToggleFullscreen)

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event       = awaitPointerEvent()
                    val firstChange = event.changes.firstOrNull() ?: continue
                    val isTouch     = event.changes.any { it.type == PointerType.Touch }
                    val isStylus    = event.changes.any { it.type == PointerType.Stylus }

                    if ((isTouch || isStylus) && event.type == PointerEventType.Press) {
                        firstChange.consume()
                        val currentTime = System.currentTimeMillis()
                        val tapX        = firstChange.position.x
                        val width       = size.width.toFloat()

                        if (currentTime - lastTapTime < 300) {
                            pendingClickJob?.cancel()
                            pendingClickJob = null
                            when {
                                tapX < width * 0.3f -> {
                                    val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                    exoPlayer.seekTo(newPos)
                                    onShowSkipBackIndicator()
                                    onShowUi()
                                }
                                tapX > width * 0.7f -> {
                                    val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                    exoPlayer.seekTo(newPos)
                                    onShowSkipForwardIndicator()
                                    onShowUi()
                                }
                                else -> {
                                    onShowUi()
                                    if (currentIsPlaying) { exoPlayer.pause(); onShowPauseIndicator() }
                                    else                  { exoPlayer.play();  onShowPlayIndicator()  }
                                }
                            }
                        } else {
                            pendingClickJob?.cancel()
                            pendingClickJob = scope.launch {
                                delay(300)
                                if (currentIsUiVisible) currentOnUiVisibilityChange(false)
                                else { currentOnUiVisibilityChange(true); currentOnHideTimerTrigger() }
                            }
                        }
                        lastTapTime = currentTime
                        lastTapX    = tapX
                    }
                }
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event       = awaitPointerEvent()
                    val firstChange = event.changes.firstOrNull() ?: continue
                    val isMouse     = event.changes.any { it.type == PointerType.Mouse }
                    val isPrimary   = event.buttons.isPrimaryPressed

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
                                if (currentIsPlaying) { exoPlayer.pause(); onShowPauseIndicator() }
                                else                  { exoPlayer.play();  onShowPlayIndicator()  }
                            }
                        }
                        lastClickTime = currentTime
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
}

fun Modifier.videoKeyboardControls(
    exoPlayer: ExoPlayer,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onShowSkipBackIndicator: () -> Unit,
    onShowSkipForwardIndicator: () -> Unit,
    onShowPauseIndicator: () -> Unit,
    onShowPlayIndicator: () -> Unit,
    onShowUi: () -> Unit,
    activity: Activity?
): Modifier = this.onKeyEvent { keyEvent ->
    if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
    when (keyEvent.key) {
        Key.Spacebar, Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause -> {
            onShowUi()
            if (exoPlayer.isPlaying) { exoPlayer.pause(); onShowPauseIndicator() }
            else                     { exoPlayer.play();  onShowPlayIndicator()  }
            true
        }
        Key.DirectionRight, Key.MediaFastForward -> {
            onShowUi()
            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
            onShowSkipForwardIndicator()
            true
        }
        Key.DirectionLeft, Key.MediaRewind -> {
            onShowUi()
            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
            onShowSkipBackIndicator()
            true
        }
        Key.DirectionUp -> {
            onVolumeChange((volume + 0.1f).coerceAtMost(1f)); true
        }
        Key.DirectionDown -> {
            onVolumeChange((volume - 0.1f).coerceAtLeast(0f)); true
        }
        Key.F -> { onToggleFullscreen(); true }
        Key.M -> { onVolumeChange(if (volume > 0f) 0f else 1f); true }
        Key.W -> {
            if (keyEvent.isCtrlPressed) {
                activity?.finish()
                true
            } else false
        }
        else  -> false
    }
}

fun Modifier.nonFocusable() = this.focusProperties { canFocus = false }