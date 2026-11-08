package com.inovixlabs.tazkiyah.data.download

import com.inovixlabs.tazkiyah.data.local.entity.DownloadKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Streams as YouTube served them for a real lecture (with an AI-dubbed English track). */
class StreamSelectorTest {

    private fun audio(itag: Int, mime: String, kbps: Int, size: Long, original: Boolean = true) =
        StreamCandidate(url = "a$itag-$original", itag = itag, mimeType = mime, bitrateKbps = kbps, sizeBytes = size, isOriginalAudio = original)

    private fun video(itag: Int, height: Int, codec: String, mime: String = "video/mp4", fps: Int = 30, kbps: Int = 500, size: Long = 0) =
        StreamCandidate(url = "v$itag", itag = itag, mimeType = mime, codec = codec, bitrateKbps = kbps, height = height, fps = fps, sizeBytes = size)

    private val dubbedAndOriginal = listOf(
        audio(139, "audio/mp4", 48, 10_246_742, original = false),
        audio(139, "audio/mp4", 48, 10_246_367),
        audio(140, "audio/mp4", 128, 27_192_332, original = false),
        audio(140, "audio/mp4", 128, 27_191_186),
        audio(249, "audio/webm", 50, 12_436_034, original = false),
        audio(251, "audio/webm", 160, 24_439_184),
    )

    private val videos = listOf(
        video(137, 1080, "avc1.640028", size = 336_190_026),
        video(136, 720, "avc1.4D401F", size = 85_530_636),
        video(397, 480, "av01.0.04M.08", size = 71_904_432),
        video(134, 360, "avc1.4D401E", size = 31_475_297),
        video(243, 360, "vp9", mime = "video/webm", size = 58_598_055),
        video(160, 144, "avc1.4D400C", size = 11_065_690),
    )

    @Test
    fun `audio is the original track, never the dub`() {
        val chosen = StreamSelector.audio(dubbedAndOriginal, mp4Only = false)!!
        assertTrue(chosen.isOriginalAudio)
        assertEquals(139, chosen.itag)
    }

    @Test
    fun `audio prefers the smallest clear AAC stream`() {
        val streams = listOf(audio(140, "audio/mp4", 128, 0), audio(139, "audio/mp4", 48, 0), audio(599, "audio/mp4", 30, 0))
        assertEquals(139, StreamSelector.audio(streams, mp4Only = true)!!.itag)
    }

    @Test
    fun `audio falls back to opus only when no AAC exists`() {
        val opus = listOf(audio(251, "audio/webm", 160, 0), audio(250, "audio/webm", 70, 0))
        assertEquals(250, StreamSelector.audio(opus, mp4Only = false)!!.itag)
        assertNull(StreamSelector.audio(opus, mp4Only = true))
    }

    @Test
    fun `single unnamed track counts as original`() {
        val plain = listOf(audio(139, "audio/mp4", 48, 0))
        assertEquals(139, StreamSelector.audio(plain, mp4Only = true)!!.itag)
    }

    @Test
    fun `video uses H264 only, skipping AV1 and VP9`() {
        assertEquals(listOf(360, 720), StreamSelector.videoHeights(videos))
        assertNull(StreamSelector.video(videos, 480))
        assertEquals(134, StreamSelector.video(videos, 360)!!.itag)
    }

    @Test
    fun `30 fps is preferred over 60 fps`() {
        val list = listOf(video(298, 720, "avc1.4D4020", fps = 60, kbps = 900), video(136, 720, "avc1.4D401F", fps = 30, kbps = 600))
        assertEquals(136, StreamSelector.video(list, 720)!!.itag)
    }

    @Test
    fun `old low-resolution upload still offers its best video`() {
        val old = listOf(video(160, 144, "avc1.4D400C"), video(133, 240, "avc1.4D4015"))
        assertEquals(listOf(240), StreamSelector.videoHeights(old))
    }

    @Test
    fun `choices list audio first, then each height with sound included`() {
        val choices = StreamSelector.choices(dubbedAndOriginal, videos, durationSec = 1680)
        assertEquals(
            listOf(
                DownloadChoice(DownloadKind.AUDIO, 0, 10_246_367),
                DownloadChoice(DownloadKind.VIDEO, 360, 31_475_297 + 10_246_367),
                DownloadChoice(DownloadKind.VIDEO, 720, 85_530_636 + 10_246_367),
            ),
            choices,
        )
    }

    @Test
    fun `size is estimated from the bitrate when unknown`() {
        assertEquals(48_000L / 8 * 100, StreamSelector.estimatedSize(audio(139, "audio/mp4", 48, 0), 100))
    }

    @Test
    fun `video plan muxes picture and original sound into mp4`() {
        val plan = StreamSelector.plan(dubbedAndOriginal, videos, DownloadKind.VIDEO, 360)!!
        assertEquals(listOf(134, 139), plan.parts.map { it.itag })
        assertTrue(plan.parts[1].isOriginalAudio)
        assertEquals("mp4", plan.extension)
        assertTrue(plan.remux)
    }

    @Test
    fun `audio plan keeps opus as-is but remuxes AAC`() {
        val aac = StreamSelector.plan(dubbedAndOriginal, videos, DownloadKind.AUDIO, 0)!!
        assertEquals("m4a", aac.extension)
        assertTrue(aac.remux)
        val opus = StreamSelector.plan(listOf(audio(251, "audio/webm", 160, 0)), emptyList(), DownloadKind.AUDIO, 0)!!
        assertEquals("webm", opus.extension)
        assertFalse(opus.remux)
    }

    @Test
    fun `no plan when the chosen height is gone`() {
        assertNull(StreamSelector.plan(dubbedAndOriginal, videos, DownloadKind.VIDEO, 480))
    }
}
