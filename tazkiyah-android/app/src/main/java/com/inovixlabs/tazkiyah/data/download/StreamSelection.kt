package com.inovixlabs.tazkiyah.data.download

import com.inovixlabs.tazkiyah.data.local.entity.DownloadKind

/** One downloadable stream, kept free of NewPipeExtractor types so the choices are unit-testable. */
data class StreamCandidate(
    val url: String,
    /** YouTube format id; 0 for a direct file. Names the part file, so a resumed part matches. */
    val itag: Int,
    val mimeType: String,
    val codec: String? = null,
    val bitrateKbps: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    /** 0 when not known. */
    val sizeBytes: Long = 0,
    /** False for YouTube's dubbed (often AI-translated) and audio-description tracks. */
    val isOriginalAudio: Boolean = true,
)

/** An option offered to the user: audio only, or video at [quality] (height) with sound. */
data class DownloadChoice(val kind: String, val quality: Int, val sizeBytes: Long)

/**
 * The streams one download is made of. Two parts (video + audio) are muxed into one MP4;
 * a single part is remuxed when [remux] is set (YouTube's fragmented m4a seeks badly otherwise).
 */
data class DownloadPlan(
    val parts: List<StreamCandidate>,
    val mimeType: String,
    val extension: String,
    val remux: Boolean,
)

object StreamSelector {
    /** Heights offered for video; lectures rarely need more than 720p. */
    val VIDEO_HEIGHTS = listOf(360, 480, 720)
    private const val MIN_SPEECH_KBPS = 40
    private const val MP4_AUDIO = "audio/mp4"
    private const val MP4_VIDEO = "video/mp4"

    /**
     * The original-language track (never a dub), preferring AAC in MP4, and the smallest bitrate
     * that is still clear for speech (YouTube's 48 kbps AAC) — lectures don't need music quality.
     */
    fun audio(streams: List<StreamCandidate>, mp4Only: Boolean): StreamCandidate? {
        val original = streams.filter { it.isOriginalAudio }.ifEmpty { streams }
        val mp4 = original.filter { it.mimeType == MP4_AUDIO }
        val pool = if (mp4Only) mp4 else mp4.ifEmpty { original }
        return pool.filter { it.bitrateKbps >= MIN_SPEECH_KBPS }.minByOrNull { it.bitrateKbps }
            ?: pool.maxByOrNull { it.bitrateKbps }
    }

    /** H.264 in MP4 only: every phone decodes it in hardware and MediaMuxer can write it. */
    fun video(streams: List<StreamCandidate>, height: Int): StreamCandidate? = streams
        .filter { it.mimeType == MP4_VIDEO && it.codec.orEmpty().startsWith("avc1") && it.height == height }
        .minWithOrNull(compareBy<StreamCandidate> { it.fps }.thenBy { it.bitrateKbps })

    /** Offered heights: the usual ones, or the best available up to 720p for old low-res uploads. */
    fun videoHeights(streams: List<StreamCandidate>): List<Int> {
        val available = streams
            .filter { it.mimeType == MP4_VIDEO && it.codec.orEmpty().startsWith("avc1") }
            .map { it.height }
            .toSet()
        val usual = VIDEO_HEIGHTS.filter { it in available }
        return usual.ifEmpty { listOfNotNull(available.filter { it in 1..720 }.maxOrNull()) }
    }

    fun choices(audio: List<StreamCandidate>, video: List<StreamCandidate>, durationSec: Long): List<DownloadChoice> {
        val audioOnly = audio(audio, mp4Only = false) ?: return emptyList()
        val result = mutableListOf(DownloadChoice(DownloadKind.AUDIO, 0, estimatedSize(audioOnly, durationSec)))
        val sound = audio(audio, mp4Only = true) ?: return result
        videoHeights(video).forEach { height ->
            val picture = video(video, height) ?: return@forEach
            result += DownloadChoice(
                DownloadKind.VIDEO,
                height,
                estimatedSize(picture, durationSec) + estimatedSize(sound, durationSec),
            )
        }
        return result
    }

    fun plan(audio: List<StreamCandidate>, video: List<StreamCandidate>, kind: String, quality: Int): DownloadPlan? =
        if (kind == DownloadKind.AUDIO) {
            audio(audio, mp4Only = false)?.let { a ->
                val mp4 = a.mimeType == MP4_AUDIO
                DownloadPlan(listOf(a), a.mimeType, if (mp4) "m4a" else "webm", remux = mp4)
            }
        } else {
            val picture = video(video, quality)
            val sound = audio(audio, mp4Only = true)
            if (picture == null || sound == null) null
            else DownloadPlan(listOf(picture, sound), MP4_VIDEO, "mp4", remux = true)
        }

    fun estimatedSize(stream: StreamCandidate, durationSec: Long): Long =
        if (stream.sizeBytes > 0) stream.sizeBytes else stream.bitrateKbps * 1000L / 8 * durationSec
}
