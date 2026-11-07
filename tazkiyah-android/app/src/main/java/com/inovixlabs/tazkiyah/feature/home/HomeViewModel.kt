package com.inovixlabs.tazkiyah.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inovixlabs.tazkiyah.core.designsystem.UiState
import com.inovixlabs.tazkiyah.core.designsystem.asUiState
import com.inovixlabs.tazkiyah.data.local.entity.CategoryWithCount
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.repository.AppSettings
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import com.inovixlabs.tazkiyah.data.sync.ContentSyncCoordinator
import com.inovixlabs.tazkiyah.data.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

data class HomeData(
    val continueListening: List<LectureRow>,
    val recent: List<LectureRow>,
    val today: LectureRow?,
    val topics: List<CategoryWithCount>,
    val isEmpty: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    catalog: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val syncCoordinator: ContentSyncCoordinator,
) : ViewModel() {

    private val today = LocalDate.now()
    private val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val uiState: StateFlow<UiState<HomeData>> = combine(
        catalog.continueListening(),
        catalog.recentlyAdded(8),
        catalog.todayCandidates(startOfDay),
        catalog.categories(),
        catalog.counts(),
    ) { continueListening, recent, candidates, categories, counts ->
        HomeData(
            continueListening = continueListening,
            recent = recent,
            today = pickForToday(candidates),
            topics = categories.filter { it.category.parentId == null && it.lectureCount > 0 },
            isEmpty = counts.lectures == 0,
        )
    }.asUiState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isSyncing: StateFlow<Boolean> = syncCoordinator.isSyncing

    private val _syncResults = Channel<SyncResult>(Channel.BUFFERED)
    val syncResults: Flow<SyncResult> = _syncResults.receiveAsFlow()

    fun refresh() {
        viewModelScope.launch { _syncResults.send(syncCoordinator.syncRemote()) }
    }

    fun dismissChangelog() {
        viewModelScope.launch { settingsRepository.dismissChangelog() }
    }

    /** One lecture per day from the ones not heard yet, stable for the whole day. */
    private fun pickForToday(candidates: List<LectureRow>): LectureRow? {
        if (candidates.isEmpty()) return null
        return candidates[Random(today.toEpochDay()).nextInt(candidates.size)]
    }
}
