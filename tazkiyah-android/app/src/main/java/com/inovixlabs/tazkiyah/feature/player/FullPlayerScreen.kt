package com.inovixlabs.tazkiyah.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.designsystem.CompletedMark
import com.inovixlabs.tazkiyah.core.designsystem.FavoriteButton
import com.inovixlabs.tazkiyah.core.designsystem.LectureListItem
import com.inovixlabs.tazkiyah.core.designsystem.LectureThumbnail
import com.inovixlabs.tazkiyah.core.designsystem.SpeakerAvatar
import com.inovixlabs.tazkiyah.core.designsystem.categoryIcon
import com.inovixlabs.tazkiyah.core.designsystem.durationLabel
import com.inovixlabs.tazkiyah.core.util.formatBanglaDate
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.data.local.entity.CategoryEntity
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.local.entity.MediaType
import com.inovixlabs.tazkiyah.playback.PlaybackError
import com.inovixlabs.tazkiyah.playback.PlayerState
import com.inovixlabs.tazkiyah.playback.SleepTimer
import kotlinx.coroutines.delay

@Composable
fun errorText(error: PlaybackError): String = stringResource(
    when (error) {
        PlaybackError.NotEmbeddable -> R.string.error_not_embeddable
        PlaybackError.NotFound -> R.string.error_not_found
        PlaybackError.Network -> R.string.error_network
        PlaybackError.Unknown -> R.string.error_playback
    },
)

/**
 * The lecture & player screen. For YouTube the 16:9 slot stays empty: the app root draws
 * [com.inovixlabs.tazkiyah.playback.youtube.FocusedYouTubePlayer] exactly over [onVideoSlot]'s
 * bounds, so the same player survives collapsing to the mini player.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullPlayerScreen(
    lecture: LectureRow,
    state: PlayerState,
    listenOnly: Boolean,
    sleepTimer: SleepTimer,
    seriesParts: List<LectureRow>,
    categories: List<CategoryEntity>,
    audioPlayer: Player?,
    onVideoSlot: (Rect) -> Unit,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onListenOnly: (Boolean) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepAtEnd: () -> Unit,
    onSleepCancel: () -> Unit,
    onOpenLecture: (String) -> Unit,
    onOpenSpeaker: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = stringArrayResource(R.array.month_names)
    val isYouTube = lecture.lecture.mediaType == MediaType.YOUTUBE
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.collapse_player))
                }
                Text(
                    stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                FavoriteButton(lecture.isFavorite, onToggleFavorite)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .onGloballyPositioned { onVideoSlot(it.boundsInRoot()) },
            ) {
                when (lecture.lecture.mediaType) {
                    MediaType.YOUTUBE -> Box(Modifier.fillMaxSize().background(Color.Black))
                    MediaType.VIDEO -> VideoSurface(audioPlayer)
                    else -> AudioArtwork(lecture)
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(lecture.lecture.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onOpenSpeaker(lecture.lecture.speakerId) },
                        ) {
                            SpeakerAvatar(lecture.speakerName.orEmpty(), lecture.speakerPhotoUrl, 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                lecture.speakerName.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (lecture.isCompleted) {
                                Spacer(Modifier.width(8.dp))
                                CompletedMark()
                                Text(
                                    " " + stringResource(R.string.badge_completed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        val meta = listOfNotNull(
                            durationLabel(lecture.durationSec),
                            formatBanglaDate(lecture.lecture.publishedAt, months)?.let { stringResource(R.string.published_on, it) },
                            formatBanglaDate(lecture.lecture.addedAt, months)?.let { stringResource(R.string.added_on, it) },
                        )
                        if (meta.isNotEmpty()) {
                            Text(
                                meta.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SeekBar(state, onSeekTo)
                        TransportRow(state, onPlayPause, onSeekBy, Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.speed), style = MaterialTheme.typography.labelLarge)
                            SpeedButton(state.speed, onSpeed)
                            Spacer(Modifier.weight(1f))
                            SleepTimerButton(sleepTimer, onSleepMinutes, onSleepAtEnd, onSleepCancel)
                        }
                        state.error?.let {
                            Text(
                                errorText(it),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                if (isYouTube) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.listen_only)) },
                            supportingContent = { Text(stringResource(R.string.listen_only_desc)) },
                            leadingContent = { Icon(Icons.Rounded.Headphones, contentDescription = null) },
                            trailingContent = { Switch(checked = listenOnly, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.toggleable(value = listenOnly, role = Role.Switch, onValueChange = onListenOnly),
                        )
                    }
                }
                if (categories.isNotEmpty()) {
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            categories.forEach { category ->
                                AssistChip(
                                    onClick = { onOpenCategory(category.id) },
                                    label = { Text(category.name) },
                                    leadingIcon = { Icon(categoryIcon(category.icon), null, Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                }
                lecture.lecture.description?.let { description ->
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.description), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(description, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (seriesParts.size > 1) {
                    item {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        Text(
                            stringResource(R.string.series_other_parts),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(seriesParts, key = { it.id }) { part ->
                        LectureListItem(
                            row = part,
                            onClick = { onOpenLecture(part.id) },
                            showSpeaker = false,
                            modifier = if (part.id == lecture.id) {
                                Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            } else {
                                Modifier
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AudioArtwork(lecture: LectureRow) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            SpeakerAvatar(lecture.speakerName.orEmpty(), lecture.speakerPhotoUrl, 88.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                lecture.speakerName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VideoSurface(player: Player?) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun SleepTimerButton(
    timer: SleepTimer,
    onMinutes: (Int) -> Unit,
    onAtEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis(), timer) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val label = when (timer) {
        SleepTimer.Off -> stringResource(R.string.sleep_timer)
        SleepTimer.EndOfLecture -> stringResource(R.string.sleep_at_end_active)
        is SleepTimer.Countdown -> {
            val minutes = ((timer.endsAtMillis - now + 59_999) / 60_000).coerceAtLeast(1)
            stringResource(R.string.sleep_remaining, minutes.toBanglaDigits())
        }
    }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(15, 30, 45, 60).forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_minutes, minutes.toBanglaDigits())) },
                    onClick = {
                        onMinutes(minutes)
                        open = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sleep_end_of_lecture)) },
                onClick = {
                    onAtEnd()
                    open = false
                },
            )
            if (timer != SleepTimer.Off) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_off)) },
                    onClick = {
                        onCancel()
                        open = false
                    },
                )
            }
        }
    }
}

/** Persistent bar above the bottom navigation. */
@Composable
fun MiniPlayer(
    lecture: LectureRow,
    state: PlayerState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column {
            val fraction = if (state.durationSec > 0) (state.positionSec.toFloat() / state.durationSec).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Butt,
                drawStopIndicator = {},
            )
            Row(
                Modifier.clickable(onClick = onExpand).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LectureThumbnail(lecture, Modifier.width(72.dp).aspectRatio(16f / 9f), showProgress = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(lecture.lecture.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        lecture.speakerName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        playPauseIcon(state),
                        contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                }
            }
        }
    }
}
