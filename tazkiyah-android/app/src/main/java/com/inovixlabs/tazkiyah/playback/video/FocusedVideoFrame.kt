package com.inovixlabs.tazkiyah.playback.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.SpeakerAvatar
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.feature.player.SeekBar
import com.inovixlabs.tazkiyah.feature.player.SpeedButton
import com.inovixlabs.tazkiyah.feature.player.TransportRow
import com.inovixlabs.tazkiyah.feature.player.errorText
import com.inovixlabs.tazkiyah.playback.PlayerState
import com.inovixlabs.tazkiyah.playback.youTubeThumbnail
import kotlinx.coroutines.delay

/**
 * Everything drawn over a video (docs/PLAN.md, "ইউটিউব প্লেয়ার"), shared by streaming YouTube and
 * saved files: a full-size touch shield (the video itself never gets a tap), our own cover card,
 * and our own controls with fullscreen.
 *
 * @param coverWhenPaused YouTube is hidden whenever it isn't playing (its paused/ended screens
 *   advertise other videos); a file can show its paused frame.
 */
@Composable
fun FocusedVideoFrame(
    lecture: LectureRow,
    state: PlayerState,
    listenOnly: Boolean,
    fullscreen: Boolean,
    nextInSeries: LectureRow?,
    coverWhenPaused: Boolean,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onReplay: () -> Unit,
    onPlayNext: (LectureRow) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    video: @Composable () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(3_000)
            controlsVisible = false
        }
    }
    val showCover = listenOnly || state.isEnded || state.error != null ||
        (coverWhenPaused && !state.isPlaying && !state.isBuffering)

    Box(modifier.background(Color.Black)) {
        video()
        // The touch shield.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset -> onSeekBy(if (offset.x < size.width / 2) -10 else 10) },
                    )
                },
        )
        if (showCover) {
            CoverCard(
                lecture = lecture,
                state = state,
                listenOnly = listenOnly,
                nextInSeries = nextInSeries,
                onReplay = onReplay,
                onPlayNext = onPlayNext,
            )
        }
        AnimatedVisibility(
            visible = controlsVisible || showCover || !state.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            OverlayControls(
                state = state,
                fullscreen = fullscreen,
                showTransport = !state.isEnded,
                onPlayPause = onPlayPause,
                onSeekBy = onSeekBy,
                onSeekTo = onSeekTo,
                onSpeed = onSpeed,
                onToggleFullscreen = onToggleFullscreen,
            )
        }
    }
}

/** A video file (saved on the phone, or a `media.type = video` lecture) inside [FocusedVideoFrame]. */
@Composable
fun FocusedFileVideoPlayer(
    player: Player?,
    lecture: LectureRow,
    state: PlayerState,
    listenOnly: Boolean,
    fullscreen: Boolean,
    nextInSeries: LectureRow?,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onReplay: () -> Unit,
    onPlayNext: (LectureRow) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusedVideoFrame(
        lecture = lecture,
        state = state,
        listenOnly = listenOnly,
        fullscreen = fullscreen,
        nextInSeries = nextInSeries,
        coverWhenPaused = false,
        onPlayPause = onPlayPause,
        onSeekBy = onSeekBy,
        onSeekTo = onSeekTo,
        onSpeed = onSpeed,
        onReplay = onReplay,
        onPlayNext = onPlayNext,
        onToggleFullscreen = onToggleFullscreen,
        modifier = modifier,
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    keepScreenOn = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BoxScope.CoverCard(
    lecture: LectureRow,
    state: PlayerState,
    listenOnly: Boolean,
    nextInSeries: LectureRow?,
    onReplay: () -> Unit,
    onPlayNext: (LectureRow) -> Unit,
) {
    val artwork = lecture.speakerPhotoUrl ?: lecture.lecture.youtubeId?.let(::youTubeThumbnail)
    Box(
        Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary),
                ),
            )
            // Swallow taps that land on the card itself.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(24.dp).alpha(0.35f),
            )
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.error != null -> {
                    Text(
                        errorText(state.error),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
                state.isEnded -> {
                    Text(
                        lecture.lecture.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onReplay) { Text(stringResource(R.string.listen_again)) }
                        if (nextInSeries != null) {
                            FilledTonalButton(onClick = { onPlayNext(nextInSeries) }) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.next_part))
                            }
                        }
                    }
                }
                listenOnly -> {
                    SpeakerAvatar(lecture.speakerName.orEmpty(), lecture.speakerPhotoUrl, 72.dp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            " " + stringResource(R.string.listen_only_active),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
                else -> Unit // paused / not started: the overlay's play button sits on top
            }
        }
    }
}

@Composable
private fun OverlayControls(
    state: PlayerState,
    fullscreen: Boolean,
    showTransport: Boolean,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
        if (showTransport) {
            TransportRow(
                state = state,
                onPlayPause = onPlayPause,
                onSeekBy = onSeekBy,
                onDark = true,
                big = fullscreen,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeekBar(state, onSeekTo, Modifier.weight(1f), onDark = true)
                SpeedButton(state.speed, onSpeed, onDark = true)
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = stringResource(if (fullscreen) R.string.exit_fullscreen else R.string.fullscreen),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
