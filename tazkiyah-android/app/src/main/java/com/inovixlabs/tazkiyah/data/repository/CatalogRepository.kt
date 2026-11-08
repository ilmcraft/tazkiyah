package com.inovixlabs.tazkiyah.data.repository

import com.inovixlabs.tazkiyah.core.util.BanglaNormalizer
import com.inovixlabs.tazkiyah.data.local.dao.CatalogDao
import com.inovixlabs.tazkiyah.data.local.entity.CategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.CategoryWithCount
import com.inovixlabs.tazkiyah.data.local.entity.LectureCategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.local.entity.SpeakerEntity
import com.inovixlabs.tazkiyah.data.local.entity.SpeakerWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogCounts(val speakers: Int, val categories: Int, val lectures: Int)

/** Read side of the catalog. Every screen reads Room only, so it works offline. */
@Singleton
class CatalogRepository @Inject constructor(
    private val dao: CatalogDao,
) {
    fun speakers(): Flow<List<SpeakerWithCount>> = dao.speakersWithCount()
    fun speaker(id: String): Flow<SpeakerEntity?> = dao.speaker(id)
    fun lecturesBySpeaker(speakerId: String): Flow<List<LectureRow>> = dao.lecturesBySpeaker(speakerId)
    fun categoriesForSpeaker(speakerId: String): Flow<List<CategoryEntity>> = dao.categoriesForSpeaker(speakerId)
    fun linksForSpeaker(speakerId: String): Flow<List<LectureCategoryEntity>> = dao.linksForSpeaker(speakerId)

    fun categories(): Flow<List<CategoryWithCount>> = dao.categoriesWithCount()
    fun category(id: String): Flow<CategoryEntity?> = dao.category(id)
    fun lecturesByCategories(ids: List<String>): Flow<List<LectureRow>> = dao.lecturesByCategories(ids)
    fun speakersForCategories(ids: List<String>): Flow<List<SpeakerEntity>> = dao.speakersForCategories(ids)
    fun categoriesForLecture(lectureId: String): Flow<List<CategoryEntity>> = dao.categoriesForLecture(lectureId)

    fun lecture(id: String): Flow<LectureRow?> = dao.lecture(id)
    suspend fun lectureNow(id: String): LectureRow? = dao.lectureNow(id)
    fun series(seriesId: String): Flow<List<LectureRow>> = dao.series(seriesId)
    suspend fun seriesNow(seriesId: String): List<LectureRow> = dao.seriesNow(seriesId)

    fun recentlyAdded(limit: Int = 12): Flow<List<LectureRow>> = dao.recentlyAdded(limit)
    fun continueListening(limit: Int = 10): Flow<List<LectureRow>> = dao.continueListening(limit)
    fun todayCandidates(startOfDayMillis: Long): Flow<List<LectureRow>> = dao.todayCandidates(startOfDayMillis)
    fun favorites(): Flow<List<LectureRow>> = dao.favorites()
    fun downloaded(): Flow<List<LectureRow>> = dao.downloaded()
    fun history(limit: Int = 100): Flow<List<LectureRow>> = dao.history(limit)
    fun completed(): Flow<List<LectureRow>> = dao.completed()

    fun counts(): Flow<CatalogCounts> =
        combine(dao.speakerCount(), dao.categoryCount(), dao.lectureCount()) { s, c, l -> CatalogCounts(s, c, l) }

    /**
     * Prefix search on the FTS table (title, tags, speaker names, description), with a substring
     * fallback so a word typed from its middle still finds something. Results keep FTS order first.
     */
    suspend fun search(query: String): List<LectureRow> {
        val normalized = BanglaNormalizer.normalize(query)
        if (normalized.isBlank()) return emptyList()
        val ftsIds = BanglaNormalizer.toFtsQuery(query)
            ?.let { runCatching { dao.searchFts(it) }.getOrDefault(emptyList()) }
            .orEmpty()
        val likeIds = dao.searchLike("%" + normalized.replace("%", "").replace("_", "") + "%")
        val ids = (ftsIds + likeIds).distinct()
        if (ids.isEmpty()) return emptyList()
        val rows = ids.chunked(500).flatMap { dao.lecturesByIdsNow(it) }.associateBy { it.id }
        return ids.mapNotNull { rows[it] }
    }
}
