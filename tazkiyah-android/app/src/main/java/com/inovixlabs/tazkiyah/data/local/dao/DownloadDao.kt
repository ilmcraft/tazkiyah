package com.inovixlabs.tazkiyah.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.inovixlabs.tazkiyah.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

/**
 * Only [upsert] creates a row (when the user starts a download); every other write is an UPDATE,
 * so a worker that is still finishing can never bring back a download the user just removed.
 */
@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE lectureId = :lectureId")
    suspend fun get(lectureId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE lectureId = :lectureId")
    fun observe(lectureId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE status IN ('queued', 'running')")
    suspend fun unfinished(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE lectureId = :lectureId")
    suspend fun setStatus(lectureId: String, status: String, error: String?)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total WHERE lectureId = :lectureId")
    suspend fun setProgress(lectureId: String, downloaded: Long, total: Long)

    @Query(
        "UPDATE downloads SET status = 'done', error = NULL, localPath = :path, mimeType = :mimeType, " +
            "downloadedBytes = :bytes, totalBytes = :bytes WHERE lectureId = :lectureId",
    )
    suspend fun markDone(lectureId: String, path: String, mimeType: String, bytes: Long)

    @Query("DELETE FROM downloads WHERE lectureId = :lectureId")
    suspend fun delete(lectureId: String)
}
