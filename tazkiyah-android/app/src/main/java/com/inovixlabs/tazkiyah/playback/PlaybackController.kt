package com.inovixlabs.tazkiyah.playback

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlayerState(
    /** Lecture this state belongs to; null after stop(). Progress is only saved when it matches. */
    val mediaId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val positionSec: Long = 0,
    val durationSec: Long = 0,
    val speed: Float = 1f,
    val error: PlaybackError? = null,
)

enum class PlaybackError { NotEmbeddable, NotFound, Network, Unknown }

val SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * One interface for both players (docs/PLAN.md, "একটাই প্লেব্যাক কন্ট্রোলার"); the UI never
 * knows whether ExoPlayer or the YouTube IFrame player is behind it.
 */
interface PlaybackController {
    val state: StateFlow<PlayerState>
    fun play()
    fun pause()
    fun seekTo(positionSec: Long)
    fun setSpeed(speed: Float)

    /** Stops playback and forgets the current media. */
    fun stop()
}

fun PlaybackController.seekBy(deltaSec: Long) {
    val s = state.value
    val max = if (s.durationSec > 0) s.durationSec else Long.MAX_VALUE
    seekTo((s.positionSec + deltaSec).coerceIn(0, max))
}

internal suspend fun <T> ListenableFuture<T>.await(context: Context): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: ExecutionException) {
                cont.resumeWithException(e.cause ?: e)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
        cont.invokeOnCancellation { cancel(false) }
    }
