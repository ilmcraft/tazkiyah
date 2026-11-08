package com.inovixlabs.tazkiyah.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.AppScreen
import com.inovixlabs.tazkiyah.core.designsystem.EmptyState
import com.inovixlabs.tazkiyah.core.designsystem.LectureCard
import com.inovixlabs.tazkiyah.core.designsystem.LectureListItem
import com.inovixlabs.tazkiyah.core.designsystem.LectureThumbnail
import com.inovixlabs.tazkiyah.core.designsystem.LocalShowMessage
import com.inovixlabs.tazkiyah.core.designsystem.SectionHeader
import com.inovixlabs.tazkiyah.core.designsystem.UiStateContent
import com.inovixlabs.tazkiyah.core.designsystem.categoryIcon
import com.inovixlabs.tazkiyah.core.designsystem.durationLabel
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.feature.about.rememberLinkOpener
import com.inovixlabs.tazkiyah.data.repository.AppSettings
import com.inovixlabs.tazkiyah.data.sync.SyncResult

fun syncResultRes(result: SyncResult): Int = when (result) {
    SyncResult.Updated -> R.string.sync_updated
    SyncResult.UpToDate -> R.string.sync_up_to_date
    SyncResult.Offline -> R.string.sync_offline
    SyncResult.Partial -> R.string.sync_partial
    SyncResult.AppUpdateRequired -> R.string.sync_app_update
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenLecture: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val showMessage = LocalShowMessage.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.syncResults.collect { result ->
            showMessage(resources.getString(syncResultRes(result)))
        }
    }

    AppScreen(
        title = stringResource(R.string.app_name),
        largeTitle = true,
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            UiStateContent(state) { data ->
                // Banners sit above the list so an update notice is visible whatever the scroll position.
                Column(Modifier.fillMaxSize()) {
                    Banners(settings, viewModel::dismissChangelog, viewModel::dismissAppUpdate)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (data.isEmpty) {
                            item { EmptyState(stringResource(R.string.home_empty)) }
                        }
                        if (data.continueListening.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.home_continue)) }
                            item { LectureRowScroller(data.continueListening, onOpenLecture) }
                        }
                        if (data.today != null) {
                            item { SectionHeader(stringResource(R.string.home_today)) }
                            item { TodayCard(data.today, onClick = { onOpenLecture(data.today.id) }) }
                        } else if (!data.isEmpty) {
                            item {
                                SectionHeader(stringResource(R.string.home_today))
                                Text(
                                    stringResource(R.string.home_all_listened),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                        if (data.recent.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.home_new)) }
                            items(data.recent, key = { "recent-" + it.id }) { row ->
                                LectureListItem(row, onClick = { onOpenLecture(row.id) })
                            }
                        }
                        if (data.topics.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.home_topics)) }
                            item {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) {
                                    data.topics.forEach { topic ->
                                        AssistChip(
                                            onClick = { onOpenCategory(topic.category.id) },
                                            label = { Text(topic.category.name) },
                                            leadingIcon = {
                                                Icon(categoryIcon(topic.category.icon), null, Modifier.size(18.dp))
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Banners(settings: AppSettings?, onDismiss: () -> Unit, onDismissAppUpdate: (String) -> Unit) {
    settings ?: return
    val openLink = rememberLinkOpener()
    Column(Modifier.padding(horizontal = 16.dp)) {
        val update = settings.appUpdate
        if (update != null && settings.showAppUpdateBanner) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp)) {
                    Text(
                        stringResource(R.string.banner_app_update_available, update.versionName.toBanglaDigits()),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(stringResource(R.string.banner_app_update_desc), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onDismissAppUpdate(update.versionName) }) { Text(stringResource(R.string.later)) }
                        TextButton(onClick = { openLink(update.url) }) { Text(stringResource(R.string.download)) }
                    }
                }
            }
        }
        if (settings.appUpdateRequired) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.banner_app_update), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (settings.showChangelog) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp)) {
                    Text(stringResource(R.string.banner_new_content), style = MaterialTheme.typography.titleSmall)
                    Text(settings.changelog.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LectureRowScroller(rows: List<LectureRow>, onOpenLecture: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(rows, key = { it.id }) { row -> LectureCard(row, onClick = { onOpenLecture(row.id) }) }
    }
}

@Composable
private fun TodayCard(row: LectureRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        LectureThumbnail(row, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(16.dp)) {
            Text(row.lecture.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(row.speakerName, durationLabel(row.durationSec)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
