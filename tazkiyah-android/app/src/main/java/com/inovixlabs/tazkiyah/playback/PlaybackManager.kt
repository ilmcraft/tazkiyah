package com.inovixlabs.tazkiyah.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import com.inovixlabs.tazkiyah.data.download.DownloadRepository
import com.inovixlabs.tazkiyah.data.download.SavedMedia
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.local.entity.MediaType
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import com.inovixlabs.tazkiyah.data.repository.UserDataRepository
import com.inovixlabs.tazkiyah.playback.audio.AudioItem
import com.inovixlabs.tazkiyah.playback.audio.ExoAudioController
import com.inovixlabs.tazkiyah.playback.youtube.YouTubeController
import com.inovixlabs.tazkiyah.playback.youtube.YouTubeSessionBridge
import com.inovixlabs.tazkiyah.playback.youtube.YouTubeSessionPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepTimer {
    data object Off : SleepTimer
    data class Countdown(val endsAtMillis: Long) : SleepTimer
    data object EndOfLecture : SleepTimer
}

/** Completed once 95% has been heard (docs/PLAN.md, "রিজিউম"). */
private const val COMPLETE_FRACTION = 0.95

fun youTubeThumbnail(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

/** What the player's 16:9 area shows for the current lecture. */
enum class PlayerSurface {
    /** Streaming through the focused YouTube player. */
    YOUTUBE,

    /** A video file (saved on the phone, or a `media.type = video` lecture) in ExoPlayer. */
    VIDEO,

    /** Sound only: the speaker artwork. */
    ARTWORK,
}

/** The lecture that is loaded, and the saved file it plays from (null while streaming). */
private data class Active(val row: LectureRow, val saved: SavedMedia?)

/**
 * The single owner of "what is playing": picks the controller for the lecture, resumes where the
 * user stopped, saves progress every 5 seconds and on pause, logs listening time, and runs the
 * sleep timer. Nothing here ever auto-plays another lecture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlaybackManager @Inject constructor(
    private val catalog: CatalogRepository,
    private val userData: UserDataRepository,
    private val settings: SettingsRepository,
    private val audio: ExoAudioController,
    private val youtube: YouTubeController,
    private val youTubeBridge: YouTubeSessionBridge,
    private val downloads: DownloadRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val active = MutableStateFlow<Active?>(null)
    private val currentRow: LectureRow? get() = active.value?.row

    /** The current lecture, live from Room (favorite/progress stay fresh). */
    val current: StateFlow<LectureRow?> = active
        .map { it?.row?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else catalog.lecture(id) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val playerState: StateFlow<PlayerState> = active
        .flatMapLatest { a -> controllerFor(a)?.state ?: flowOf(PlayerState()) }
        .stateIn(scope, SharingStarted.Eagerly, PlayerState())

    val surface: StateFlow<PlayerSurface?> = active
        .map { a ->
            when {
                a == null -> null
                a.saved != null -> if (a.saved.isVideo) PlayerSurface.VIDEO else PlayerSurface.ARTWORK
                a.row.lecture.mediaType == MediaType.YOUTUBE -> PlayerSurface.YOUTUBE
                a.row.lecture.mediaType == MediaType.VIDEO -> PlayerSurface.VIDEO
                else -> PlayerSurface.ARTWORK
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** True while the lecture plays from a file saved on the phone. */
    val playingSaved: StateFlow<Boolean> = active
        .map { it?.saved != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Next part of the same series, the only thing ever suggested after a lecture. */
    val nextInSeries: StateFlow<LectureRow?> = current
        .map { it?.id to it?.lecture?.seriesId }
        .distinctUntilChanged()
        .flatMapLatest { (id, seriesId) ->
            if (id == null || seriesId == null) flowOf(null)
            else catalog.series(seriesId).map { list ->
                val index = list.indexOfFirst { it.id == id }
                if (index >= 0) list.getOrNull(index + 1) else null
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val listenOnly: StateFlow<Boolean> = settings.settings
        .map { it.youtubeListenOnly }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _sleepTimer = MutableStateFlow<SleepTimer>(SleepTimer.Off)
    val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null

    init {
        // Save progress and listening time every 5 seconds while playing.
        scope.launch {
            while (true) {
                delay(5_000)
                if (playerState.value.isPlaying) {
                    saveProgressNow()
                    userData.addListenTime(5)
                }
            }
        }
        // ...and whenever playback pauses.
        scope.launch {
            playerState.map { it.isPlaying }.distinctUntilChanged().drop(1).collect { playing ->
                if (!playing) saveProgressNow()
            }
        }
        scope.launch {
            playerState.map { it.isEnded }.distinctUntilChanged().collect { ended -> if (ended) onEnded() }
        }
        // Remember a duration the catalog doesn't have.
        scope.launch {
            playerState.map { it.durationSec }.distinctUntilChanged().collect { d ->
                val row = currentRow ?: return@collect
                if (d > 0 && row.lecture.durationSec == null && row.knownDurationSec != d) saveProgressNow()
            }
        }
        scope.launch { youtube.state.collect { youTubeBridge.refresh() } }
    }

    /** A saved file always plays in ExoPlayer, even for a YouTube lecture — no network needed. */
    private fun controllerFor(a: Active?): PlaybackController? = when {
        a == null -> null
        a.saved != null -> audio
        a.row.lecture.mediaType == MediaType.YOUTUBE -> youtube
        else -> audio
    }

    private fun activeController(): PlaybackController? = controllerFor(active.value)

    /** Opens a lecture; if it is already the current one, just resumes it. */
    fun open(lectureId: String, autoPlay: Boolean = true) {
        scope.launch {
            if (currentRow?.id == lectureId) {
                if (autoPlay && !playerState.value.isPlaying) activeController()?.play()
                return@launch
            }
            val row = catalog.lectureNow(lectureId) ?: return@launch
            load(row, autoPlay)
        }
    }

    /**
     * Called before a saved file is deleted: if that lecture is playing from it, it carries on by
     * streaming from where it was (the player stays open).
     */
    suspend fun leaveSavedFile(lectureId: String) {
        val now = active.value ?: return
        if (now.row.id != lectureId || now.saved == null) return
        load(now.row, autoPlay = playerState.value.isPlaying, useSaved = false)
    }

    private suspend fun load(row: LectureRow, autoPlay: Boolean, useSaved: Boolean = true) {
        saveProgressNow()
        val progress = userData.progress(row.id)
        val duration = row.durationSec ?: progress?.durationSec?.takeIf { it > 0 } ?: 0L
        val savedAt = progress?.positionSec ?: 0L
        val resumeAt = if (savedAt > 5 && (duration == 0L || savedAt < duration * COMPLETE_FRACTION)) savedAt
        else row.lecture.startSec
        val speed = settings.current().defaultSpeed
        val next = Active(row, if (useSaved) downloads.savedMedia(row.id) else null)

        val previous = activeController()
        if (previous !== controllerFor(next)) previous?.stop()
        active.value = next
        if (_sleepTimer.value == SleepTimer.EndOfLecture) cancelSleepTimer()

        if (controllerFor(next) === youtube) {
            youtube.load(row.id, row.lecture.youtubeId.orEmpty(), resumeAt, speed, autoPlay)
        } else {
            audio.load(row.toAudioItem(duration, next.saved), resumeAt, speed, autoPlay)
        }
        updateYouTubeSession()
        userData.saveProgress(row.id, resumeAt, duration, completed = false)
    }

    fun togglePlayPause() {
        val c = activeController() ?: return
        if (playerState.value.isPlaying || playerState.value.isBuffering) c.pause() else c.play()
    }

    fun play() = activeController()?.play()
    fun pause() = activeController()?.pause()
    fun seekTo(positionSec: Long) = activeController()?.seekTo(positionSec)
    fun seekBy(deltaSec: Long) = activeController()?.seekBy(deltaSec)

    fun setSpeed(speed: Float) {
        activeController()?.setSpeed(speed)
        scope.launch { settings.setDefaultSpeed(speed) }
    }

    fun replay() {
        val c = activeController() ?: return
        c.seekTo(currentRow?.lecture?.startSec ?: 0)
        c.play()
    }

    /** Stops playback and hides the mini player. */
    fun close() {
        scope.launch {
            saveProgressNow()
            activeController()?.stop()
            active.value = null
            updateYouTubeSession()
            cancelSleepTimer()
        }
    }

    fun toggleFavorite(lectureId: String) {
        scope.launch { userData.toggleFavorite(lectureId) }
    }

    fun setListenOnly(enabled: Boolean) {
        scope.launch { settings.setYoutubeListenOnly(enabled) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimer.value = SleepTimer.Countdown(endsAt)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            activeController()?.pause()
            _sleepTimer.value = SleepTimer.Off
        }
    }

    fun setSleepAtEndOfLecture() {
        sleepJob?.cancel()
        _sleepTimer.value = SleepTimer.EndOfLecture
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleepTimer.value = SleepTimer.Off
    }

    private suspend fun saveProgressNow() {
        val row = currentRow ?: return
        val s = activeController()?.state?.value ?: return
        // A stopped/reset or not-yet-loaded player must never overwrite the saved position.
        if (s.mediaId != row.id) return
        val duration = s.durationSec.takeIf { it > 0 } ?: row.durationSec ?: 0L
        val completed = duration > 0 && s.positionSec >= duration * COMPLETE_FRACTION
        userData.saveProgress(row.id, s.positionSec, duration, completed)
    }

    private suspend fun onEnded() {
        val row = currentRow ?: return
        if (playerState.value.mediaId != row.id) return
        val duration = playerState.value.durationSec.takeIf { it > 0 } ?: row.durationSec ?: 0L
        // Position 0 so the next open starts from the beginning; "completed" stays.
        userData.saveProgress(row.id, 0, duration, completed = true)
        if (_sleepTimer.value == SleepTimer.EndOfLecture) _sleepTimer.value = SleepTimer.Off
    }

    /**
     * Streaming YouTube keeps playing in the background, so it gets its own media session
     * (notification, lock screen, headset buttons) hosted by the foreground PlaybackService.
     * ExoPlayer (files, saved lectures) has its own session there already.
     */
    private fun updateYouTubeSession() {
        val row = currentRow
        if (row == null || activeController() !== youtube) {
            youTubeBridge.publish(null)
            return
        }
        if (youTubeBridge.player.value?.mediaId == row.id) return
        val metadata = MediaMetadata.Builder()
            .setTitle(row.lecture.title)
            .setArtist(row.speakerName)
            .setArtworkUri((row.speakerPhotoUrl ?: youTubeThumbnail(row.lecture.youtubeId.orEmpty())).toUri())
            .build()
        youTubeBridge.publish(YouTubeSessionPlayer(youtube, row.id, metadata))
        // Binding the playback service lets it host the session and go foreground while playing.
        scope.launch { runCatching { audio.connect() } }
    }

    private fun LectureRow.toAudioItem(duration: Long, saved: SavedMedia?) = AudioItem(
        id = id,
        url = saved?.file?.toUri()?.toString() ?: lecture.mediaUrl.orEmpty(),
        mimeType = saved?.mimeType ?: lecture.mimeType,
        title = lecture.title,
        artist = speakerName,
        artworkUrl = speakerPhotoUrl ?: lecture.youtubeId?.let(::youTubeThumbnail),
        durationSec = duration,
    )
}
