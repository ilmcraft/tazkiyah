package com.inovixlabs.tazkiyah.feature.speaker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.BrandShape
import com.inovixlabs.tazkiyah.core.designsystem.AppScreen
import com.inovixlabs.tazkiyah.core.designsystem.EmptyState
import com.inovixlabs.tazkiyah.core.designsystem.LectureListItem
import com.inovixlabs.tazkiyah.core.designsystem.SpeakerAvatar
import com.inovixlabs.tazkiyah.core.designsystem.UiState
import com.inovixlabs.tazkiyah.core.designsystem.UiStateContent
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits

@Composable
fun SpeakerScreen(
    onBack: () -> Unit,
    onOpenLecture: (String) -> Unit,
    viewModel: SpeakerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = (state as? UiState.Success)?.data?.speaker?.name.orEmpty()
    AppScreen(title = title, onBack = onBack) { padding ->
        UiStateContent(state, Modifier.padding(padding)) { page ->
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Header(page) }
                if (page.categories.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = page.selectedCategory == null,
                                    onClick = { viewModel.selectCategory(null) },
                                    label = {
                                        Text(stringResource(R.string.chip_with_count, stringResource(R.string.speaker_all_topics), page.totalLectures.toBanglaDigits()))
                                    },
                                )
                            }
                            items(page.categories, key = { it.id }) { category ->
                                FilterChip(
                                    selected = page.selectedCategory == category.id,
                                    onClick = { viewModel.selectCategory(category.id) },
                                    label = {
                                        val count = page.categoryCounts[category.id] ?: 0
                                        Text(stringResource(R.string.chip_with_count, category.name, count.toBanglaDigits()))
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    val options = listOf(
                        SpeakerSort.NEWEST to R.string.sort_newest,
                        SpeakerSort.OLDEST to R.string.sort_oldest,
                        SpeakerSort.SERIES to R.string.sort_series,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        options.forEachIndexed { index, (sort, label) ->
                            SegmentedButton(
                                selected = page.sort == sort,
                                onClick = { viewModel.setSort(sort) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size, baseShape = BrandShape),
                            ) { Text(stringResource(label), maxLines = 1) }
                        }
                    }
                }
                if (page.groups.all { it.lectures.isEmpty() }) {
                    item { EmptyState(stringResource(R.string.lectures_empty)) }
                }
                page.groups.forEach { group ->
                    if (group.title != null) {
                        item(key = "group-" + group.title) {
                            Text(
                                group.title.ifEmpty { stringResource(R.string.no_series) },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(group.lectures, key = { it.id }) { row ->
                        LectureListItem(
                            row = row,
                            onClick = { onOpenLecture(row.id) },
                            showSpeaker = false,
                            onToggleFavorite = { viewModel.toggleFavorite(row.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(page: SpeakerPage) {
    val speaker = page.speaker ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpeakerAvatar(speaker.name, speaker.photoUrl, 84.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(speaker.name, style = MaterialTheme.typography.headlineSmall)
                speaker.nameEn?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.lecture_count, page.totalLectures.toBanglaDigits()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        speaker.bio?.takeIf { it.isNotBlank() }?.let { bio ->
            Text(
                bio,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .animateContentSize()
                    .clickable { expanded = !expanded },
            )
        }
    }
}
