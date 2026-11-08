package com.inovixlabs.tazkiyah

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.inovixlabs.tazkiyah.data.download.DownloadRepository
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import com.inovixlabs.tazkiyah.data.sync.ContentSyncCoordinator
import com.inovixlabs.tazkiyah.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TazkiyahApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncCoordinator: ContentSyncCoordinator
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var downloads: DownloadRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Bundled snapshot first, so a fresh install shows lectures even offline.
            syncCoordinator.ensureSeeded()
            val wifiOnly = settings.current().wifiOnlyUpdates
            syncScheduler.schedulePeriodic(wifiOnly)
            syncScheduler.syncOnAppStart(wifiOnly)
            downloads.resumeUnfinished()
        }
    }
}
