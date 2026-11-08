package com.inovixlabs.tazkiyah.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---- User tables: content sync never touches these, and they have no foreign keys to the
// catalog, so removing a lecture from the content repo only hides history, never deletes it. ----

@Entity(tableName = "playback_progress", indices = [Index("lastPlayedAt")])
data class PlaybackProgressEntity(
    @PrimaryKey val lectureId: String,
    val positionSec: Long,
    /** Duration reported by the player; used when the catalog has no durationSec. */
    val durationSec: Long,
    val completed: Boolean,
    val lastPlayedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val lectureId: String,
    val addedAt: Long,
)

/** Seconds listened per local day (ISO date). */
@Entity(tableName = "listen_log")
data class ListenLogEntity(
    @PrimaryKey val date: String,
    val listenedSec: Long,
)

object DownloadKind {
    const val AUDIO = "audio"
    const val VIDEO = "video"
}

object DownloadStatus {
    /** Waiting for its turn or for a network connection. */
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val DONE = "done"
    const val FAILED = "failed"
}

/** Error codes stored in [DownloadEntity.error]; the UI maps them to Bengali text. */
object DownloadError {
    const val NETWORK = "network"
    const val UNAVAILABLE = "unavailable"
    const val STORAGE = "storage"
    const val UNKNOWN = "unknown"
}

/** A lecture saved on the phone for offline use (docs/PLAN.md, `downloads`). */
@Entity(tableName = "downloads", indices = [Index("createdAt")])
data class DownloadEntity(
    @PrimaryKey val lectureId: String,
    /** [DownloadKind]. */
    val kind: String,
    /** Video height (360, 720…); 0 for audio. */
    val quality: Int,
    /** [DownloadStatus]. */
    val status: String,
    /** The finished file; set once [status] is DONE. */
    val localPath: String?,
    val mimeType: String?,
    val downloadedBytes: Long,
    /** Expected size; 0 when not known yet. */
    val totalBytes: Long,
    /** [DownloadError] of the last failed attempt. */
    val error: String?,
    val createdAt: Long,
)
