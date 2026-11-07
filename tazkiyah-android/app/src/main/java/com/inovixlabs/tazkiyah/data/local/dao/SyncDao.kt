package com.inovixlabs.tazkiyah.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.inovixlabs.tazkiyah.data.local.entity.SyncedFileEntity

@Dao
interface SyncDao {
    @Query("SELECT * FROM synced_files")
    suspend fun syncedFiles(): List<SyncedFileEntity>

    @Upsert
    suspend fun upsertSynced(file: SyncedFileEntity)

    @Query("DELETE FROM synced_files WHERE path = :path")
    suspend fun deleteSynced(path: String)
}
