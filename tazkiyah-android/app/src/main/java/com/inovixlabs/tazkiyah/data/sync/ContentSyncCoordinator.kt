package com.inovixlabs.tazkiyah.data.sync

import com.inovixlabs.tazkiyah.data.remote.HttpContentSource
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import javax.inject.Inject
import javax.inject.Singleton

/** Entry point for every sync: bundled seed first (cheap when already imported), then GitHub. */
@Singleton
class ContentSyncCoordinator @Inject constructor(
    private val syncer: ContentSyncer,
    private val remote: HttpContentSource,
    private val seed: AssetContentSource,
    private val settings: SettingsRepository,
) {
    private val running = MutableStateFlow(0)
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    suspend fun ensureSeeded(): SyncResult = syncer.sync(seed, isSeed = true)

    suspend fun syncRemote(): SyncResult {
        running.update { it + 1 }
        _isSyncing.value = true
        try {
            ensureSeeded()
            val result = syncer.sync(remote)
            settings.recordSync(result)
            return result
        } finally {
            val left = running.updateAndGet { it - 1 }
            _isSyncing.value = left > 0
        }
    }

}
