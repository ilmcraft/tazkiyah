package com.inovixlabs.tazkiyah.data.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedStreams(
    val durationSec: Long,
    val audio: List<StreamCandidate>,
    val video: List<StreamCandidate>,
)

/**
 * Finds the file URLs behind a YouTube lecture with NewPipeExtractor, so it can be saved on the
 * phone. This is against YouTube's terms (personal build only, see CLAUDE.md) and breaks when
 * YouTube changes; the fix is usually a newer extractor version in libs.versions.toml.
 */
@Singleton
class StreamResolver @Inject constructor(client: OkHttpClient) {

    private val http = client

    init {
        NewPipe.init(NewPipeHttp(client), Localization("bn", "BD"))
    }

    suspend fun youtube(videoId: String): ResolvedStreams = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        ResolvedStreams(
            durationSec = info.duration,
            audio = info.audioStreams.mapNotNull { it.toCandidate() },
            video = info.videoOnlyStreams.mapNotNull { it.toCandidate() },
        )
    }

    /** Size of a direct media file (0 when the server doesn't say). */
    suspend fun contentLength(url: String): Long = withContext(Dispatchers.IO) {
        val request = okhttp3.Request.Builder().url(url).head().build()
        http.newCall(request).execute().use { r -> if (r.isSuccessful) r.header("Content-Length")?.toLongOrNull() ?: 0 else 0 }
    }

    private fun AudioStream.toCandidate(): StreamCandidate? {
        if (!isUrl || deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP) return null
        return StreamCandidate(
            url = content,
            itag = itag,
            mimeType = format?.mimeType ?: return null,
            codec = codec,
            bitrateKbps = averageBitrate.takeIf { it > 0 } ?: (bitrate / 1000),
            sizeBytes = itagItem?.contentLength?.takeIf { it > 0 } ?: 0,
            isOriginalAudio = audioTrackType == null || audioTrackType == AudioTrackType.ORIGINAL,
        )
    }

    private fun VideoStream.toCandidate(): StreamCandidate? {
        if (!isUrl || deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP) return null
        return StreamCandidate(
            url = content,
            itag = itag,
            mimeType = format?.mimeType ?: return null,
            codec = codec,
            bitrateKbps = bitrate / 1000,
            height = height,
            fps = fps,
            sizeBytes = itagItem?.contentLength?.takeIf { it > 0 } ?: 0,
        )
    }
}

/** NewPipeExtractor's HTTP layer on top of the app's OkHttp client. */
private class NewPipeHttp(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
