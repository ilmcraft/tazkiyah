package com.inovixlabs.tazkiyah.playback.youtube

import com.inovixlabs.tazkiyah.playback.PlaybackController
import com.inovixlabs.tazkiyah.playback.PlaybackError
import com.inovixlabs.tazkiyah.playback.PlayerState
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.toFloat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Drives the YouTube IFrame player that [FocusedYouTubePlayer] hosts. The view can come and go
 * (e.g. the activity is recreated); the current video is re-cued when a new player gets ready.
 */
@Singleton
class YouTubeController @Inject constructor() : PlaybackController {

    private data class Load(val lectureId: String, val videoId: String, val startSec: Long, val autoPlay: Boolean)

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var player: YouTubePlayer? = null
    private var pending: Load? = null
    private var current: Load? = null
    private var desiredSpeed = 1f

    val listener = object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            player = youTubePlayer
            val toApply = pending ?: current?.copy(startSec = _state.value.positionSec, autoPlay = false)
            pending = null
            toApply?.let(::apply)
        }

        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            val playing = state == PlayerConstants.PlayerState.PLAYING
            _state.update {
                it.copy(
                    isPlaying = playing,
                    isBuffering = state == PlayerConstants.PlayerState.BUFFERING,
                    isEnded = state == PlayerConstants.PlayerState.ENDED,
                    error = if (playing) null else it.error,
                )
            }
            if (playing && abs(_state.value.speed - desiredSpeed) > 0.01f) {
                youTubePlayer.setPlaybackRate(desiredSpeed.toPlaybackRate())
            }
        }

        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
            _state.update { it.copy(positionSec = second.toLong()) }
        }

        override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
            if (duration > 0) _state.update { it.copy(durationSec = duration.toLong()) }
        }

        override fun onPlaybackRateChange(youTubePlayer: YouTubePlayer, playbackRate: PlayerConstants.PlaybackRate) {
            _state.update { it.copy(speed = playbackRate.toFloat()) }
        }

        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
            val mapped = when (error) {
                PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER -> PlaybackError.NotEmbeddable
                PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> PlaybackError.NotFound
                else -> PlaybackError.Unknown
            }
            _state.update { it.copy(isPlaying = false, isBuffering = false, error = mapped) }
        }
    }

    fun load(lectureId: String, videoId: String, startSec: Long, speed: Float, autoPlay: Boolean) {
        desiredSpeed = speed
        _state.value = PlayerState(mediaId = lectureId, isBuffering = autoPlay, positionSec = startSec, speed = speed)
        val load = Load(lectureId, videoId, startSec, autoPlay)
        if (player == null) pending = load else apply(load)
    }

    private fun apply(load: Load) {
        val p = player ?: return
        current = load
        if (load.autoPlay) p.loadVideo(load.videoId, load.startSec.toFloat())
        else p.cueVideo(load.videoId, load.startSec.toFloat())
    }

    /** The hosting view was destroyed; keep the position so a new view can continue. */
    fun onViewReleased() {
        player = null
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    override fun play() {
        val p = player
        if (p == null) {
            pending = (pending ?: current?.copy(startSec = _state.value.positionSec))?.copy(autoPlay = true)
            return
        }
        if (_state.value.isEnded) p.seekTo(0f)
        p.play()
    }

    override fun pause() {
        player?.pause()
        pending = pending?.copy(autoPlay = false)
    }

    override fun seekTo(positionSec: Long) {
        player?.seekTo(positionSec.toFloat())
        _state.update { it.copy(positionSec = positionSec, isEnded = false) }
    }

    override fun setSpeed(speed: Float) {
        desiredSpeed = speed
        player?.setPlaybackRate(speed.toPlaybackRate())
        _state.update { it.copy(speed = speed) }
    }

    override fun stop() {
        player?.pause()
        pending = null
        current = null
        _state.value = PlayerState()
    }
}

private fun Float.toPlaybackRate(): PlayerConstants.PlaybackRate = when {
    this <= 0.3f -> PlayerConstants.PlaybackRate.RATE_0_25
    this <= 0.6f -> PlayerConstants.PlaybackRate.RATE_0_5
    this <= 0.8f -> PlayerConstants.PlaybackRate.RATE_0_75
    this <= 1.1f -> PlayerConstants.PlaybackRate.RATE_1
    this <= 1.3f -> PlayerConstants.PlaybackRate.RATE_1_25
    this <= 1.6f -> PlayerConstants.PlaybackRate.RATE_1_5
    this <= 1.8f -> PlayerConstants.PlaybackRate.RATE_1_75
    else -> PlayerConstants.PlaybackRate.RATE_2
}
