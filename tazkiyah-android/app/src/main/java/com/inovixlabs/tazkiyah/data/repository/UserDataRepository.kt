package com.inovixlabs.tazkiyah.data.repository

import androidx.room.withTransaction
import com.inovixlabs.tazkiyah.data.local.TazkiyahDatabase
import com.inovixlabs.tazkiyah.data.local.entity.FavoriteEntity
import com.inovixlabs.tazkiyah.data.local.entity.ListenLogEntity
import com.inovixlabs.tazkiyah.data.local.entity.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Favorites, resume positions and listening time — the tables content sync never touches. */
@Singleton
class UserDataRepository @Inject constructor(
    private val db: TazkiyahDatabase,
) {
    private val dao get() = db.userDao()

    suspend fun progress(lectureId: String): PlaybackProgressEntity? = dao.progress(lectureId)

    suspend fun saveProgress(lectureId: String, positionSec: Long, durationSec: Long, completed: Boolean) {
        val previous = dao.progress(lectureId)
        dao.upsertProgress(
            PlaybackProgressEntity(
                lectureId = lectureId,
                positionSec = positionSec.coerceAtLeast(0),
                durationSec = if (durationSec > 0) durationSec else previous?.durationSec ?: 0,
                // Sticky: once a lecture was heard to 95% it stays in "শেষ করা লেকচার".
                completed = completed || previous?.completed == true,
                lastPlayedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun toggleFavorite(lectureId: String): Boolean = db.withTransaction {
        if (dao.isFavorite(lectureId)) {
            dao.removeFavorite(lectureId)
            false
        } else {
            dao.addFavorite(FavoriteEntity(lectureId, System.currentTimeMillis()))
            true
        }
    }

    suspend fun addListenTime(seconds: Long, date: LocalDate = LocalDate.now()) {
        if (seconds <= 0) return
        db.withTransaction {
            dao.ensureLogDay(date.toString())
            dao.addToLogDay(date.toString(), seconds)
        }
    }

    /** Last 7 days including today. */
    fun weeklyListenLog(today: LocalDate = LocalDate.now()): Flow<List<ListenLogEntity>> =
        dao.listenLogSince(today.minusDays(6).toString())
}
