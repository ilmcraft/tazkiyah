package com.inovixlabs.tazkiyah.playback.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.inovixlabs.tazkiyah.MainActivity
import com.inovixlabs.tazkiyah.playback.youtube.YouTubeSessionBridge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the ExoPlayer audio session (background playback, notification, lock screen, headset
 * buttons) and — in YouTube "শুধু শুনুন" mode — a second session that bridges notification
 * controls to the in-app YouTube player.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var youTubeBridge: YouTubeSessionBridge

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var audioSession: MediaSession? = null
    private var youTubeSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        audioSession = MediaSession.Builder(this, player)
            .setId("audio")
            .setSessionActivity(openAppIntent())
            .build()

        scope.launch {
            youTubeBridge.player.collect { ytPlayer ->
                youTubeSession?.let {
                    removeSession(it)
                    it.release()
                }
                youTubeSession = ytPlayer?.let { p ->
                    MediaSession.Builder(this@PlaybackService, p)
                        .setId("youtube")
                        .setSessionActivity(openAppIntent())
                        .build()
                        .also { addSession(it) }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = audioSession

    /**
     * Swiped away from Recents: audio files keep playing, but the YouTube player lives in the
     * activity's WebView and dies with it, so everything else is paused and the service stops.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val audio = audioSession?.player
        val audioOngoing = audio != null && audio.playWhenReady && audio.mediaItemCount > 0 &&
            audio.playbackState != Player.STATE_ENDED
        if (!audioOngoing) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        youTubeSession?.let {
            removeSession(it)
            it.release()
        }
        youTubeSession = null
        audioSession?.run {
            player.release()
            release()
        }
        audioSession = null
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
