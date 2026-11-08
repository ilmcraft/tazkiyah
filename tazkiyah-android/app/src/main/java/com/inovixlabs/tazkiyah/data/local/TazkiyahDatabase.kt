package com.inovixlabs.tazkiyah.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.inovixlabs.tazkiyah.data.local.dao.CatalogDao
import com.inovixlabs.tazkiyah.data.local.dao.DownloadDao
import com.inovixlabs.tazkiyah.data.local.dao.SyncDao
import com.inovixlabs.tazkiyah.data.local.dao.UserDao
import com.inovixlabs.tazkiyah.data.local.entity.CategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.DownloadEntity
import com.inovixlabs.tazkiyah.data.local.entity.FavoriteEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureCategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureFtsEntity
import com.inovixlabs.tazkiyah.data.local.entity.ListenLogEntity
import com.inovixlabs.tazkiyah.data.local.entity.PlaybackProgressEntity
import com.inovixlabs.tazkiyah.data.local.entity.SpeakerEntity
import com.inovixlabs.tazkiyah.data.local.entity.SyncedFileEntity

/**
 * Schema changes must ship an AutoMigration (schemas/ is exported); never use a destructive
 * fallback — it would wipe the user's history and favorites.
 */
@Database(
    entities = [
        SpeakerEntity::class,
        CategoryEntity::class,
        LectureEntity::class,
        LectureCategoryEntity::class,
        LectureFtsEntity::class,
        SyncedFileEntity::class,
        PlaybackProgressEntity::class,
        FavoriteEntity::class,
        ListenLogEntity::class,
        DownloadEntity::class,
    ],
    version = 2,
    autoMigrations = [
        // v2: `downloads` (offline lectures).
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
abstract class TazkiyahDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun userDao(): UserDao
    abstract fun syncDao(): SyncDao
    abstract fun downloadDao(): DownloadDao
}
