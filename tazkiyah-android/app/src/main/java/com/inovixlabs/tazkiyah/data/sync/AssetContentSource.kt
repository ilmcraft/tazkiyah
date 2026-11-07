package com.inovixlabs.tazkiyah.data.sync

import android.content.res.AssetManager
import com.inovixlabs.tazkiyah.data.remote.ContentJson
import com.inovixlabs.tazkiyah.data.remote.ContentSource
import com.inovixlabs.tazkiyah.data.remote.MANIFEST_PATH
import com.inovixlabs.tazkiyah.data.remote.ManifestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** The content snapshot bundled in the APK under assets/seed (first launch works offline). */
class AssetContentSource(
    private val assets: AssetManager,
    private val root: String = "seed",
) : ContentSource {

    override suspend fun fetchManifest(): ManifestDto? = withContext(Dispatchers.IO) {
        read(MANIFEST_PATH)?.let { bytes ->
            runCatching { ContentJson.decodeFromString(ManifestDto.serializer(), bytes.decodeToString()) }.getOrNull()
        }
    }

    override suspend fun download(path: String): ByteArray? = withContext(Dispatchers.IO) { read(path) }

    private fun read(path: String): ByteArray? = try {
        assets.open("$root/$path").use { it.readBytes() }
    } catch (_: IOException) {
        null
    }
}
