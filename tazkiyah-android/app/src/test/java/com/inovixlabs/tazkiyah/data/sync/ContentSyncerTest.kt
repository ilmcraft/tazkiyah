package com.inovixlabs.tazkiyah.data.sync

import com.inovixlabs.tazkiyah.data.remote.HttpContentSource
import com.inovixlabs.tazkiyah.data.remote.ManifestFileDto
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ContentSyncerTest {

    private lateinit var server: MockWebServer
    private val store = FakeCatalogStore()
    private val state = FakeSyncState()
    private val syncer = ContentSyncer(store, state, appVersionCode = 1)

    /** path -> body served by the "GitHub" server. */
    private val files = mutableMapOf<String, String>()
    private var manifestOverride: String? = null

    private val speakers = """{"speakers":[{"id":"speaker-001","name":"বক্তা","nameEn":"Speaker","bio":"","order":1}]}"""
    private val categories = """{"categories":[{"id":"salah","name":"সালাত","icon":"prayer","order":1,"parentId":null}]}"""
    private val lectures = """{"speakerId":"speaker-001","lectures":[{"id":"spk001-0001","title":"তাওবা","categoryIds":["salah"],"media":{"type":"youtube","videoId":"abcdefghijk"},"addedAt":"2026-11-07"}]}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().removePrefix("/")
                if (path == "manifest.json") {
                    return MockResponse().setBody(manifestOverride ?: manifest(dataVersion = 1))
                }
                return files[path]?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        files["data/speakers.json"] = speakers
        files["data/categories.json"] = categories
        files["data/lectures/speaker-001.json"] = lectures
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun source(vararg bases: String = arrayOf(server.url("/").toString())) =
        HttpContentSource(OkHttpClient(), bases.toList())

    private fun manifest(
        dataVersion: Int,
        schemaVersion: Int = 1,
        minAppVersionCode: Int = 1,
        hashes: Map<String, String> = files.mapValues { sha256Hex(it.value.toByteArray()) },
    ): String {
        val entries = hashes.entries.joinToString(",") { (path, sha) -> """{"path":"$path","sha256":"$sha","size":1}""" }
        return """{"schemaVersion":$schemaVersion,"minAppVersionCode":$minAppVersionCode,"dataVersion":$dataVersion,""" +
            """"changelog":"নতুন","files":[$entries],"futureField":true}"""
    }

    /** Data files requested since the last call (drains the server's request queue). */
    private fun downloadedPaths(): List<String> = generateSequence { server.takeRequest(50, TimeUnit.MILLISECONDS) }
        .map { it.path.orEmpty().removePrefix("/") }
        .filter { it != "manifest.json" }
        .toList()

    @Test
    fun freshInstall_downloadsEverything_andSavesVersion() = runTest {
        manifestOverride = manifest(dataVersion = 3)

        val result = syncer.sync(source())

        assertEquals(SyncResult.Updated, result)
        assertEquals(listOf("data/speakers.json", "data/categories.json", "data/lectures/speaker-001.json"), store.imported)
        assertEquals(3, state.version)
        assertEquals("নতুন", state.changelog)
        assertTrue(state.announced)
    }

    @Test
    fun noChange_doesNotDownloadAnything() = runTest {
        manifestOverride = manifest(dataVersion = 3)
        syncer.sync(source())
        downloadedPaths() // drain
        store.imported.clear()

        val result = syncer.sync(source())

        assertEquals(SyncResult.UpToDate, result)
        assertTrue(store.imported.isEmpty())
        assertTrue(downloadedPaths().isEmpty())
    }

    @Test
    fun oneFileChanged_onlyThatFileIsDownloaded() = runTest {
        manifestOverride = manifest(dataVersion = 3)
        syncer.sync(source())
        downloadedPaths()
        store.imported.clear()

        files["data/lectures/speaker-001.json"] = lectures.replace("তাওবা", "তাওবার শর্ত")
        manifestOverride = manifest(dataVersion = 4)
        val result = syncer.sync(source())

        assertEquals(SyncResult.Updated, result)
        assertEquals(listOf("data/lectures/speaker-001.json"), store.imported)
        assertEquals(listOf("data/lectures/speaker-001.json"), downloadedPaths())
        assertEquals(4, state.version)
    }

    @Test
    fun hashMismatch_rejectsFile_andKeepsOldVersion() = runTest {
        val hashes = files.mapValues { sha256Hex(it.value.toByteArray()) }.toMutableMap()
        hashes["data/categories.json"] = "0".repeat(64)
        manifestOverride = manifest(dataVersion = 5, hashes = hashes)

        val result = syncer.sync(source())

        assertEquals(SyncResult.Partial, result)
        assertFalse("data/categories.json" in store.imported)
        assertTrue("data/speakers.json" in store.imported)
        assertEquals(0, state.version) // not saved, so the next sync retries the missing file

        // Next sync with a correct manifest only fetches what is still missing.
        store.imported.clear()
        manifestOverride = manifest(dataVersion = 5)
        assertEquals(SyncResult.Updated, syncer.sync(source()))
        assertEquals(listOf("data/categories.json"), store.imported)
        assertEquals(5, state.version)
    }

    @Test
    fun schemaVersionTooNew_requiresAppUpdate_andChangesNothing() = runTest {
        manifestOverride = manifest(dataVersion = 9, schemaVersion = SUPPORTED_SCHEMA_VERSION + 1)

        val result = syncer.sync(source())

        assertEquals(SyncResult.AppUpdateRequired, result)
        assertTrue(store.imported.isEmpty())
        assertTrue(state.appUpdateRequired)
        assertEquals(0, state.version)
    }

    @Test
    fun minAppVersionTooNew_requiresAppUpdate() = runTest {
        manifestOverride = manifest(dataVersion = 9, minAppVersionCode = 2)
        assertEquals(SyncResult.AppUpdateRequired, syncer.sync(source()))
        assertTrue(store.imported.isEmpty())
    }

    @Test
    fun removedFile_isRemovedFromCatalog() = runTest {
        manifestOverride = manifest(dataVersion = 3)
        syncer.sync(source())

        files.remove("data/lectures/speaker-001.json")
        manifestOverride = manifest(dataVersion = 4)
        assertEquals(SyncResult.Updated, syncer.sync(source()))
        assertEquals(listOf("data/lectures/speaker-001.json"), store.removed)
    }

    @Test
    fun malformedFile_isRejected() = runTest {
        store.failOn = "data/lectures/speaker-001.json"
        manifestOverride = manifest(dataVersion = 3)
        assertEquals(SyncResult.Partial, syncer.sync(source()))
        assertEquals(0, state.version)
    }

    @Test
    fun primaryDown_usesFallbackUrl() = runTest {
        manifestOverride = manifest(dataVersion = 2)
        val dead = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(503)
            }
            start()
        }
        try {
            val result = syncer.sync(source(dead.url("/").toString(), server.url("/").toString()))
            assertEquals(SyncResult.Updated, result)
            assertEquals(2, state.version)
        } finally {
            dead.shutdown()
        }
    }

    @Test
    fun offline_whenNoSourceAnswers() = runTest {
        server.shutdown()
        assertEquals(SyncResult.Offline, syncer.sync(source("http://127.0.0.1:1/")))
    }

    @Test
    fun seedImport_doesNotAnnounceChangelog() = runTest {
        manifestOverride = manifest(dataVersion = 1)
        assertEquals(SyncResult.Updated, syncer.sync(source(), isSeed = true))
        assertFalse(state.announced)
    }

    private class FakeCatalogStore : CatalogStore {
        val synced = mutableMapOf<String, String>()
        val imported = mutableListOf<String>()
        val removed = mutableListOf<String>()
        var failOn: String? = null

        override suspend fun syncedFiles() = synced.map { SyncedFile(it.key, it.value) }

        override suspend fun removeFile(path: String) {
            removed += path
            synced.remove(path)
        }

        override suspend fun importFile(file: ManifestFileDto, bytes: ByteArray) {
            if (file.path == failOn) error("malformed")
            imported += file.path
            synced[file.path] = file.sha256
        }
    }

    private class FakeSyncState : SyncStateStore {
        var version = 0
        var changelog: String? = null
        var announced = false
        var appUpdateRequired = false

        override suspend fun dataVersion() = version

        override suspend fun saveDataVersion(version: Int, changelog: String?, announce: Boolean) {
            this.version = version
            this.changelog = changelog
            this.announced = announce
        }

        override suspend fun setAppUpdateRequired(required: Boolean) {
            appUpdateRequired = required
        }
    }
}
