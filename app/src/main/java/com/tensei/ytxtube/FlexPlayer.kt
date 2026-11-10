package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * The player in flex mode. Stood half open (tabletop), the video fills the upper half above the
 * fold and everything you touch is on the lower half lying on the table: the seek bar, big play
 * controls, captions, speed, quality, comments and what plays next. Held like a book, the video is
 * on the left page and the rest on the right. The split follows the real fold position the phone
 * reports, so nothing sits on the crease.
 */
@Composable
fun FlexPlayer(
    state: PlayerState,
    fold: FoldState,
    onOpen: (VideoItem) -> Unit,
    onCollapse: () -> Unit,
    onFullVideo: () -> Unit,
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        val tabletop = fold.mode == FoldMode.Tabletop
        val total = (if (tabletop) constraints.maxHeight else constraints.maxWidth).toFloat()
        val shift = if (tabletop) origin.y else origin.x
        val start = (fold.hingeStart?.let { it - shift } ?: (total / 2f)).coerceIn(total * 0.3f, total * 0.7f)
        val end = (fold.hingeEnd?.let { it - shift } ?: start).coerceIn(start, start + total * 0.1f)
        val firstPart = with(density) { start.toDp() }
        val crease = with(density) { (end - start).toDp() }

        val video: @Composable (Modifier) -> Unit = { m ->
            Box(m.background(Color.Black)) {
                VideoContent(
                    state = state,
                    fullscreen = true,
                    onCollapse = onCollapse,
                    onToggleFullscreen = onFullVideo,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (tabletop) {
            Column(Modifier.fillMaxSize()) {
                video(Modifier.fillMaxWidth().height(firstPart))
                if (crease > 0.dp) Spacer(Modifier.height(crease))
                FlexPanel(state, onOpen, onFullVideo, wide = true, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                video(Modifier.width(firstPart).fillMaxHeight())
                if (crease > 0.dp) Spacer(Modifier.width(crease))
                FlexPanel(state, onOpen, onFullVideo, wide = false, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun FlexPanel(
    state: PlayerState,
    onOpen: (VideoItem) -> Unit,
    onFullVideo: () -> Unit,
    wide: Boolean,
    modifier: Modifier,
) {
    val c = MaterialTheme.colorScheme
    val isAmoled = ThemeSettings.mode == "amoled"
    val page = if (isAmoled) Color.Black else c.surface
    val item = state.current ?: return
    val details = state.details
    Box(modifier.background(page)) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        details?.item?.title ?: item.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        details?.item?.channel ?: item.channel,
                        fontSize = 13.sp,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    FlexSeekBar(state)
                    FlexTransport(state, wide)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FlexChip(Icons.Rounded.ClosedCaption, "Captions", selected = state.selectedSubtitleLanguage != null) {
                                state.toggleCaptions()
                            }
                        }
                        item {
                            FlexChip(Icons.Rounded.Speed, if (state.speed == 1f) "Speed" else "${state.speed}×") {
                                state.sheet = PlayerSheet.Speed
                            }
                        }
                        item { FlexChip(Icons.Rounded.HighQuality, "Quality") { state.sheet = PlayerSheet.Quality } }
                        item {
                            FlexChip(Icons.Rounded.Comment, "Comments", selected = state.sheet == PlayerSheet.Comments) {
                                state.sheet = if (state.sheet == PlayerSheet.Comments) null else PlayerSheet.Comments
                            }
                        }
                        item { FlexChip(Icons.Rounded.Fullscreen, "Full video", onClick = onFullVideo) }
                    }
                    Spacer(Modifier.height(14.dp))
                    val next = details?.related.orEmpty()
                    if (next.isNotEmpty()) {
                        Text("Up next", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            items(FeedFilters.withoutBlocked(details?.related.orEmpty()).filter { it.id != item.id }, key = { "rel:" + it.id }) { video ->
                FlexRelatedRow(video) { onOpen(video) }
            }
        }
        if (state.sheet == PlayerSheet.Comments) {
            Box(Modifier.fillMaxSize()) {
                CommentsPanel(
                    state.comments,
                    partialTop = 0.dp,
                    onClose = { state.sheet = null },
                    creatorAvatar = details?.item?.avatar,
                    creatorName = details?.item?.channel.orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun FlexSeekBar(state: PlayerState) {
    val c = MaterialTheme.colorScheme
    val duration = state.durationMs.coerceAtLeast(0L)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val fraction = if (dragging) dragValue else if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                if (duration > 0) state.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = c.primary,
                activeTrackColor = c.primary,
                inactiveTrackColor = c.onSurface.copy(alpha = 0.18f),
            ),
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(if (dragging) (dragValue * duration).toLong() else state.positionMs), fontSize = 12.sp, color = c.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(if (duration > 0) formatDuration(duration) else "", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun FlexTransport(state: PlayerState, wide: Boolean) {
    val gap = if (wide) 26.dp else 14.dp
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlexRound(Icons.Rounded.Replay10, "Back 10 seconds") { state.seekBy(-10_000) }
        FlexRound(Icons.Rounded.SkipPrevious, "Previous") { state.skipPrevious() }
        FlexRound(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", big = true) {
            state.togglePlay()
        }
        FlexRound(Icons.Rounded.SkipNext, "Next") { state.skipNext() }
        FlexRound(Icons.Rounded.Forward10, "Forward 10 seconds") { state.seekBy(10_000) }
    }
}

@Composable
private fun FlexRound(icon: ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    Box(
        Modifier
            .size(if (big) 66.dp else 50.dp)
            .clip(CircleShape)
            .background(if (big) c.onSurface else c.surfaceContainerHigh)
            .pressable(pressedScale = 0.9f) {
                Haptics.tap(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (big) c.surface else c.onSurface, modifier = Modifier.size(if (big) 36.dp else 26.dp))
    }
}

@Composable
private fun FlexChip(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.primary else c.surfaceContainerHigh)
            .pressable(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) c.onPrimary else c.onSurface, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) c.onPrimary else c.onSurface)
    }
}

@Composable
private fun FlexRelatedRow(video: VideoItem, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .prefetchOnPress(video.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(150.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceContainerHighest),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(video.channel, fontSize = 12.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
