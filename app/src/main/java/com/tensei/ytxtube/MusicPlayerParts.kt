package com.tensei.ytxtube

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * The music player's seek bar: a slim rounded track that thickens while it is dragged, with a small
 * round handle. Tapping anywhere jumps there; dragging previews the position and seeks on release.
 */
@Composable
internal fun MusicSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(1L)
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = (drag ?: (positionMs.coerceIn(0L, duration).toFloat() / duration)).coerceIn(0f, 1f)
    val active = drag != null
    val track by animateDpAsState(if (active) 7.dp else 4.dp, label = "seek_track")
    val knob by animateDpAsState(if (active) 9.dp else 6.dp, label = "seek_knob")

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { p ->
                        onSeek((p.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { p -> drag = (p.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            drag?.let { onSeek((it * duration).toLong()) }
                            drag = null
                        },
                        onDragCancel = { drag = null },
                        onHorizontalDrag = { change, _ ->
                            drag = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            val h = track.toPx()
            val y = size.height / 2f
            val r = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(color.copy(alpha = 0.22f), Offset(0f, y - h / 2f), Size(size.width, h), r)
            drawRoundRect(color, Offset(0f, y - h / 2f), Size(size.width * fraction, h), r)
            drawCircle(color, knob.toPx(), Offset(size.width * fraction, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = drag?.let { (it * duration).toLong() } ?: positionMs.coerceIn(0L, duration)
            Text(formatDuration(shown), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color.copy(alpha = 0.7f))
            Text(
                if (durationMs > 0) "-" + formatDuration((duration - shown).coerceAtLeast(0L)) else "--:--",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.7f),
            )
        }
    }
}

/** The songs that play after this one. Tapping one plays it and keeps the rest of the list after it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicQueueSheet(
    queue: List<VideoItem>,
    onPlay: (Int) -> Unit,
    onDismiss: () -> Unit,
    onMove: (Int, Int) -> Unit = { _, _ -> },
    onRemove: (Int) -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainerLow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Up next",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = c.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (queue.isEmpty()) {
                Text(
                    "Nothing queued. When this song ends, a related song plays automatically.",
                    fontSize = 14.sp,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                ReorderableQueue(
                    queue,
                    onMove = onMove,
                    onRemove = onRemove,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) { i, track, handle ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(c.surfaceContainerLow)
                                .clickable { onPlay(i) }
                                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.surfaceContainerHighest),
                            ) {
                                AsyncImage(
                                    model = highResMusicThumbnail(track.thumbnail) ?: sharpenFeedThumbnail(null, track.id),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.channel,
                                    fontSize = 13.sp,
                                    color = c.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(handle.size(40.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Rounded.DragHandle,
                                    "Drag to reorder",
                                    tint = c.onSurfaceVariant,
                                )
                            }
                        }
                }
            }
        }
    }
}
