package com.inovixlabs.tazkiyah.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inovixlabs.tazkiyah.data.local.entity.CategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.CategoryWithCount
import com.inovixlabs.tazkiyah.data.local.entity.LectureCategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureFtsEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.local.entity.SpeakerEntity
import com.inovixlabs.tazkiyah.data.local.entity.SpeakerWithCount
import kotlinx.coroutines.flow.Flow

const val LECTURE_ROW_SELECT =
    "SELECT l.*, s.name AS speakerName, s.photoUrl AS speakerPhotoUrl, " +
        "p.positionSec AS progressSec, p.durationSec AS knownDurationSec, p.completed AS completed, " +
        "p.lastPlayedAt AS lastPlayedAt, CASE WHEN f.lectureId IS NULL THEN 0 ELSE 1 END AS isFavorite " +
        "FROM lectures l " +
        "LEFT JOIN speakers s ON s.id = l.speakerId " +
        "LEFT JOIN playback_progress p ON p.lectureId = l.id " +
        "LEFT JOIN favorites f ON f.lectureId = l.id "

@Dao
interface CatalogDao {

    // ---------- Writes (content sync only) ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakers(speakers: List<SpeakerEntity>)

    @Query("DELETE FROM speakers")
    suspend fun clearSpeakers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLectures(lectures: List<LectureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLectureCategories(links: List<LectureCategoryEntity>)

    @Insert
    suspend fun insertFts(rows: List<LectureFtsEntity>)

    @Query("DELETE FROM lectures_fts WHERE lectureId IN (SELECT id FROM lectures WHERE sourcePath = :path)")
    suspend fun deleteFtsForSource(path: String)

    @Query("DELETE FROM lectures_fts WHERE lectureId IN (:ids)")
    suspend fun deleteFtsForIds(ids: List<String>)

    @Query("DELETE FROM lectures_fts")
    suspend fun clearFts()

    @Query("DELETE FROM lecture_category WHERE lectureId IN (SELECT id FROM lectures WHERE sourcePath = :path)")
    suspend fun deleteLinksForSource(path: String)

    @Query("DELETE FROM lecture_category WHERE lectureId IN (:ids)")
    suspend fun deleteLinksForIds(ids: List<String>)

    @Query("DELETE FROM lectures WHERE sourcePath = :path")
    suspend fun deleteLecturesForSource(path: String)

    @Query("SELECT * FROM lectures")
    suspend fun allLectures(): List<LectureEntity>

    @Query("SELECT * FROM speakers")
    suspend fun allSpeakers(): List<SpeakerEntity>

    @Query("SELECT * FROM speakers WHERE id = :id")
    suspend fun speakerNow(id: String): SpeakerEntity?

    // ---------- Speakers ----------

    @Query(
        "SELECT s.*, (SELECT COUNT(*) FROM lectures l WHERE l.speakerId = s.id) AS lectureCount " +
            "FROM speakers s ORDER BY s.sortOrder, s.name",
    )
    fun speakersWithCount(): Flow<List<SpeakerWithCount>>

    @Query("SELECT * FROM speakers WHERE id = :id")
    fun speaker(id: String): Flow<SpeakerEntity?>

    @Query(
        "SELECT DISTINCT s.* FROM speakers s JOIN lectures l ON l.speakerId = s.id " +
            "JOIN lecture_category lc ON lc.lectureId = l.id WHERE lc.categoryId IN (:categoryIds) " +
            "ORDER BY s.sortOrder, s.name",
    )
    fun speakersForCategories(categoryIds: List<String>): Flow<List<SpeakerEntity>>

    // ---------- Categories ----------

    @Query(
        "SELECT c.*, (SELECT COUNT(DISTINCT lc.lectureId) FROM lecture_category lc " +
            "JOIN lectures l ON l.id = lc.lectureId " +
            "WHERE lc.categoryId = c.id OR lc.categoryId IN (SELECT sub.id FROM categories sub WHERE sub.parentId = c.id)) " +
            "AS lectureCount FROM categories c ORDER BY c.sortOrder, c.name",
    )
    fun categoriesWithCount(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun category(id: String): Flow<CategoryEntity?>

    @Query(
        "SELECT DISTINCT c.* FROM categories c JOIN lecture_category lc ON lc.categoryId = c.id " +
            "JOIN lectures l ON l.id = lc.lectureId WHERE l.speakerId = :speakerId ORDER BY c.sortOrder, c.name",
    )
    fun categoriesForSpeaker(speakerId: String): Flow<List<CategoryEntity>>

    @Query("SELECT lc.* FROM lecture_category lc JOIN lectures l ON l.id = lc.lectureId WHERE l.speakerId = :speakerId")
    fun linksForSpeaker(speakerId: String): Flow<List<LectureCategoryEntity>>

    @Query("SELECT c.* FROM categories c JOIN lecture_category lc ON lc.categoryId = c.id WHERE lc.lectureId = :lectureId ORDER BY c.sortOrder")
    fun categoriesForLecture(lectureId: String): Flow<List<CategoryEntity>>

    // ---------- Lectures ----------

    @Query(LECTURE_ROW_SELECT + "WHERE l.id = :id")
    fun lecture(id: String): Flow<LectureRow?>

    @Query(LECTURE_ROW_SELECT + "WHERE l.id = :id")
    suspend fun lectureNow(id: String): LectureRow?

    @Query(LECTURE_ROW_SELECT + "WHERE l.speakerId = :speakerId ORDER BY l.sortIndex")
    fun lecturesBySpeaker(speakerId: String): Flow<List<LectureRow>>

    @Query(
        LECTURE_ROW_SELECT +
            "WHERE l.id IN (SELECT lectureId FROM lecture_category WHERE categoryId IN (:categoryIds)) " +
            "ORDER BY l.addedAt DESC, l.speakerId, l.sortIndex",
    )
    fun lecturesByCategories(categoryIds: List<String>): Flow<List<LectureRow>>

    @Query(LECTURE_ROW_SELECT + "WHERE l.id IN (:ids)")
    fun lecturesByIds(ids: List<String>): Flow<List<LectureRow>>

    @Query(LECTURE_ROW_SELECT + "ORDER BY l.addedAt DESC, l.speakerId, l.sortIndex DESC LIMIT :limit")
    fun recentlyAdded(limit: Int): Flow<List<LectureRow>>

    @Query(LECTURE_ROW_SELECT + "WHERE l.seriesId = :seriesId ORDER BY l.seriesPart, l.sortIndex")
    fun series(seriesId: String): Flow<List<LectureRow>>

    @Query(LECTURE_ROW_SELECT + "WHERE l.seriesId = :seriesId ORDER BY l.seriesPart, l.sortIndex")
    suspend fun seriesNow(seriesId: String): List<LectureRow>

    @Query(
        LECTURE_ROW_SELECT +
            "WHERE p.lectureId IS NOT NULL AND p.completed = 0 AND p.positionSec > 0 " +
            "ORDER BY p.lastPlayedAt DESC LIMIT :limit",
    )
    fun continueListening(limit: Int): Flow<List<LectureRow>>

    /** Not yet heard, or first heard today — keeps "আজকের লেকচার" stable for the whole day. */
    @Query(LECTURE_ROW_SELECT + "WHERE p.lectureId IS NULL OR p.lastPlayedAt >= :startOfDayMillis ORDER BY l.id")
    fun todayCandidates(startOfDayMillis: Long): Flow<List<LectureRow>>

    @Query(LECTURE_ROW_SELECT + "WHERE f.lectureId IS NOT NULL ORDER BY f.addedAt DESC")
    fun favorites(): Flow<List<LectureRow>>

    @Query(LECTURE_ROW_SELECT + "WHERE p.lectureId IS NOT NULL ORDER BY p.lastPlayedAt DESC LIMIT :limit")
    fun history(limit: Int): Flow<List<LectureRow>>

    @Query(LECTURE_ROW_SELECT + "WHERE p.completed = 1 ORDER BY p.lastPlayedAt DESC")
    fun completed(): Flow<List<LectureRow>>

    // ---------- Search ----------

    @Query("SELECT lectureId FROM lectures_fts WHERE lectures_fts MATCH :match")
    suspend fun searchFts(match: String): List<String>

    @Query(
        "SELECT lectureId FROM lectures_fts WHERE title LIKE :like OR tags LIKE :like " +
            "OR speakerName LIKE :like OR speakerNameEn LIKE :like OR description LIKE :like",
    )
    suspend fun searchLike(like: String): List<String>

    @Query(LECTURE_ROW_SELECT + "WHERE l.id IN (:ids)")
    suspend fun lecturesByIdsNow(ids: List<String>): List<LectureRow>

    // ---------- Counts ----------

    @Query("SELECT COUNT(*) FROM speakers")
    fun speakerCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM categories")
    fun categoryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM lectures")
    fun lectureCount(): Flow<Int>
}
