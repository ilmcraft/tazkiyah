package com.inovixlabs.tazkiyah.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.inovixlabs.tazkiyah.core.designsystem.ThemeMode
import com.inovixlabs.tazkiyah.data.sync.SyncResult
import com.inovixlabs.tazkiyah.data.sync.SyncStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val wifiOnlyUpdates: Boolean = false,
    val defaultSpeed: Float = 1f,
    val youtubeListenOnly: Boolean = false,
    val dataVersion: Int = 0,
    val changelog: String? = null,
    val showChangelog: Boolean = false,
    val appUpdateRequired: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastSyncResult: SyncResult? = null,
    val recentSearches: List<String> = emptyList(),
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SyncStateStore {
    private val store = context.settingsStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        val version = p[DATA_VERSION] ?: 0
        val changelog = p[CHANGELOG]
        AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            wifiOnlyUpdates = p[WIFI_ONLY] ?: false,
            defaultSpeed = p[DEFAULT_SPEED] ?: 1f,
            youtubeListenOnly = p[YT_LISTEN_ONLY] ?: false,
            dataVersion = version,
            changelog = changelog,
            showChangelog = !changelog.isNullOrBlank() && version > (p[CHANGELOG_SEEN] ?: 0),
            appUpdateRequired = p[APP_UPDATE_REQUIRED] ?: false,
            lastSyncAt = p[LAST_SYNC_AT] ?: 0,
            lastSyncResult = p[LAST_SYNC_RESULT]?.let { runCatching { SyncResult.valueOf(it) }.getOrNull() },
            recentSearches = p[RECENT_SEARCHES]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setWifiOnlyUpdates(enabled: Boolean) = store.edit { it[WIFI_ONLY] = enabled }
    suspend fun setDefaultSpeed(speed: Float) = store.edit { it[DEFAULT_SPEED] = speed }
    suspend fun setYoutubeListenOnly(enabled: Boolean) = store.edit { it[YT_LISTEN_ONLY] = enabled }

    suspend fun dismissChangelog() = store.edit { it[CHANGELOG_SEEN] = it[DATA_VERSION] ?: 0 }

    suspend fun addRecentSearch(query: String) = store.edit { p ->
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty()) return@edit
        val current = p[RECENT_SEARCHES]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        p[RECENT_SEARCHES] = (listOf(q) + current.filterNot { it == q }).take(8).joinToString("\n")
    }

    suspend fun clearRecentSearches() = store.edit { it.remove(RECENT_SEARCHES) }

    suspend fun recordSync(result: SyncResult) = store.edit {
        it[LAST_SYNC_RESULT] = result.name
        if (result != SyncResult.Offline) it[LAST_SYNC_AT] = System.currentTimeMillis()
    }

    // ---- SyncStateStore ----

    override suspend fun dataVersion(): Int = store.data.first()[DATA_VERSION] ?: 0

    override suspend fun saveDataVersion(version: Int, changelog: String?, announce: Boolean) {
        store.edit {
            it[DATA_VERSION] = version
            if (changelog != null) it[CHANGELOG] = changelog else it.remove(CHANGELOG)
            if (!announce) it[CHANGELOG_SEEN] = version
        }
    }

    override suspend fun setAppUpdateRequired(required: Boolean) {
        store.edit { it[APP_UPDATE_REQUIRED] = required }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val YT_LISTEN_ONLY = booleanPreferencesKey("youtube_listen_only")
        val DATA_VERSION = intPreferencesKey("data_version")
        val CHANGELOG = stringPreferencesKey("changelog")
        val CHANGELOG_SEEN = intPreferencesKey("changelog_seen_version")
        val APP_UPDATE_REQUIRED = booleanPreferencesKey("app_update_required")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNC_RESULT = stringPreferencesKey("last_sync_result")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }
}
