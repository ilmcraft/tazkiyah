package com.inovixlabs.tazkiyah.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.inovixlabs.tazkiyah.data.local.dao.CatalogDao
import com.inovixlabs.tazkiyah.data.local.dao.DownloadDao
import com.inovixlabs.tazkiyah.data.local.entity.DownloadEntity
import com.inovixlabs.tazkiyah.data.local.entity.DownloadError
import com.inovixlabs.tazkiyah.data.local.entity.DownloadKind
import com.inovixlabs.tazkiyah.data.local.entity.DownloadStatus
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.local.entity.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A finished download the player can use instead of streaming. */
data class SavedMedia(val file: File, val mimeType: String, val isVideo: Boolean)

/** A failure with a [DownloadError] code the UI can explain. */
class DownloadFailure(val code: String) : Exception(code)

fun downloadErrorOf(e: Throwable): String = when (e) {
    is DownloadFailure -> e.code
    is IOException -> DownloadError.NETWORK
    is ExtractionException -> DownloadError.UNAVAILABLE
    else -> DownloadError.UNKNOWN
}

/**
 * Lectures saved on the phone (docs/PLAN.md, ফেজ ৫). Files live in app-private storage
 * (`files/downloads/`), so they need no permission and leave with the app.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val catalog: CatalogDao,
    private val resolver: StreamResolver,
) {
    val directory: File get() = File(context.filesDir, "downloads").apply { mkdirs() }

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun observe(lectureId: String): Flow<DownloadEntity?> = dao.observe(lectureId)

    /** What can be saved for this lecture, with sizes (needs the network). */
    suspend fun choices(lectureId: String): List<DownloadChoice> {
        val row = catalog.lectureNow(lectureId) ?: throw DownloadFailure(DownloadError.UNAVAILABLE)
        val choices = when (row.lecture.mediaType) {
            MediaType.YOUTUBE -> {
                val streams = resolver.youtube(row.lecture.youtubeId ?: throw DownloadFailure(DownloadError.UNAVAILABLE))
                StreamSelector.choices(streams.audio, streams.video, streams.durationSec)
            }
            else -> {
                val url = row.lecture.mediaUrl ?: throw DownloadFailure(DownloadError.UNAVAILABLE)
                listOf(DownloadChoice(directKind(row), 0, resolver.contentLength(url)))
            }
        }
        return choices.ifEmpty { throw DownloadFailure(DownloadError.UNAVAILABLE) }
    }

    /** The streams to fetch for a download; resolved again on every attempt (YouTube URLs expire). */
    suspend fun plan(row: LectureRow, download: DownloadEntity): DownloadPlan {
        val plan = when (row.lecture.mediaType) {
            MediaType.YOUTUBE -> {
                val streams = resolver.youtube(row.lecture.youtubeId ?: throw DownloadFailure(DownloadError.UNAVAILABLE))
                StreamSelector.plan(streams.audio, streams.video, download.kind, download.quality)
            }
            else -> row.lecture.mediaUrl?.let { url ->
                val mime = row.lecture.mimeType ?: if (download.kind == DownloadKind.VIDEO) "video/mp4" else "audio/mpeg"
                val extension = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..4 }
                    ?: mime.substringAfter('/')
                DownloadPlan(
                    parts = listOf(StreamCandidate(url = url, itag = 0, mimeType = mime, sizeBytes = download.totalBytes)),
                    mimeType = mime,
                    extension = extension,
                    remux = false,
                )
            }
        }
        return plan ?: throw DownloadFailure(DownloadError.UNAVAILABLE)
    }

    suspend fun start(lectureId: String, choice: DownloadChoice) {
        deleteFiles(lectureId)
        dao.upsert(
            DownloadEntity(
                lectureId = lectureId,
                kind = choice.kind,
                quality = choice.quality,
                status = DownloadStatus.QUEUED,
                localPath = null,
                mimeType = null,
                downloadedBytes = 0,
                totalBytes = choice.sizeBytes,
                error = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
        enqueue(lectureId, ExistingWorkPolicy.REPLACE)
    }

    suspend fun retry(lectureId: String) {
        dao.setStatus(lectureId, DownloadStatus.QUEUED, null)
        enqueue(lectureId, ExistingWorkPolicy.REPLACE)
    }

    /** Cancels a running download or deletes a saved file. */
    suspend fun remove(lectureId: String) {
        workManager.cancelUniqueWork(workName(lectureId)).await()
        dao.delete(lectureId)
        deleteFiles(lectureId)
    }

    suspend fun savedMedia(lectureId: String): SavedMedia? {
        val download = dao.get(lectureId)?.takeIf { it.status == DownloadStatus.DONE } ?: return null
        val file = download.localPath?.let(::File)?.takeIf { it.isFile } ?: return null
        return SavedMedia(file, download.mimeType ?: "video/mp4", download.kind == DownloadKind.VIDEO)
    }

    /** Makes sure every unfinished download has its job (e.g. after an app update). */
    suspend fun resumeUnfinished() {
        dao.unfinished().forEach { enqueue(it.lectureId, ExistingWorkPolicy.KEEP) }
    }

    fun partFile(lectureId: String, itag: Int) = File(directory, "$lectureId.$itag.part")
    fun tempFile(lectureId: String) = File(directory, "$lectureId.tmp")
    fun finalFile(lectureId: String, extension: String) = File(directory, "$lectureId.$extension")

    private suspend fun deleteFiles(lectureId: String) = withContext(Dispatchers.IO) {
        directory.listFiles { f -> f.name.startsWith("$lectureId.") }?.forEach { it.delete() }
    }

    private fun enqueue(lectureId: String, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_LECTURE_ID to lectureId))
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(lectureId), policy, request)
    }

    private fun workName(lectureId: String) = "download-$lectureId"

    private fun directKind(row: LectureRow) =
        if (row.lecture.mediaType == MediaType.VIDEO) DownloadKind.VIDEO else DownloadKind.AUDIO
}
