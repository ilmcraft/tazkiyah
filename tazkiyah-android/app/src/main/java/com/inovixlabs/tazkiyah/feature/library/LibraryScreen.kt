package com.inovixlabs.tazkiyah.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.AppScreen
import com.inovixlabs.tazkiyah.core.designsystem.EmptyState
import com.inovixlabs.tazkiyah.core.designsystem.LectureListItem
import com.inovixlabs.tazkiyah.core.designsystem.UiState
import com.inovixlabs.tazkiyah.core.designsystem.UiStateContent
import com.inovixlabs.tazkiyah.core.designsystem.asUiState
import com.inovixlabs.tazkiyah.core.designsystem.durationLabel
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import com.inovixlabs.tazkiyah.data.repository.UserDataRepository
import com.inovixlabs.tazkiyah.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class DayListen(val date: LocalDate, val seconds: Long)

data class LibraryData(
    val favorites: List<LectureRow>,
    val history: List<LectureRow>,
    val completed: List<LectureRow>,
    val week: List<DayListen>,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    catalog: CatalogRepository,
    userData: UserDataRepository,
    private val playback: PlaybackManager,
) : ViewModel() {
    private val today = LocalDate.now()

    // History and favorites join the catalog, so a lecture removed from the content repo is
    // simply hidden here — its user rows stay intact.
    val uiState: StateFlow<UiState<LibraryData>> = combine(
        catalog.favorites(),
        catalog.history(),
        catalog.completed(),
        userData.weeklyListenLog(today),
    ) { favorites, history, completed, log ->
        val byDate = log.associate { it.date to it.listenedSec }
        val week = (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            DayListen(date, byDate[date.toString()] ?: 0)
        }
        LibraryData(favorites, history, completed, week)
    }.asUiState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun toggleFavorite(lectureId: String) = playback.toggleFavorite(lectureId)
}

@Composable
fun LibraryScreen(
    onOpenLecture: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    AppScreen(
        title = stringResource(R.string.library_title),
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) } },
    ) { padding ->
        UiStateContent(state, Modifier.padding(padding)) { data ->
            val tabs = listOf(
                Triple(R.string.library_favorites, data.favorites, R.string.library_favorites_empty),
                Triple(R.string.library_history, data.history, R.string.library_history_empty),
                Triple(R.string.library_completed, data.completed, R.string.library_completed_empty),
            )
            val (_, rows, emptyText) = tabs[tab]
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { WeekCard(data.week) }
                item {
                    PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                        tabs.forEachIndexed { index, (label, list, _) ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index },
                                text = { Text(stringResource(label) + " (" + list.size.toBanglaDigits() + ")") },
                            )
                        }
                    }
                }
                if (rows.isEmpty()) {
                    item {
                        val icon = when (tab) {
                            0 -> Icons.Rounded.FavoriteBorder
                            1 -> Icons.Rounded.History
                            else -> Icons.Rounded.CheckCircle
                        }
                        EmptyState(stringResource(emptyText), icon = icon)
                    }
                }
                items(rows, key = { "$tab-" + it.id }) { row ->
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

@Composable
private fun WeekCard(week: List<DayListen>) {
    val weekdays = stringArrayResource(R.array.weekday_short)
    val total = week.sumOf { it.seconds }
    val max = (week.maxOfOrNull { it.seconds } ?: 0).coerceAtLeast(60)
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.library_week), style = MaterialTheme.typography.titleSmall)
            Text(
                durationLabel(total) ?: stringResource(R.string.minutes_short, 0.toBanglaDigits()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                Modifier.fillMaxWidth().height(96.dp).padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                week.forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
                        Box(Modifier.weight(1f).width(18.dp), contentAlignment = Alignment.BottomCenter) {
                            val fraction = (day.seconds.toFloat() / max).coerceIn(0.04f, 1f)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (day.seconds > 0) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                            )
                        }
                        Text(
                            weekdays.getOrElse(day.date.dayOfWeek.value - 1) { "" },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
