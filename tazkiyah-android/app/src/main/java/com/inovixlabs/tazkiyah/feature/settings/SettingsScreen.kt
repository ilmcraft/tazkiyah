package com.inovixlabs.tazkiyah.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inovixlabs.tazkiyah.BuildConfig
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.AppScreen
import com.inovixlabs.tazkiyah.core.designsystem.LocalShowMessage
import com.inovixlabs.tazkiyah.core.designsystem.ThemeMode
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.data.repository.AppSettings
import com.inovixlabs.tazkiyah.data.repository.CatalogCounts
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import com.inovixlabs.tazkiyah.data.sync.ContentSyncCoordinator
import com.inovixlabs.tazkiyah.data.sync.SyncResult
import com.inovixlabs.tazkiyah.data.sync.SyncScheduler
import com.inovixlabs.tazkiyah.feature.home.syncResultRes
import com.inovixlabs.tazkiyah.feature.player.speedLabel
import com.inovixlabs.tazkiyah.playback.SPEED_OPTIONS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncCoordinator: ContentSyncCoordinator,
    private val syncScheduler: SyncScheduler,
    catalog: CatalogRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val counts: StateFlow<CatalogCounts?> = catalog.counts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isSyncing: StateFlow<Boolean> = syncCoordinator.isSyncing

    private val _results = Channel<SyncResult>(Channel.BUFFERED)
    val results: Flow<SyncResult> = _results.receiveAsFlow()

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch { settingsRepository.setDefaultSpeed(speed) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWifiOnlyUpdates(enabled)
            syncScheduler.schedulePeriodic(enabled)
        }
    }

    fun checkNow() {
        viewModelScope.launch { _results.send(syncCoordinator.syncRemote()) }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val syncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val showMessage = LocalShowMessage.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.results.collect { showMessage(resources.getString(syncResultRes(it))) }
    }
    val s = settings ?: AppSettings()
    val months = stringArrayResource(R.array.month_names)

    AppScreen(title = stringResource(R.string.settings), onBack = onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Section(stringResource(R.string.settings_appearance)) }
            item {
                val options = listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        options.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) { Text(stringResource(label), maxLines = 1) }
                        }
                    }
                }
            }
            item { Section(stringResource(R.string.settings_playback)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.settings_default_speed), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        SPEED_OPTIONS.forEach { speed ->
                            FilterChip(
                                selected = s.defaultSpeed == speed,
                                onClick = { viewModel.setDefaultSpeed(speed) },
                                label = { Text(stringResource(R.string.speed_value, speedLabel(speed))) },
                            )
                        }
                    }
                }
            }
            item { Section(stringResource(R.string.settings_updates)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_wifi_only)) },
                    supportingContent = { Text(stringResource(R.string.settings_wifi_only_desc)) },
                    trailingContent = { Switch(checked = s.wifiOnlyUpdates, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.toggleable(value = s.wifiOnlyUpdates, role = Role.Switch, onValueChange = viewModel::setWifiOnly),
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Button(onClick = viewModel::checkNow, enabled = !syncing) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (syncing) R.string.syncing else R.string.settings_check_now))
                    }
                    Text(
                        stringResource(R.string.settings_data_version, s.dataVersion.toBanglaDigits()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val lastSync = if (s.lastSyncAt > 0) {
                        val dt = Instant.ofEpochMilli(s.lastSyncAt).atZone(ZoneId.systemDefault())
                        "${dt.dayOfMonth.toBanglaDigits()} ${months[dt.monthValue - 1]}, " +
                            "%d:%02d".format(dt.hour, dt.minute).toBanglaDigits()
                    } else {
                        stringResource(R.string.settings_never)
                    }
                    Text(
                        stringResource(R.string.settings_last_sync, lastSync),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Section(stringResource(R.string.settings_about)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME.toBanglaDigits()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    counts?.let {
                        Text(
                            stringResource(
                                R.string.settings_catalog_counts,
                                it.speakers.toBanglaDigits(),
                                it.categories.toBanglaDigits(),
                                it.lectures.toBanglaDigits(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.settings_focus_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item {
                val update = s.appUpdate
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_open_about)) },
                    supportingContent = update?.let {
                        {
                            Text(
                                stringResource(R.string.settings_open_about_update, it.versionName.toBanglaDigits()),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(top = 8.dp).clickable(onClick = onOpenAbout),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}
