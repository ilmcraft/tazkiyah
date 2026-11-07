package com.inovixlabs.tazkiyah.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class ContentSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: ContentSyncCoordinator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (coordinator.syncRemote()) {
        // WorkManager applies exponential backoff; give up after a few tries until the next trigger.
        SyncResult.Offline, SyncResult.Partial -> if (runAttemptCount < 5) Result.retry() else Result.success()
        SyncResult.AppUpdateRequired, SyncResult.UpToDate, SyncResult.Updated -> Result.success()
    }
}

/** App start runs one sync; a periodic sync runs every 12 hours (Wi-Fi only if the user chose so). */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(wifiOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<ContentSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun syncOnAppStart(wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<ContentSyncWorker>()
            .setConstraints(constraints(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(STARTUP_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val PERIODIC_WORK = "content-sync-periodic"
        const val STARTUP_WORK = "content-sync-startup"
    }
}
