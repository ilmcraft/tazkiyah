package com.inovixlabs.tazkiyah.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.util.formatClock
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.playback.PlayerState
import com.inovixlabs.tazkiyah.playback.SPEED_OPTIONS

fun speedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
    return text.toBanglaDigits()
}

/** Seek bar with elapsed / total time. Dragging only seeks on release. */
@Composable
fun SeekBar(
    state: PlayerState,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationSec.coerceAtLeast(0)
    val position = if (dragging) dragValue else state.positionSec.toFloat().coerceAtMost(duration.toFloat())
    val textColor = if (onDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Slider(
            value = if (duration > 0) position else 0f,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..(duration.coerceAtLeast(1).toFloat()),
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary,
                inactiveTrackColor = if (onDark) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(position.toLong()), style = MaterialTheme.typography.labelMedium, color = textColor)
            Box(Modifier.weight(1f))
            Text(
                if (duration > 0) formatClock(duration) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
        }
    }
}

fun playPauseIcon(state: PlayerState) = when {
    state.isEnded -> Icons.Rounded.Replay
    state.isPlaying || state.isBuffering -> Icons.Rounded.Pause
    else -> Icons.Rounded.PlayArrow
}

/** −10s · play/pause · +10s */
@Composable
fun TransportRow(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    big: Boolean = true,
) {
    val tint = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(if (big) 28.dp else 16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onSeekBy(-10) }) {
            Icon(Icons.Rounded.Replay10, stringResource(R.string.rewind_10), tint = tint, modifier = Modifier.size(if (big) 34.dp else 28.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(if (big) 72.dp else 56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    playPauseIcon(state),
                    contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                    modifier = Modifier.size(if (big) 40.dp else 32.dp),
                )
            }
            if (state.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (big) 78.dp else 62.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    strokeWidth = 3.dp,
                )
            }
        }
        IconButton(onClick = { onSeekBy(10) }) {
            Icon(Icons.Rounded.Forward10, stringResource(R.string.forward_10), tint = tint, modifier = Modifier.size(if (big) 34.dp else 28.dp))
        }
    }
}

@Composable
fun SpeedButton(speed: Float, onSpeed: (Float) -> Unit, onDark: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(
                stringResource(R.string.speed_value, speedLabel(speed)),
                style = MaterialTheme.typography.labelLarge,
                color = if (onDark) Color.White else MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SPEED_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.speed_value, speedLabel(option))) },
                    onClick = {
                        onSpeed(option)
                        open = false
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
