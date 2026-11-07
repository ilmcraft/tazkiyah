package com.inovixlabs.tazkiyah.data.sync

import com.inovixlabs.tazkiyah.data.remote.ContentSource
import com.inovixlabs.tazkiyah.data.remote.ManifestFileDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/** Highest content `schemaVersion` this build understands. */
const val SUPPORTED_SCHEMA_VERSION = 1

enum class SyncResult { Offline, AppUpdateRequired, UpToDate, Partial, Updated }

data class SyncedFile(val path: String, val sha256: String)

/** Catalog side of the database. Each call is one transaction; user tables are never touched. */
interface CatalogStore {
    suspend fun syncedFiles(): List<SyncedFile>
    suspend fun removeFile(path: String)

    /** Parses and imports [bytes]; throws if the file is malformed (the transaction rolls back). */
    suspend fun importFile(file: ManifestFileDto, bytes: ByteArray)
}

interface SyncStateStore {
    suspend fun dataVersion(): Int
    suspend fun saveDataVersion(version: Int, changelog: String?, announce: Boolean)
    suspend fun setAppUpdateRequired(required: Boolean)
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * docs/PLAN.md, "আপডেট মেকানিজম": nothing reaches the database unless the manifest is
 * compatible, the version is newer and the file's sha256 matches; `dataVersion` is saved only
 * when every file landed, so a partial sync retries just the missing files next time.
 */
class ContentSyncer(
    private val store: CatalogStore,
    private val state: SyncStateStore,
    private val appVersionCode: Int,
) {
    private val mutex = Mutex()

    /** [isSeed] = importing the snapshot bundled in the APK (no changelog banner). */
    suspend fun sync(source: ContentSource, isSeed: Boolean = false): SyncResult = mutex.withLock {
        val manifest = source.fetchManifest() ?: return SyncResult.Offline
        if (manifest.schemaVersion > SUPPORTED_SCHEMA_VERSION || manifest.minAppVersionCode > appVersionCode) {
            if (!isSeed) state.setAppUpdateRequired(true)
            return SyncResult.AppUpdateRequired
        }
        if (!isSeed) state.setAppUpdateRequired(false)
        if (manifest.dataVersion <= state.dataVersion()) return SyncResult.UpToDate

        val local = store.syncedFiles().associateBy { it.path }
        val remotePaths = manifest.files.map { it.path }.toSet()
        (local.keys - remotePaths).forEach { path -> store.removeFile(path) }

        var allOk = true
        val changed = manifest.files
            .filter { local[it.path]?.sha256 != it.sha256.lowercase() }
            .sortedBy { importOrder(it.path) }
        for (file in changed) {
            val bytes = source.download(file.path)
            if (bytes == null || sha256Hex(bytes) != file.sha256.lowercase()) {
                allOk = false
                continue
            }
            try {
                store.importFile(file.copy(sha256 = file.sha256.lowercase()), bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                allOk = false
            }
        }
        if (!allOk) return SyncResult.Partial

        state.saveDataVersion(manifest.dataVersion, manifest.changelog, announce = !isSeed)
        SyncResult.Updated
    }

    // Speakers first so lecture rows can be indexed with speaker names.
    private fun importOrder(path: String) = when (path) {
        CatalogPaths.SPEAKERS -> 0
        CatalogPaths.CATEGORIES -> 1
        else -> 2
    }
}

object CatalogPaths {
    const val SPEAKERS = "data/speakers.json"
    const val CATEGORIES = "data/categories.json"
    const val LECTURES_DIR = "data/lectures/"
}
