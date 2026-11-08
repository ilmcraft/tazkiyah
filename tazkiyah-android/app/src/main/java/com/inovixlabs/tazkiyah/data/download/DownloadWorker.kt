package com.inovixlabs.tazkiyah.data.download

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.inovixlabs.tazkiyah.MainActivity
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.data.local.dao.CatalogDao
import com.inovixlabs.tazkiyah.data.local.dao.DownloadDao
import com.inovixlabs.tazkiyah.data.local.entity.DownloadEntity
import com.inovixlabs.tazkiyah.data.local.entity.DownloadError
import com.inovixlabs.tazkiyah.data.local.entity.DownloadStatus
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** The stream URL stopped working (YouTube URLs expire after a few hours); resolve again. */
private class ExpiredUrlException : IOException("Stream URL expired")

/**
 * Saves one lecture: resolves the streams, fetches them in resumable chunks (a dropped connection
 * continues where it stopped), muxes picture and sound into one file, and records it as done.
 * Runs as a foreground job with a progress notification.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val catalog: CatalogDao,
    private val repository: DownloadRepository,
    client: OkHttpClient,
) : CoroutineWorker(context, params) {

    private val http = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private var lastProgressAt = 0L
    private var foregroundAllowed = true

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_LECTURE_ID) ?: return Result.failure()
        val download = dao.get(id) ?: return Result.success() // removed meanwhile
        if (download.status == DownloadStatus.DONE) return Result.success()
        val row = catalog.lectureNow(id) ?: run {
            dao.setStatus(id, DownloadStatus.FAILED, DownloadError.UNAVAILABLE)
            return Result.failure()
        }
        showProgress(row, download.downloadedBytes, download.totalBytes)
        dao.setStatus(id, DownloadStatus.RUNNING, null)
        return try {
            save(row, download)
            Result.success()
        } catch (e: CancellationException) {
            // Stopped by the system (no network, …) or replaced: wait for the next run. A removed
            // download has no row any more, so this update does nothing.
            withContext(NonCancellable) { dao.setStatus(id, DownloadStatus.QUEUED, null) }
            throw e
        } catch (e: Exception) {
            val code = downloadErrorOf(e)
            if (code == DownloadError.NETWORK && runAttemptCount < MAX_ATTEMPTS) {
                dao.setStatus(id, DownloadStatus.QUEUED, code)
                Result.retry()
            } else {
                dao.setStatus(id, DownloadStatus.FAILED, code)
                Result.failure()
            }
        }
    }

    private suspend fun save(row: LectureRow, download: DownloadEntity) {
        var plan: DownloadPlan
        var refreshes = 0
        while (true) {
            plan = repository.plan(row, download)
            val files = plan.parts.map { repository.partFile(row.id, it.itag) }
            val total = plan.parts.sumOf { it.sizeBytes }
            ensureSpace(files, total, plan.remux)
            try {
                plan.parts.zip(files).forEach { (stream, file) ->
                    fetch(stream, file) { reportProgress(row, files, total) }
                }
                break
            } catch (e: ExpiredUrlException) {
                if (++refreshes > 2) throw e
            }
        }
        finish(row, plan)
    }

    private suspend fun finish(row: LectureRow, plan: DownloadPlan) = withContext(Dispatchers.IO) {
        val parts = plan.parts.map { repository.partFile(row.id, it.itag) }
        val output = repository.finalFile(row.id, plan.extension)
        val temp = repository.tempFile(row.id)
        val muxed = plan.remux && runCatching { MediaRemuxer.remux(parts, temp) }.onFailure { temp.delete() }.isSuccess
        val ok = when {
            muxed -> temp.renameTo(output)
            parts.size == 1 -> parts.single().renameTo(output) // already a playable file
            else -> false
        }
        parts.forEach { it.delete() }
        if (!ok) throw DownloadFailure(DownloadError.UNKNOWN)
        ensureActive()
        dao.markDone(row.id, output.path, plan.mimeType, output.length())
        // Removed while finishing: don't leave an orphan file behind.
        if (dao.get(row.id)?.status != DownloadStatus.DONE) {
            output.delete()
        } else {
            notifyDone(row)
        }
    }

    /** Fetches [stream] into [file] in ranged chunks, appending to what an earlier attempt saved. */
    private suspend fun fetch(stream: StreamCandidate, file: File, onProgress: suspend () -> Unit) =
        withContext(Dispatchers.IO) {
            var total = stream.sizeBytes
            var serverSaysDone = false
            FileOutputStream(file, true).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (total <= 0 || file.length() < total) {
                    val start = file.length()
                    val end = (start + CHUNK_BYTES - 1).let { if (total > 0) min(it, total - 1) else it }
                    val request = okhttp3.Request.Builder()
                        .url(stream.url)
                        .header("Range", "bytes=$start-$end")
                        .build()
                    val finished = http.newCall(request).execute().use { response ->
                        when (response.code) {
                            206 -> Unit
                            // The server ignored Range: take the whole file in one go.
                            200 -> out.channel.truncate(0)
                            416 -> {
                                serverSaysDone = true // nothing left after `start`
                                return@use true
                            }
                            403, 410 -> throw ExpiredUrlException()
                            404 -> throw DownloadFailure(DownloadError.UNAVAILABLE)
                            else -> throw IOException("HTTP ${response.code}")
                        }
                        if (total <= 0) {
                            total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                                ?: if (response.code == 200) response.body?.contentLength() ?: 0 else 0
                        }
                        var received = 0L
                        response.body?.byteStream()?.use { input ->
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                received += read
                                onProgress()
                            }
                        }
                        response.code == 200 || received == 0L
                    }
                    if (finished) break
                }
            }
            if (total > 0 && file.length() < total && !serverSaysDone) throw IOException("Incomplete download")
            onProgress()
        }

    private suspend fun reportProgress(row: LectureRow, files: List<File>, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastProgressAt < PROGRESS_INTERVAL_MS) return
        lastProgressAt = now
        val done = files.sumOf { it.length() }
        dao.setProgress(row.id, done, total)
        showProgress(row, done, total)
    }

    private fun ensureSpace(files: List<File>, total: Long, remux: Boolean) {
        if (total <= 0) return
        val remaining = (total - files.sumOf { it.length() }).coerceAtLeast(0)
        // Muxing writes a second copy before the parts are deleted.
        val needed = remaining + (if (remux) total else 0) + SPACE_MARGIN_BYTES
        if (freeBytes(repository.directory) < needed) throw DownloadFailure(DownloadError.STORAGE)
    }

    /** Free space including cache the system would clear for us; unknown → don't block. */
    private fun freeBytes(dir: File): Long {
        val storage = applicationContext.getSystemService(StorageManager::class.java) ?: return Long.MAX_VALUE
        return try {
            storage.getAllocatableBytes(storage.getUuidForPath(dir))
        } catch (e: IOException) {
            Long.MAX_VALUE
        }
    }

    private suspend fun showProgress(row: LectureRow, done: Long, total: Long) {
        if (!foregroundAllowed) return
        ensureChannel()
        val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(row.lecture.title)
            .setContentText(applicationContext.getString(R.string.download_notification_progress, percent.toBanglaDigits()))
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()
        val notificationId = row.id.hashCode()
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
        // Not allowed from the background on newer Android; the job then simply runs without it.
        try {
            setForeground(info)
        } catch (e: IllegalStateException) {
            foregroundAllowed = false
        }
    }

    private fun notifyDone(row: LectureRow) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(row.lecture.title)
            .setContentText(applicationContext.getString(R.string.download_notification_done))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        NotificationManagerCompat.from(applicationContext).notify(row.id.hashCode() + 1, notification)
    }

    private fun ensureChannel() {
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(applicationContext.getString(R.string.download_channel))
                .build(),
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        0,
        Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val KEY_LECTURE_ID = "lectureId"
        private const val CHANNEL_ID = "downloads"
        private const val MAX_ATTEMPTS = 6
        private const val CHUNK_BYTES = 5L * 1024 * 1024
        private const val PROGRESS_INTERVAL_MS = 1_000L
        private const val SPACE_MARGIN_BYTES = 50L * 1024 * 1024
    }
}
