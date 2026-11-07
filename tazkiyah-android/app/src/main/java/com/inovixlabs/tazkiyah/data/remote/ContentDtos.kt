package com.inovixlabs.tazkiyah.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Unknown keys are ignored so new optional fields in the content repo never break old apps. */
val ContentJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class ManifestDto(
    val schemaVersion: Int,
    val minAppVersionCode: Int = 1,
    val dataVersion: Int,
    val updatedAt: String? = null,
    val changelog: String? = null,
    val files: List<ManifestFileDto> = emptyList(),
)

@Serializable
data class ManifestFileDto(val path: String, val sha256: String, val size: Long = 0)

@Serializable
data class SpeakersFileDto(val speakers: List<SpeakerDto> = emptyList())

@Serializable
data class SpeakerDto(
    val id: String,
    val name: String,
    val nameEn: String? = null,
    val bio: String? = null,
    val photo: String? = null,
    val order: Int = 0,
)

@Serializable
data class CategoriesFileDto(val categories: List<CategoryDto> = emptyList())

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val icon: String? = null,
    val order: Int = 0,
    val parentId: String? = null,
)

@Serializable
data class LectureFileDto(val speakerId: String, val lectures: List<LectureDto> = emptyList())

@Serializable
data class LectureDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val categoryIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val media: MediaDto,
    val durationSec: Long? = null,
    val publishedAt: String? = null,
    val addedAt: String? = null,
    val series: SeriesDto? = null,
)

/** `type` is youtube (videoId), audio (url) or video (url). */
@Serializable
data class MediaDto(
    val type: String,
    val videoId: String? = null,
    val startSec: Long = 0,
    val url: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class SeriesDto(val id: String, val title: String, val part: Int? = null)
