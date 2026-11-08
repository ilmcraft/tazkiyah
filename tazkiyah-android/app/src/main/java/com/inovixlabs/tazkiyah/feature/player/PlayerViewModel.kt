package com.inovixlabs.tazkiyah.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.inovixlabs.tazkiyah.data.download.DownloadChoice
import com.inovixlabs.tazkiyah.data.download.DownloadRepository
import com.inovixlabs.tazkiyah.data.download.downloadErrorOf
import com.inovixlabs.tazkiyah.data.local.entity.CategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.DownloadEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.repository.AppSettings
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import com.inovixlabs.tazkiyah.playback.PlaybackManager
import com.inovixlabs.tazkiyah.playback.PlayerSurface
import com.inovixlabs.tazkiyah.playback.PlayerState
import com.inovixlabs.tazkiyah.playback.SleepTimer
import com.inovixlabs.tazkiyah.playback.audio.ExoAudioController
import com.inovixlabs.tazkiyah.playback.youtube.YouTubeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The "how to save" dialog: hidden, looking up the sizes, the options, or why it can't. */
sealed interface DownloadChoicesState {
    data object Hidden : DownloadChoicesState
    data object Loading : DownloadChoicesState
    data class Ready(val choices: List<DownloadChoice>) : DownloadChoicesState
    data class Failed(val error: String) : DownloadChoicesState
}

/** App-wide player state for the mini player, full player and the YouTube host. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackManager,
    catalog: CatalogRepository,
    settingsRepository: SettingsRepository,
    val youTubeController: YouTubeController,
    audioController: ExoAudioController,
    private val downloads: DownloadRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val current: StateFlow<LectureRow?> = playback.current
    val playerState: StateFlow<PlayerState> = playback.playerState
    val nextInSeries: StateFlow<LectureRow?> = playback.nextInSeries
    val listenOnly: StateFlow<Boolean> = playback.listenOnly
    val sleepTimer: StateFlow<SleepTimer> = playback.sleepTimer
    val audioPlayer: StateFlow<Player?> = audioController.player
    val surface: StateFlow<PlayerSurface?> = playback.surface
    val playingSaved: StateFlow<Boolean> = playback.playingSaved

    val download: StateFlow<DownloadEntity?> = current
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else downloads.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _downloadChoices = MutableStateFlow<DownloadChoicesState>(DownloadChoicesState.Hidden)
    val downloadChoices: StateFlow<DownloadChoicesState> = _downloadChoices.asStateFlow()
    private var choicesJob: Job? = null

    val seriesParts: StateFlow<List<LectureRow>> = current
        .map { it?.lecture?.seriesId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else catalog.series(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = current
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else catalog.categoriesForLecture(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(lectureId: String) = playback.open(lectureId)
    fun togglePlayPause() = playback.togglePlayPause()
    fun seekTo(positionSec: Long) = playback.seekTo(positionSec)
    fun seekBy(deltaSec: Long) = playback.seekBy(deltaSec)
    fun setSpeed(speed: Float) = playback.setSpeed(speed)
    fun replay() = playback.replay()
    fun close() = playback.close()
    fun toggleFavorite(lectureId: String) = playback.toggleFavorite(lectureId)
    fun setListenOnly(enabled: Boolean) = playback.setListenOnly(enabled)
    fun setSleepTimer(minutes: Int) = playback.setSleepTimer(minutes)
    fun setSleepAtEnd() = playback.setSleepAtEndOfLecture()
    fun cancelSleepTimer() = playback.cancelSleepTimer()

    fun showDownloadChoices() {
        val id = current.value?.id ?: return
        choicesJob?.cancel()
        _downloadChoices.value = DownloadChoicesState.Loading
        choicesJob = viewModelScope.launch {
            _downloadChoices.value = try {
                DownloadChoicesState.Ready(downloads.choices(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadChoicesState.Failed(downloadErrorOf(e))
            }
        }
    }

    fun dismissDownloadChoices() {
        choicesJob?.cancel()
        _downloadChoices.value = DownloadChoicesState.Hidden
    }

    fun startDownload(choice: DownloadChoice) {
        val id = current.value?.id ?: return
        dismissDownloadChoices()
        viewModelScope.launch { downloads.start(id, choice) }
    }

    fun retryDownload() {
        val id = current.value?.id ?: return
        viewModelScope.launch { downloads.retry(id) }
    }

    /** Cancels a download in progress, or deletes the saved file (playback switches to streaming). */
    fun removeDownload() {
        val id = current.value?.id ?: return
        viewModelScope.launch {
            playback.leaveSavedFile(id)
            downloads.remove(id)
        }
    }
}
