package com.inovixlabs.tazkiyah.feature.speaker

import com.inovixlabs.tazkiyah.data.local.entity.LectureCategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerCategoryCountsTest {

    private fun row(id: String) = LectureRow(
        lecture = LectureEntity(
            id = id, speakerId = "speaker-001", sourcePath = "data/lectures/speaker-001.json", sortIndex = 0,
            title = id, description = null, mediaType = "youtube", youtubeId = "AAAAAAAAAAA", mediaUrl = null,
            mimeType = null, startSec = 0, durationSec = null, publishedAt = null, addedAt = null,
            seriesId = null, seriesTitle = null, seriesPart = null, tags = "",
        ),
        speakerName = null, speakerPhotoUrl = null, progressSec = null, knownDurationSec = null,
        completed = null, lastPlayedAt = null, isFavorite = false,
    )

    @Test
    fun countsLecturesPerCategory() {
        val lectures = listOf(row("a"), row("b"), row("c"))
        val links = listOf(
            LectureCategoryEntity("a", "aqidah"),
            LectureCategoryEntity("b", "aqidah"),
            LectureCategoryEntity("b", "family"),
            LectureCategoryEntity("c", "family"),
            LectureCategoryEntity("c", "aqidah"),
        )
        assertEquals(mapOf("aqidah" to 3, "family" to 2), SpeakerViewModel.categoryCounts(links, lectures))
    }

    @Test
    fun ignoresLinksToLecturesNotListedAndDuplicates() {
        val links = listOf(
            LectureCategoryEntity("a", "salah"),
            LectureCategoryEntity("a", "salah"),
            LectureCategoryEntity("gone", "salah"),
        )
        assertEquals(mapOf("salah" to 1), SpeakerViewModel.categoryCounts(links, listOf(row("a"))))
    }
}
