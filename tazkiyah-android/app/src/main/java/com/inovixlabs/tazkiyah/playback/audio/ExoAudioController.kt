package com.inovixlabs.tazkiyah.playback.audio

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.inovixlabs.tazkiyah.playback.PlaybackController
import com.inovixlabs.tazkiyah.playback.PlaybackError
import com.inovixlabs.tazkiyah.playback.PlayerState
import com.inovixlabs.tazkiyah.playback.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class AudioItem(
    val id: String,
    val url: String,
    val mimeType: String?,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val durationSec: Long,
)

/**
 * Talks to [PlaybackService] (ExoPlayer inside a MediaSessionService) through a MediaController,
 * so audio keeps playing with the screen off and shows lock-screen/notification controls.
 */
@Singleton
class ExoAudioController @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)

    /** The session player, for rendering the picture of `media.type = video` lectures. */
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var controller: MediaController? = null
    private var connecting: Deferred<MediaController>? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    /** Binds the playback service (also used to host the YouTube "শুধু শুনুন" session). */
    suspend fun connect(): MediaController {
        controller?.let { return it }
        val pending = connecting ?: scope.async {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync().await(context)
        }.also { connecting = it }
        val connected = try {
            pending.await()
        } finally {
            connecting = null
        }
        controller?.let { existing ->
            if (existing !== connected) connected.release()
            return existing
        }
        controller = connected
        connected.addListener(listener)
        _player.value = connected
        return connected
    }

    suspend fun load(item: AudioItem, startSec: Long, speed: Float, autoPlay: Boolean) {
        val c = connect()
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .apply { item.artworkUrl?.let { setArtworkUri(it.toUri()) } }
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(item.url)
            .apply { item.mimeType?.let { setMimeType(it) } }
            .setMediaMetadata(metadata)
            .build()
        _state.value = PlayerState(
            mediaId = item.id,
            isBuffering = autoPlay,
            positionSec = startSec,
            durationSec = item.durationSec,
            speed = speed,
        )
        c.setMediaItem(mediaItem, startSec * 1000)
        c.setPlaybackSpeed(speed)
        c.prepare()
        c.playWhenReady = autoPlay
        startTicker()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                refresh()
                delay(if (_state.value.isPlaying) 500 else 1500)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.div(1000) ?: _state.value.durationSec
        _state.value = PlayerState(
            mediaId = c.currentMediaItem?.mediaId,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            isEnded = c.playbackState == Player.STATE_ENDED,
            positionSec = c.currentPosition.coerceAtLeast(0) / 1000,
            durationSec = duration,
            speed = c.playbackParameters.speed,
            error = c.playerError?.let { e ->
                if (e.errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..2999) PlaybackError.Network
                else PlaybackError.Unknown
            },
        )
    }

    override fun play() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun seekTo(positionSec: Long) {
        controller?.seekTo(positionSec * 1000)
        refresh()
    }

    override fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    override fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
        ticker?.cancel()
        _state.value = PlayerState()
    }
}
