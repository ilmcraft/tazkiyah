package com.inovixlabs.tazkiyah.data.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Copies the first audio/video track of each input into one plain MP4 — YouTube serves picture and
 * sound as separate fragmented files. No re-encoding, so it takes seconds even for long lectures.
 */
object MediaRemuxer {
    private const val MIN_BUFFER = 2 * 1024 * 1024

    private class Track(val extractor: MediaExtractor, val muxerIndex: Int, val maxSample: Int)

    fun remux(inputs: List<File>, output: File) {
        val extractors = mutableListOf<MediaExtractor>()
        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val tracks = inputs.map { file ->
                val extractor = MediaExtractor().also { extractors += it }
                extractor.setDataSource(file.path)
                val index = (0 until extractor.trackCount).firstOrNull { i ->
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                    mime.startsWith("audio/") || mime.startsWith("video/")
                } ?: error("No media track in ${file.name}")
                extractor.selectTrack(index)
                val format = extractor.getTrackFormat(index)
                val maxSample = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    0
                }
                Track(extractor, muxer.addTrack(format), maxSample)
            }
            muxer.start()
            val buffer = ByteBuffer.allocateDirect(maxOf(MIN_BUFFER, tracks.maxOf { it.maxSample }))
            val info = MediaCodec.BufferInfo()
            val pending = tracks.toMutableList()
            while (pending.isNotEmpty()) {
                // Interleave by timestamp, so playback never waits for a far-away chunk.
                val track = pending.minBy { it.extractor.sampleTime }
                val size = track.extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    pending.remove(track)
                    continue
                }
                val keyFrame = track.extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                info.set(0, size, track.extractor.sampleTime, if (keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(track.muxerIndex, buffer, info)
                track.extractor.advance()
            }
            muxer.stop()
        } finally {
            extractors.forEach { it.release() }
            muxer.release()
        }
    }
}
