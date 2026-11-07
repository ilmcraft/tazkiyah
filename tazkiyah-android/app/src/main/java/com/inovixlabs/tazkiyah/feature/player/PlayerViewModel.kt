package com.inovixlabs.tazkiyah.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.inovixlabs.tazkiyah.data.local.entity.CategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.repository.AppSettings
import com.inovixlabs.tazkiyah.data.repository.CatalogRepository
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import com.inovixlabs.tazkiyah.playback.PlaybackManager
import com.inovixlabs.tazkiyah.playback.PlayerState
import com.inovixlabs.tazkiyah.playback.SleepTimer
import com.inovixlabs.tazkiyah.playback.audio.ExoAudioController
import com.inovixlabs.tazkiyah.playback.youtube.YouTubeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-wide player state for the mini player, full player and the YouTube host. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackManager,
    catalog: CatalogRepository,
    settingsRepository: SettingsRepository,
    val youTubeController: YouTubeController,
    audioController: ExoAudioController,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val current: StateFlow<LectureRow?> = playback.current
    val playerState: StateFlow<PlayerState> = playback.playerState
    val nextInSeries: StateFlow<LectureRow?> = playback.nextInSeries
    val listenOnly: StateFlow<Boolean> = playback.listenOnly
    val sleepTimer: StateFlow<SleepTimer> = playback.sleepTimer
    val audioPlayer: StateFlow<Player?> = audioController.player

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
}
