package com.inovixlabs.tazkiyah.data.sync

import com.inovixlabs.tazkiyah.core.util.BanglaNormalizer
import com.inovixlabs.tazkiyah.data.local.dao.CatalogDao
import com.inovixlabs.tazkiyah.data.local.entity.CategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureCategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureFtsEntity
import com.inovixlabs.tazkiyah.data.local.entity.MediaType
import com.inovixlabs.tazkiyah.data.local.entity.SpeakerEntity
import com.inovixlabs.tazkiyah.data.remote.CategoriesFileDto
import com.inovixlabs.tazkiyah.data.remote.ContentJson
import com.inovixlabs.tazkiyah.data.remote.LectureDto
import com.inovixlabs.tazkiyah.data.remote.LectureFileDto
import com.inovixlabs.tazkiyah.data.remote.SpeakersFileDto

private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

/** Turns one content file into catalog rows. Must run inside a transaction (RoomCatalogStore does). */
class CatalogImporter(
    private val dao: CatalogDao,
    private val contentBaseUrl: String,
) {
    suspend fun import(path: String, bytes: ByteArray) {
        val text = bytes.decodeToString()
        when {
            path == CatalogPaths.SPEAKERS ->
                importSpeakers(ContentJson.decodeFromString(SpeakersFileDto.serializer(), text))
            path == CatalogPaths.CATEGORIES ->
                importCategories(ContentJson.decodeFromString(CategoriesFileDto.serializer(), text))
            isLectureFile(path) ->
                importLectures(path, ContentJson.decodeFromString(LectureFileDto.serializer(), text))
            else -> Unit // images and other files: nothing to store
        }
    }

    suspend fun remove(path: String) {
        when {
            path == CatalogPaths.SPEAKERS -> dao.clearSpeakers()
            path == CatalogPaths.CATEGORIES -> dao.clearCategories()
            isLectureFile(path) -> {
                dao.deleteFtsForSource(path)
                dao.deleteLinksForSource(path)
                dao.deleteLecturesForSource(path)
            }
        }
    }

    private fun isLectureFile(path: String) =
        path.startsWith(CatalogPaths.LECTURES_DIR) && path.endsWith(".json")

    private suspend fun importSpeakers(file: SpeakersFileDto) {
        dao.clearSpeakers()
        dao.insertSpeakers(
            file.speakers.map {
                SpeakerEntity(
                    id = it.id,
                    name = it.name,
                    nameEn = it.nameEn,
                    bio = it.bio,
                    photoUrl = it.photo?.let(::resolveUrl),
                    sortOrder = it.order,
                )
            },
        )
        rebuildSearchIndex() // speaker names are part of every lecture's search row
    }

    private suspend fun importCategories(file: CategoriesFileDto) {
        dao.clearCategories()
        dao.insertCategories(
            file.categories.map { CategoryEntity(it.id, it.name, it.icon, it.order, it.parentId) },
        )
    }

    private suspend fun importLectures(path: String, file: LectureFileDto) {
        val valid = file.lectures.filter { it.isPlayable() }.distinctBy { it.id }
        val ids = valid.map { it.id }
        dao.deleteFtsForSource(path)
        dao.deleteLinksForSource(path)
        dao.deleteLecturesForSource(path)
        // A lecture may have moved here from another file: clear its old rows too.
        ids.chunked(500).forEach {
            dao.deleteFtsForIds(it)
            dao.deleteLinksForIds(it)
        }
        val entities = valid.mapIndexed { index, dto -> dto.toEntity(file.speakerId, path, index) }
        dao.insertLectures(entities)
        dao.insertLectureCategories(
            valid.flatMap { dto -> dto.categoryIds.distinct().map { LectureCategoryEntity(dto.id, it) } },
        )
        val speaker = dao.speakerNow(file.speakerId)
        dao.insertFts(entities.map { searchRow(it, speaker) })
    }

    private suspend fun rebuildSearchIndex() {
        val speakers = dao.allSpeakers().associateBy { it.id }
        dao.clearFts()
        dao.allLectures().chunked(500).forEach { chunk ->
            dao.insertFts(chunk.map { searchRow(it, speakers[it.speakerId]) })
        }
    }

    private fun searchRow(lecture: LectureEntity, speaker: SpeakerEntity?) = LectureFtsEntity(
        lectureId = lecture.id,
        title = BanglaNormalizer.normalize(lecture.title),
        tags = BanglaNormalizer.normalize(lecture.tags.replace(",", " ")),
        speakerName = BanglaNormalizer.normalize(speaker?.name.orEmpty()),
        speakerNameEn = BanglaNormalizer.normalize(speaker?.nameEn.orEmpty()),
        description = BanglaNormalizer.normalize(lecture.description.orEmpty()),
    )

    private fun LectureDto.isPlayable(): Boolean = when (media.type) {
        MediaType.YOUTUBE -> media.videoId?.matches(VIDEO_ID) == true
        MediaType.AUDIO, MediaType.VIDEO -> media.url?.startsWith("https://") == true
        else -> false
    }

    private fun LectureDto.toEntity(speakerId: String, path: String, index: Int) = LectureEntity(
        id = id,
        speakerId = speakerId,
        sourcePath = path,
        sortIndex = index,
        title = title.trim(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        mediaType = media.type,
        youtubeId = media.videoId,
        mediaUrl = media.url,
        mimeType = media.mimeType,
        startSec = media.startSec.coerceAtLeast(0),
        durationSec = durationSec?.takeIf { it > 0 },
        publishedAt = publishedAt,
        addedAt = addedAt,
        seriesId = series?.id,
        seriesTitle = series?.title,
        seriesPart = series?.part,
        tags = tags.joinToString(", "),
    )

    private fun resolveUrl(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl
        else contentBaseUrl + pathOrUrl.removePrefix("/")
}
