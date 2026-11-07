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
