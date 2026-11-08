package com.inovixlabs.tazkiyah.playback

import android.content.Context
import android.media.AudioManager

/** Which speaker icon the volume button shows. */
enum class VolumeLevel { Off, Low, High }

fun volumeLevel(level: Int, max: Int): VolumeLevel = when {
    level <= 0 -> VolumeLevel.Off
    level * 2 < max -> VolumeLevel.Low
    else -> VolumeLevel.High
}

/** Where the mute button goes when the volume was simply turned down to 0: about half way. */
fun unmuteLevel(max: Int): Int = ((max + 1) / 2).coerceAtLeast(1)

/**
 * The phone's media volume (STREAM_MUSIC), the same one the side buttons change. ExoPlayer and the
 * YouTube WebView both play on it, so one control covers every lecture, saved or streaming.
 */
class MediaVolume(context: Context) {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    /** False where the volume can't be changed (fixed-volume devices); the control is hidden there. */
    val adjustable: Boolean = audio != null && !audio.isVolumeFixed

    val max: Int = audio?.getStreamMaxVolume(STREAM)?.coerceAtLeast(1) ?: 1

    /** 0 while muted. */
    fun level(): Int = audio?.getStreamVolume(STREAM) ?: 0

    /** No system volume panel: our own slider is on screen. A level above 0 also unmutes. */
    fun setLevel(level: Int) {
        val a = audio ?: return
        runCatching { a.setStreamVolume(STREAM, level.coerceIn(0, max), 0) }
    }

    /**
     * Mutes without forgetting the level, so unmuting (here or with the side buttons) returns to it.
     * At 0 without a mute, goes to half volume.
     */
    fun toggleMute() {
        val a = audio ?: return
        runCatching {
            if (level() > 0) {
                a.adjustStreamVolume(STREAM, AudioManager.ADJUST_MUTE, 0)
            } else {
                if (a.isStreamMute(STREAM)) a.adjustStreamVolume(STREAM, AudioManager.ADJUST_UNMUTE, 0)
                if (level() == 0) setLevel(unmuteLevel(max))
            }
        }
    }

    companion object {
        const val STREAM = AudioManager.STREAM_MUSIC

        /** Sent by the system when any stream's volume changes (side buttons, other apps). */
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val ACTION_MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"
    }
}
