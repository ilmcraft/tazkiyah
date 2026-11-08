package com.inovixlabs.tazkiyah.feature.speaker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.inovixlabs.tazkiyah.core.designsystem.UiState
import com.inovixlabs.tazkiyah.core.designsystem.asUiState
import com.inovixlabs.tazkiyah.data.local.entity.CategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureCategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.local.entity.SpeakerEntity
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import com.inovixlabs.tazkiyah.playback.PlaybackManager
import com.inovixlabs.tazkiyah.ui.SpeakerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SpeakerSort { NEWEST, OLDEST, SERIES }

/** A titled group of lectures; title null = no grouping. */
data class LectureGroup(val title: String?, val lectures: List<LectureRow>)

data class SpeakerPage(
    val speaker: SpeakerEntity?,
    val categories: List<CategoryEntity>,
    /** Lectures of this speaker in each category (chip label "আকীদা (৫)"). */
    val categoryCounts: Map<String, Int>,
    val selectedCategory: String?,
    val sort: SpeakerSort,
    val groups: List<LectureGroup>,
    val totalLectures: Int,
)

@HiltViewModel
class SpeakerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalog: CatalogRepository,
    private val playback: PlaybackManager,
) : ViewModel() {
    private val speakerId = savedStateHandle.toRoute<SpeakerRoute>().id
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(SpeakerSort.NEWEST)

    val uiState: StateFlow<UiState<SpeakerPage>> = combine(
        catalog.speaker(speakerId),
        catalog.lecturesBySpeaker(speakerId),
        catalog.categoriesForSpeaker(speakerId),
        catalog.linksForSpeaker(speakerId),
        combine(selectedCategory, sort) { c, s -> c to s },
    ) { speaker, lectures, categories, links, (category, sortBy) ->
        val filtered = if (category == null) lectures else {
            val ids = links.filter { it.categoryId == category }.map { it.lectureId }.toSet()
            lectures.filter { it.id in ids }
        }
        SpeakerPage(
            speaker = speaker,
            categories = categories,
            categoryCounts = categoryCounts(links, lectures),
            selectedCategory = category,
            sort = sortBy,
            groups = group(filtered, sortBy),
            totalLectures = lectures.size,
        )
    }.asUiState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun selectCategory(id: String?) {
        selectedCategory.value = id
    }

    fun setSort(value: SpeakerSort) {
        sort.value = value
    }

    fun toggleFavorite(lectureId: String) = playback.toggleFavorite(lectureId)

    companion object {
        /** How many of [lectures] each category holds; links to lectures not in the list are ignored. */
        fun categoryCounts(links: List<LectureCategoryEntity>, lectures: List<LectureRow>): Map<String, Int> {
            val ids = lectures.mapTo(HashSet()) { it.id }
            return links.filter { it.lectureId in ids }
                .distinctBy { it.categoryId to it.lectureId }
                .groupingBy { it.categoryId }
                .eachCount()
        }

        /** Newest = latest publish/added date, then later position in the content file. */
        private val newestFirst = compareByDescending<LectureRow> { it.lecture.publishedAt ?: it.lecture.addedAt ?: "" }
            .thenByDescending { it.lecture.addedAt ?: "" }
            .thenByDescending { it.lecture.sortIndex }

        fun group(lectures: List<LectureRow>, sort: SpeakerSort): List<LectureGroup> = when (sort) {
            SpeakerSort.NEWEST -> listOf(LectureGroup(null, lectures.sortedWith(newestFirst)))
            SpeakerSort.OLDEST -> listOf(LectureGroup(null, lectures.sortedWith(newestFirst.reversed())))
            SpeakerSort.SERIES -> {
                val (inSeries, single) = lectures.partition { it.lecture.seriesId != null }
                inSeries.groupBy { it.lecture.seriesId }
                    .map { (_, parts) ->
                        LectureGroup(
                            parts.first().lecture.seriesTitle,
                            parts.sortedWith(compareBy<LectureRow> { it.lecture.seriesPart ?: Int.MAX_VALUE }.thenBy { it.lecture.sortIndex }),
                        )
                    } + if (single.isEmpty()) emptyList() else listOf(LectureGroup("", single.sortedWith(newestFirst)))
            }
        }
    }
}
