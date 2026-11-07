package com.inovixlabs.tazkiyah.feature.speakers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.AppScreen
import com.inovixlabs.tazkiyah.core.designsystem.EmptyState
import com.inovixlabs.tazkiyah.core.designsystem.SpeakerAvatar
import com.inovixlabs.tazkiyah.core.designsystem.UiState
import com.inovixlabs.tazkiyah.core.designsystem.UiStateContent
import com.inovixlabs.tazkiyah.core.designsystem.asUiState
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.data.local.entity.SpeakerWithCount
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SpeakersViewModel @Inject constructor(catalog: CatalogRepository) : ViewModel() {
    val uiState: StateFlow<UiState<List<SpeakerWithCount>>> = catalog.speakers()
        .asUiState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}

@Composable
fun SpeakersScreen(
    onOpenSpeaker: (String) -> Unit,
    onSearch: () -> Unit,
    viewModel: SpeakersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppScreen(
        title = stringResource(R.string.speakers_title),
        actions = { IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) } },
    ) { padding ->
        UiStateContent(state, Modifier.padding(padding)) { speakers ->
            if (speakers.isEmpty()) {
                EmptyState(stringResource(R.string.speakers_empty), Modifier.padding(padding), Icons.Rounded.Groups)
            } else {
                SpeakerGrid(speakers, onOpenSpeaker, Modifier.padding(padding))
            }
        }
    }
}

@Composable
fun SpeakerGrid(speakers: List<SpeakerWithCount>, onOpenSpeaker: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(speakers, key = { it.speaker.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenSpeaker(item.speaker.id) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SpeakerAvatar(item.speaker.name, item.speaker.photoUrl, 88.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        item.speaker.name,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.lecture_count, item.lectureCount.toBanglaDigits()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
