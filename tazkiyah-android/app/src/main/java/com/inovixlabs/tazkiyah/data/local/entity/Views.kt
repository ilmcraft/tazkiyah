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
) {
    val id: String get() = lecture.id
    val durationSec: Long? get() = lecture.durationSec?.takeIf { it > 0 } ?: knownDurationSec?.takeIf { it > 0 }
    val isCompleted: Boolean get() = completed == true
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
