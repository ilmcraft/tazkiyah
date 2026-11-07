package com.inovixlabs.tazkiyah.feature.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.AppScreen
import com.inovixlabs.tazkiyah.core.designsystem.EmptyState
import com.inovixlabs.tazkiyah.core.designsystem.LectureListItem
import com.inovixlabs.tazkiyah.core.designsystem.UiState
import com.inovixlabs.tazkiyah.core.designsystem.UiStateContent
import com.inovixlabs.tazkiyah.core.designsystem.asUiState
import com.inovixlabs.tazkiyah.core.designsystem.categoryIcon
import com.inovixlabs.tazkiyah.data.local.entity.CategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.CategoryWithCount
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.local.entity.SpeakerEntity
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import com.inovixlabs.tazkiyah.playback.PlaybackManager
import com.inovixlabs.tazkiyah.ui.CategoryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CategoryPage(
    val category: CategoryEntity?,
    val subcategories: List<CategoryWithCount>,
    val speakers: List<SpeakerEntity>,
    val selectedSpeaker: String?,
    val lectures: List<LectureRow>,
)

/** বিষয় → উপ-বিষয় → লেকচার, filterable by speaker. A parent includes its sub-topics' lectures. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalog: CatalogRepository,
    private val playback: PlaybackManager,
) : ViewModel() {
    private val categoryId = savedStateHandle.toRoute<CategoryRoute>().id
    private val selectedSpeaker = MutableStateFlow<String?>(null)

    private val subcategories = catalog.categories().map { all -> all.filter { it.category.parentId == categoryId } }
    private val ids = subcategories.map { subs -> listOf(categoryId) + subs.map { it.category.id } }.distinctUntilChanged()

    val uiState: StateFlow<UiState<CategoryPage>> = combine(
        catalog.category(categoryId),
        subcategories,
        ids.flatMapLatest { catalog.lecturesByCategories(it) },
        ids.flatMapLatest { catalog.speakersForCategories(it) },
        selectedSpeaker,
    ) { category, subs, lectures, speakers, speaker ->
        CategoryPage(
            category = category,
            subcategories = subs.filter { it.lectureCount > 0 },
            speakers = speakers,
            selectedSpeaker = speaker,
            lectures = if (speaker == null) lectures else lectures.filter { it.lecture.speakerId == speaker },
        )
    }.asUiState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun selectSpeaker(id: String?) {
        selectedSpeaker.value = id
    }

    fun toggleFavorite(lectureId: String) = playback.toggleFavorite(lectureId)
}

@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onOpenLecture: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = (state as? UiState.Success)?.data?.category?.name.orEmpty()
    AppScreen(title = title, onBack = onBack) { padding ->
        UiStateContent(state, Modifier.padding(padding)) { page ->
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (page.subcategories.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.subcategories),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(page.subcategories, key = { it.category.id }) { sub ->
                                AssistChip(
                                    onClick = { onOpenCategory(sub.category.id) },
                                    label = { Text(sub.category.name) },
                                    leadingIcon = { Icon(categoryIcon(sub.category.icon), null, Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                }
                if (page.speakers.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = page.selectedSpeaker == null,
                                    onClick = { viewModel.selectSpeaker(null) },
                                    label = { Text(stringResource(R.string.category_all_speakers)) },
                                )
                            }
                            items(page.speakers, key = { it.id }) { speaker ->
                                FilterChip(
                                    selected = page.selectedSpeaker == speaker.id,
                                    onClick = { viewModel.selectSpeaker(speaker.id) },
                                    label = { Text(speaker.name) },
                                )
                            }
                        }
                    }
                }
                if (page.lectures.isEmpty()) {
                    item { EmptyState(stringResource(R.string.lectures_empty)) }
                }
                items(page.lectures, key = { it.id }) { row ->
                    LectureListItem(
                        row = row,
                        onClick = { onOpenLecture(row.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(row.id) },
                    )
                }
            }
        }
    }
}
