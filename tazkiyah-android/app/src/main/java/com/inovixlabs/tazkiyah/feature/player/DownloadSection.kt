package com.inovixlabs.tazkiyah.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.data.download.DownloadChoice
import com.inovixlabs.tazkiyah.data.local.entity.DownloadEntity
import com.inovixlabs.tazkiyah.data.local.entity.DownloadError
import com.inovixlabs.tazkiyah.data.local.entity.DownloadKind
import com.inovixlabs.tazkiyah.data.local.entity.DownloadStatus
import java.util.Locale

/** "১২ এমবি" / "১.৪ জিবি". */
@Composable
fun sizeLabel(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        stringResource(R.string.download_size_gb, String.format(Locale.US, "%.1f", mb / 1024).toBanglaDigits())
    } else {
        stringResource(R.string.download_size_mb, maxOf(1, Math.round(mb)).toBanglaDigits())
    }
}

@Composable
fun downloadKindLabel(kind: String, quality: Int): String =
    if (kind == DownloadKind.VIDEO && quality > 0) stringResource(R.string.download_video_quality, quality.toBanglaDigits())
    else stringResource(R.string.download_audio_only)

@Composable
fun downloadErrorText(code: String?): String = stringResource(
    when (code) {
        DownloadError.NETWORK -> R.string.download_error_network
        DownloadError.UNAVAILABLE -> R.string.download_error_unavailable
        DownloadError.STORAGE -> R.string.download_error_storage
        else -> R.string.download_error_unknown
    },
)

/** The full player's "save for offline" row, its options dialog and the delete confirmation. */
@Composable
fun DownloadSection(
    download: DownloadEntity?,
    playingSaved: Boolean,
    choices: DownloadChoicesState,
    onSave: () -> Unit,
    onChoose: (DownloadChoice) -> Unit,
    onDismissChoices: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    when (download?.status) {
        null -> ListItem(
            headlineContent = { Text(stringResource(R.string.download_save)) },
            supportingContent = { Text(stringResource(R.string.download_save_desc)) },
            leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) },
            colors = colors,
            modifier = Modifier.clickable(onClick = onSave),
        )
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> {
            val total = download.totalBytes
            val fraction = if (total > 0) (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) else 0f
            ListItem(
                headlineContent = {
                    Text(
                        when {
                            download.status == DownloadStatus.RUNNING ->
                                stringResource(R.string.download_saving, (fraction * 100).toInt().toBanglaDigits())
                            download.error == DownloadError.NETWORK -> stringResource(R.string.download_waiting_network)
                            else -> stringResource(R.string.download_waiting)
                        },
                    )
                },
                supportingContent = {
                    Column {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(4.dp),
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
                        )
                        val sizes = if (total > 0) {
                            stringResource(R.string.download_progress_bytes, sizeLabel(download.downloadedBytes), sizeLabel(total))
                        } else {
                            sizeLabel(download.downloadedBytes)
                        }
                        Text(downloadKindLabel(download.kind, download.quality) + " · " + sizes)
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Downloading, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.download_cancel))
                    }
                },
                colors = colors,
            )
        }
        DownloadStatus.DONE -> ListItem(
            headlineContent = { Text(stringResource(R.string.download_saved)) },
            supportingContent = {
                val parts = listOfNotNull(
                    downloadKindLabel(download.kind, download.quality),
                    sizeLabel(download.downloadedBytes),
                    if (playingSaved) stringResource(R.string.download_playing_saved) else null,
                )
                Text(parts.joinToString(" · "))
            },
            leadingContent = { Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.download_delete))
                }
            },
            colors = colors,
        )
        else -> ListItem(
            headlineContent = { Text(stringResource(R.string.download_failed)) },
            supportingContent = { Text(downloadErrorText(download.error)) },
            leadingContent = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.download_retry)) }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.download_cancel))
                    }
                }
            },
            colors = colors,
        )
    }

    if (choices != DownloadChoicesState.Hidden) {
        DownloadChoicesDialog(choices, onChoose, onRetry = onSave, onDismiss = onDismissChoices)
    }
    if (confirmDelete) {
        DeleteDownloadDialog(
            onConfirm = {
                confirmDelete = false
                onRemove()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DownloadChoicesDialog(
    state: DownloadChoicesState,
    onChoose: (DownloadChoice) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_choose_title)) },
        text = {
            when (state) {
                DownloadChoicesState.Loading, DownloadChoicesState.Hidden -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.download_loading_options))
                }
                is DownloadChoicesState.Failed -> Text(downloadErrorText(state.error))
                is DownloadChoicesState.Ready -> Column {
                    state.choices.forEach { choice ->
                        ListItem(
                            headlineContent = { Text(downloadKindLabel(choice.kind, choice.quality)) },
                            supportingContent = {
                                if (choice.sizeBytes > 0) Text(stringResource(R.string.download_size_approx, sizeLabel(choice.sizeBytes)))
                            },
                            leadingContent = {
                                Icon(
                                    if (choice.kind == DownloadKind.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Headphones,
                                    contentDescription = null,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onChoose(choice) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state is DownloadChoicesState.Failed) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.download_retry)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_cancel)) } },
    )
}

@Composable
fun DeleteDownloadDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_delete_title)) },
        text = { Text(stringResource(R.string.download_delete_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.download_delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_cancel)) } },
    )
}
