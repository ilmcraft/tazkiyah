package com.inovixlabs.tazkiyah.data.update

import com.inovixlabs.tazkiyah.BuildConfig
import com.inovixlabs.tazkiyah.data.remote.ContentJson
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

/** A published app version: tag `app-v1.2.0` -> versionName `1.2.0`. */
data class AppRelease(val versionName: String, val url: String)

enum class UpdateCheckResult { UpdateAvailable, UpToDate, Failed }

/**
 * The app is shared as an APK on GitHub Releases, so it checks there (at most once a day) and
 * shows "নতুন ভার্সন এসেছে" with a link to the release page. Nothing is downloaded or installed
 * automatically.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) {
    suspend fun checkIfDue(now: Long = System.currentTimeMillis()) {
        if (now - settings.current().lastAppCheckAt >= CHECK_INTERVAL_MS) check(now)
    }

    suspend fun check(now: Long = System.currentTimeMillis()): UpdateCheckResult {
        val latest = try {
            latestAppRelease(fetchReleases())
        } catch (e: IOException) {
            return UpdateCheckResult.Failed
        } catch (e: kotlinx.serialization.SerializationException) {
            return UpdateCheckResult.Failed
        }
        settings.saveAppReleaseCheck(latest?.versionName, latest?.url, now)
        return if (latest != null && isNewer(latest.versionName, BuildConfig.VERSION_NAME)) {
            UpdateCheckResult.UpdateAvailable
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    private suspend fun fetchReleases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Tazkiyah-Android/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            ContentJson.decodeFromString<List<GitHubRelease>>(response.body?.string().orEmpty())
        }
    }

    companion object {
        const val TAG_PREFIX = "app-v"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

        /** Newest published app release (drafts, pre-releases and content tags ignored). */
        fun latestAppRelease(releases: List<GitHubRelease>): AppRelease? = releases
            .filter { !it.draft && !it.prerelease && it.tagName.startsWith(TAG_PREFIX) }
            .mapNotNull { r -> r.tagName.removePrefix(TAG_PREFIX).takeIf { parts(it) != null }?.let { AppRelease(it, r.htmlUrl) } }
            .maxWithOrNull { a, b -> compareVersions(a.versionName, b.versionName) }

        fun isNewer(candidate: String, current: String): Boolean = compareVersions(candidate, current) > 0

        fun compareVersions(a: String, b: String): Int {
            val pa = parts(a) ?: return -1
            val pb = parts(b) ?: return 1
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
                if (diff != 0) return diff
            }
            return 0
        }

        private fun parts(version: String): List<Int>? =
            version.trim().split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
    }
}
