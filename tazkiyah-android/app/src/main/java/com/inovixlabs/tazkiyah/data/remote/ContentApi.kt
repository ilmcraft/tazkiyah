package com.inovixlabs.tazkiyah.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Where the syncer reads the manifest and data files from: GitHub (with CDN fallback) or the bundled seed. */
interface ContentSource {
    suspend fun fetchManifest(): ManifestDto?
    suspend fun download(path: String): ByteArray?
}

const val MANIFEST_PATH = "manifest.json"

/** Tries each base URL in order: raw.githubusercontent.com first, jsDelivr as backup. */
class HttpContentSource(
    private val client: OkHttpClient,
    private val baseUrls: List<String>,
    private val json: Json = ContentJson,
) : ContentSource {

    override suspend fun fetchManifest(): ManifestDto? = withContext(Dispatchers.IO) {
        baseUrls.firstNotNullOfOrNull { base ->
            get(base + MANIFEST_PATH)?.let { bytes ->
                runCatching { json.decodeFromString(ManifestDto.serializer(), bytes.decodeToString()) }.getOrNull()
            }
        }
    }

    override suspend fun download(path: String): ByteArray? = withContext(Dispatchers.IO) {
        baseUrls.firstNotNullOfOrNull { base -> get(base + path) }
    }

    private fun get(url: String): ByteArray? = try {
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
