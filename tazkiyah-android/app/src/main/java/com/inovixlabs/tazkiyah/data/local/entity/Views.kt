package com.inovixlabs.tazkiyah.data.local.entity

import androidx.room.Embedded

/** A lecture joined with its speaker and the user's progress/favorite state. */
data class LectureRow(
    @Embedded val lecture: LectureEntity,
    val speakerName: String?,
    val speakerPhotoUrl: String?,
    val progressSec: Long?,
    val knownDurationSec: Long?,
    val completed: Boolean?,
    val lastPlayedAt: Long?,
    val isFavorite: Boolean,
    /** [DownloadStatus]; null when the lecture is not saved on the phone. */
    val downloadStatus: String? = null,
    val downloadKind: String? = null,
    val downloadedBytes: Long? = null,
    val downloadTotalBytes: Long? = null,
) {
    val id: String get() = lecture.id
    val durationSec: Long? get() = lecture.durationSec?.takeIf { it > 0 } ?: knownDurationSec?.takeIf { it > 0 }
    val isCompleted: Boolean get() = completed == true
    val isSaved: Boolean get() = downloadStatus == DownloadStatus.DONE
    val isSaving: Boolean get() = downloadStatus == DownloadStatus.QUEUED || downloadStatus == DownloadStatus.RUNNING
    val progressFraction: Float
        get() {
            val d = durationSec ?: return 0f
            val p = progressSec ?: return 0f
            return if (isCompleted) 1f else (p.toFloat() / d).coerceIn(0f, 1f)
        }
}

data class SpeakerWithCount(
    @Embedded val speaker: SpeakerEntity,
    val lectureCount: Int,
)

data class CategoryWithCount(
    @Embedded val category: CategoryEntity,
    val lectureCount: Int,
)
