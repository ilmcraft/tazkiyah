package com.inovixlabs.tazkiyah.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

// ---- Catalog tables: replaced by content sync. ----

@Entity(tableName = "speakers")
data class SpeakerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameEn: String?,
    val bio: String?,
    val photoUrl: String?,
    val sortOrder: Int,
)

@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String?,
    val sortOrder: Int,
    val parentId: String?,
)

object MediaType {
    const val YOUTUBE = "youtube"
    const val AUDIO = "audio"
    const val VIDEO = "video"
}

@Entity(
    tableName = "lectures",
    indices = [Index("speakerId"), Index("sourcePath"), Index("seriesId"), Index("addedAt")],
)
data class LectureEntity(
    @PrimaryKey val id: String,
    val speakerId: String,
    /** Content file the lecture came from; a file update replaces exactly these rows. */
    val sourcePath: String,
    val sortIndex: Int,
    val title: String,
    val description: String?,
    val mediaType: String,
    val youtubeId: String?,
    val mediaUrl: String?,
    val mimeType: String?,
    val startSec: Long,
    val durationSec: Long?,
    val publishedAt: String?,
    val addedAt: String?,
    val seriesId: String?,
    val seriesTitle: String?,
    val seriesPart: Int?,
    /** Tags joined with ", " for display. */
    val tags: String,
)

@Entity(
    tableName = "lecture_category",
    primaryKeys = ["lectureId", "categoryId"],
    indices = [Index("categoryId")],
)
data class LectureCategoryEntity(val lectureId: String, val categoryId: String)

/** Normalized (BanglaNormalizer) search text, one row per lecture. */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["lectureId"])
@Entity(tableName = "lectures_fts")
data class LectureFtsEntity(
    val lectureId: String,
    val title: String,
    val tags: String,
    val speakerName: String,
    val speakerNameEn: String,
    val description: String,
)

@Entity(tableName = "synced_files")
data class SyncedFileEntity(
    @PrimaryKey val path: String,
    val sha256: String,
    val size: Long,
    val syncedAt: Long,
)
