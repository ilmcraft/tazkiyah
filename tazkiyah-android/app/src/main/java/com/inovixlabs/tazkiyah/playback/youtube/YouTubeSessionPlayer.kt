package com.inovixlabs.tazkiyah.playback.youtube

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Media3 Player facade over [YouTubeController], so a MediaSession can show notification /
 * lock-screen controls for the YouTube "শুধু শুনুন" mode (docs/PLAN.md, ফেজ ৪).
 */
@OptIn(UnstableApi::class)
class YouTubeSessionPlayer(
    private val controller: YouTubeController,
    val mediaId: String,
    private val metadata: MediaMetadata,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val commands = Player.Commands.Builder()
        .addAll(
            COMMAND_PLAY_PAUSE,
            COMMAND_STOP,
            COMMAND_GET_CURRENT_MEDIA_ITEM,
            COMMAND_GET_METADATA,
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_BACK,
            COMMAND_SEEK_FORWARD,
        )
        .build()

    /** Call whenever the YouTube player's state changed. */
    fun refresh() = invalidateState()

    override fun getState(): State {
        val s = controller.state.value
        val item = MediaItemData.Builder(mediaId)
            .setMediaItem(MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setDurationUs(if (s.durationSec > 0) s.durationSec * 1_000_000 else C.TIME_UNSET)
            .setIsSeekable(true)
            .build()
        val playbackState = when {
            s.isEnded -> STATE_ENDED
            s.isBuffering -> STATE_BUFFERING
            else -> STATE_READY
        }
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(s.isPlaying || s.isBuffering, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(s.positionSec * 1000)
            .setPlaybackParameters(PlaybackParameters(s.speed))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) controller.play() else controller.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        controller.seekTo(positionMs.coerceAtLeast(0) / 1000)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        controller.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}

/** Hands the current listen-only session player to [com.inovixlabs.tazkiyah.playback.audio.PlaybackService]. */
@Singleton
class YouTubeSessionBridge @Inject constructor() {
    private val _player = MutableStateFlow<YouTubeSessionPlayer?>(null)
    val player: StateFlow<YouTubeSessionPlayer?> = _player.asStateFlow()

    fun publish(player: YouTubeSessionPlayer?) {
        _player.value = player
    }

    fun refresh() {
        _player.value?.refresh()
    }
}
