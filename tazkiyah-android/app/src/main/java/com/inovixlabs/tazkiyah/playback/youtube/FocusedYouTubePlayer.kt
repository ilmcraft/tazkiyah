package com.inovixlabs.tazkiyah.playback.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.playback.PlayerState
import com.inovixlabs.tazkiyah.playback.video.FocusedVideoFrame
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * The only way a YouTube video is shown in this app (docs/PLAN.md, "ইউটিউব প্লেয়ার"):
 * 1. IFrame options hide YouTube's own UI (controls 0, rel 0, iv_load_policy 3, fs 0).
 * 2. [FocusedVideoFrame]'s touch shield takes every touch — logo, title, end cards never get a
 *    tap; the View container below also refuses touches, focus and accessibility.
 * 3. Its cover card hides the video whenever it is paused, ended or failed (no "More videos").
 * 4. MainActivity blocks any intent that would still try to open YouTube.
 */
@Composable
fun FocusedYouTubePlayer(
    controller: YouTubeController,
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
    val context = LocalContext.current
    val container = remember { TouchBlockingFrameLayout(context) }
    val playerView = remember {
        (LayoutInflater.from(context).inflate(R.layout.focused_youtube_player, container, false) as YouTubePlayerView)
            .also { container.addView(it) }
    }

    DisposableEffect(playerView) {
        val options = IFramePlayerOptions.Builder(context)
            .controls(0)
            .rel(0)
            .ivLoadPolicy(3)
            .fullscreen(0)
            .ccLoadPolicy(0)
            .build()
        // Keep playing when the app is minimized or the screen turns off: background playback is
        // on and the view is deliberately NOT a lifecycle observer (its ON_STOP would pause).
        // Notification / lock-screen controls come from YouTubeSessionPlayer via PlaybackService.
        playerView.enableBackgroundPlayback(true)
        playerView.initialize(controller.listener, true, options)
        onDispose {
            controller.onViewReleased()
            playerView.release()
        }
    }

    FocusedVideoFrame(
        lecture = lecture,
        state = state,
        listenOnly = listenOnly,
        fullscreen = fullscreen,
        nextInSeries = nextInSeries,
        coverWhenPaused = true,
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
            factory = {
                (container.parent as? ViewGroup)?.removeView(container)
                container
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** View-level shield under the Compose shield: no touch, key focus or accessibility reaches YouTube. */
private class TouchBlockingFrameLayout(context: Context) : FrameLayout(context) {
    init {
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = true
}
