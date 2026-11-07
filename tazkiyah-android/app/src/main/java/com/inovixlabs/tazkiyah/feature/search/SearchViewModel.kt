package com.inovixlabs.tazkiyah.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data class Idle(val recent: List<String>) : SearchUiState
    data object Loading : SearchUiState
    data class Results(val query: String, val lectures: List<LectureRow>) : SearchUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val recent = settings.settings.map { it.recentSearches }

    val uiState: StateFlow<SearchUiState> = _query
        .debounce(300)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest<String, SearchUiState> { q ->
            if (q.isEmpty()) {
                recent.map { SearchUiState.Idle(it) }
            } else {
                flow {
                    emit(SearchUiState.Loading)
                    emit(SearchUiState.Results(q, catalog.search(q)))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle(emptyList()))

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun rememberQuery() {
        val q = _query.value.trim()
        if (q.isNotEmpty()) viewModelScope.launch { settings.addRecentSearch(q) }
    }

    fun clearRecent() {
        viewModelScope.launch { settings.clearRecentSearches() }
    }
}
