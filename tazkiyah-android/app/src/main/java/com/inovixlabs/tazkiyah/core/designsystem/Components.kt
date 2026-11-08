package com.inovixlabs.tazkiyah.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Foundation
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.inovixlabs.tazkiyah.core.util.formatClock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.inovixlabs.tazkiyah.R
import com.inovixlabs.tazkiyah.core.util.parseIsoDate
import com.inovixlabs.tazkiyah.core.util.toBanglaDigits
import com.inovixlabs.tazkiyah.data.local.entity.LectureRow
import com.inovixlabs.tazkiyah.data.local.entity.MediaType
import com.inovixlabs.tazkiyah.playback.youTubeThumbnail
import java.time.LocalDate

/** "নতুন" badge window. */
private const val NEW_DAYS = 7L

fun LectureRow.isNew(today: LocalDate = LocalDate.now()): Boolean =
    parseIsoDate(lecture.addedAt)?.let { !it.isBefore(today.minusDays(NEW_DAYS)) } == true

fun LectureRow.thumbnailUrl(): String? = when (lecture.mediaType) {
    MediaType.YOUTUBE -> lecture.youtubeId?.let(::youTubeThumbnail)
    else -> speakerPhotoUrl
}

@Composable
fun durationLabel(totalSec: Long?): String? {
    val sec = totalSec?.takeIf { it > 0 } ?: return null
    val minutes = (sec + 30) / 60
    return if (minutes >= 60) {
        stringResource(R.string.hours_minutes, (minutes / 60).toBanglaDigits(), (minutes % 60).toBanglaDigits())
    } else {
        stringResource(R.string.minutes_short, minutes.coerceAtLeast(1).toBanglaDigits())
    }
}

@Composable
fun SpeakerAvatar(name: String, photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1),
            style = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Thumbnail tile: YouTube thumbnail for videos, a calm icon tile for audio. */
@Composable
fun LectureThumbnail(
    row: LectureRow,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    showDuration: Boolean = true,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        val icon = if (row.lecture.mediaType == MediaType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Headphones
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.Center).size(28.dp),
        )
        row.thumbnailUrl()?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val duration = row.durationSec
        if (showDuration && duration != null) {
            // Length on the picture, as on YouTube ("৪৫:১২").
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            ) {
                Text(
                    formatClock(duration),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (showProgress && (row.progressSec ?: 0) > 0 && !row.isCompleted) {
            LinearProgressIndicator(
                progress = { row.progressFraction },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                strokeCap = StrokeCap.Butt,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, gold: Boolean = true) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (gold) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (gold) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

/** Standard list row for a lecture. */
@Composable
fun LectureListItem(
    row: LectureRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSpeaker: Boolean = true,
    onToggleFavorite: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LectureThumbnail(row, Modifier.width(112.dp).aspectRatio(16f / 9f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.lecture.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (showSpeaker) row.speakerName?.let(::add)
                durationLabel(row.durationSec)?.let(::add)
                row.lecture.seriesPart?.let { add(stringResource(R.string.series_part, row.lecture.seriesTitle.orEmpty(), it.toBanglaDigits())) }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (row.isNew()) Badge(stringResource(R.string.badge_new))
                if (row.isCompleted) Badge(stringResource(R.string.badge_completed), gold = false)
                if (row.isSaved) Badge(stringResource(R.string.badge_saved), gold = false)
                if (row.isSaving) Badge(stringResource(R.string.badge_saving), gold = false)
                when (row.lecture.mediaType) {
                    MediaType.AUDIO -> Badge(stringResource(R.string.audio_badge), gold = false)
                    MediaType.VIDEO -> Badge(stringResource(R.string.video_badge), gold = false)
                }
            }
        }
        if (onToggleFavorite != null) {
            FavoriteButton(row.isFavorite, onToggleFavorite)
        }
        trailing?.invoke()
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
            tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Card for horizontal rows on Home. */
@Composable
fun LectureCard(row: LectureRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(220.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        LectureThumbnail(row, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Text(
            row.lecture.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(row.speakerName, durationLabel(row.durationSec)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (row.isNew()) {
                Spacer(Modifier.width(6.dp))
                Badge(stringResource(R.string.badge_new))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.AutoMirrored.Rounded.MenuBook) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = BrightGold, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CompletedMark(modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.CheckCircle,
        contentDescription = stringResource(R.string.badge_completed),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(18.dp),
    )
}

/** Maps the `icon` field of categories.json to a Material icon. */
fun categoryIcon(name: String?): ImageVector = when (name) {
    "foundation" -> Icons.Rounded.Foundation
    "mosque" -> Icons.Rounded.Mosque
    "prayer" -> Icons.Rounded.SelfImprovement
    "moon" -> Icons.Rounded.Nightlight
    "hand", "hands" -> Icons.Rounded.VolunteerActivism
    "kaaba" -> Icons.Rounded.Explore
    "heart" -> Icons.Rounded.Favorite
    "smile" -> Icons.Rounded.Mood
    "book" -> Icons.AutoMirrored.Rounded.MenuBook
    "scroll" -> Icons.Rounded.AutoStories
    "star" -> Icons.Rounded.Star
    "home" -> Icons.Rounded.FamilyRestroom
    "child" -> Icons.Rounded.ChildCare
    "wallet" -> Icons.Rounded.Payments
    "hourglass" -> Icons.Rounded.HourglassBottom
    "spark" -> Icons.Rounded.Bolt
    "megaphone" -> Icons.Rounded.Campaign
    "landmark" -> Icons.Rounded.AccountBalance
    "news" -> Icons.Rounded.Newspaper
    "spa" -> Icons.Rounded.Spa
    "diamond" -> Icons.Rounded.Diamond
    else -> Icons.Rounded.Category
}
