package com.inovixlabs.tazkiyah.data.sync

import androidx.room.withTransaction
import com.inovixlabs.tazkiyah.data.local.TazkiyahDatabase
import com.inovixlabs.tazkiyah.data.local.entity.SyncedFileEntity
import com.inovixlabs.tazkiyah.data.remote.ManifestFileDto

/** One transaction per file: the catalog rows and the synced_files record change together. */
class RoomCatalogStore(
    private val db: TazkiyahDatabase,
    private val importer: CatalogImporter,
) : CatalogStore {

    override suspend fun syncedFiles(): List<SyncedFile> =
        db.syncDao().syncedFiles().map { SyncedFile(it.path, it.sha256) }

    override suspend fun removeFile(path: String) = db.withTransaction {
        importer.remove(path)
        db.syncDao().deleteSynced(path)
    }

    override suspend fun importFile(file: ManifestFileDto, bytes: ByteArray) = db.withTransaction {
        importer.import(file.path, bytes)
        db.syncDao().upsertSynced(
            SyncedFileEntity(file.path, file.sha256, file.size, System.currentTimeMillis()),
        )
    }
}
