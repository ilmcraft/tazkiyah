package com.inovixlabs.tazkiyah.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.inovixlabs.tazkiyah.data.local.entity.FavoriteEntity
import com.inovixlabs.tazkiyah.data.local.entity.ListenLogEntity
import com.inovixlabs.tazkiyah.data.local.entity.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM playback_progress WHERE lectureId = :lectureId")
    suspend fun progress(lectureId: String): PlaybackProgressEntity?

    @Upsert
    suspend fun upsertProgress(progress: PlaybackProgressEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE lectureId = :lectureId)")
    suspend fun isFavorite(lectureId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE lectureId = :lectureId")
    suspend fun removeFavorite(lectureId: String)

    @Query("INSERT OR IGNORE INTO listen_log(date, listenedSec) VALUES (:date, 0)")
    suspend fun ensureLogDay(date: String)

    @Query("UPDATE listen_log SET listenedSec = listenedSec + :seconds WHERE date = :date")
    suspend fun addToLogDay(date: String, seconds: Long)

    @Query("SELECT * FROM listen_log WHERE date >= :fromDate ORDER BY date")
    fun listenLogSince(fromDate: String): Flow<List<ListenLogEntity>>
}
