package com.tensei.ytxtube

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * The song position between the player's own updates (it reports four times a second), moved on
 * every frame so a word lights up exactly when it is sung.
 */
@Composable
internal fun rememberLyricPlayhead(positionMs: Long, playing: Boolean, speed: Float): State<Long> {
    val head = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, playing, speed) {
        head.longValue = positionMs
        if (!playing) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            head.longValue = positionMs + ((now - start) / 1_000_000.0 * speed).toLong()
        }
    }
    return head
}

/**
 * Apple Music style synced lyrics: big bold lines, the one being sung bright and the rest dimmed
 * and softly blurred, word by word when the source has word timing, a Latin reading under lines in
 * other scripts, and a soft spring that keeps the current line in the middle.
 */
@Composable
internal fun AppleSyncedLyrics(
    lyrics: LyricsDoc,
    positionMs: Long,
    playing: Boolean,
    speed: Float,
    romanized: List<String?>?,
    onSeek: (Long) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val lines = lyrics.lines
    val offset = PlaybackPrefs.lyricsOffsetMs
    val head = rememberLyricPlayhead(positionMs, playing, speed)
    val current by remember(lines, offset) {
        derivedStateOf { lines.indexOfLast { it.timeMs in 0..(head.value + offset) } }
    }
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val large = PlaybackPrefs.lyricsTextLarge
    val size = if (large) 26.sp else 19.sp
    val lineHeight = if (large) 33.sp else 26.sp
    val blurOn = PlaybackPrefs.lyricsBlur && Build.VERSION.SDK_INT >= 31

    LaunchedEffect(current, dragged) {
        if (current < 0 || dragged) return@LaunchedEffect
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == current }
        if (visible == null) listState.scrollToItem(current)
        val settled = listState.layoutInfo
        val item = settled.visibleItemsInfo.firstOrNull { it.index == current } ?: return@LaunchedEffect
        val delta = item.offset + item.size / 2f - (settled.viewportStartOffset + settled.viewportEndOffset) * 0.4f
        if (abs(delta) > 1f) listState.animateScrollBy(delta, spring(dampingRatio = 0.86f, stiffness = 160f))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = maxHeight * 0.35f, bottom = maxHeight * 0.6f),
        ) {
            itemsIndexed(lines, key = { i, line -> "$i-${line.timeMs}" }) { index, line ->
                val active = index == current
                val distance = if (current < 0) 2 else abs(index - current)
                val activation by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                    label = "lyric-line",
                )
                val blur by animateFloatAsState(
                    targetValue = if (!blurOn || active || dragged) 0f else (distance.coerceAtMost(4) * 1.1f),
                    animationSpec = spring(stiffness = 200f),
                    label = "lyric-blur",
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (line.timeMs >= 0) onSeek((line.timeMs - offset).coerceAtLeast(0L)) }
                        .padding(vertical = if (large) 10.dp else 8.dp)
                        .graphicsLayer {
                            val scale = 0.96f + 0.04f * activation
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .then(if (blur > 0.05f) Modifier.blur(blur.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
                ) {
                    val dim = c.onSurface.copy(alpha = if (index < current) 0.38f else 0.32f)
                    if (active && line.words.isNotEmpty()) {
                        WordSweepLine(line, head, offset, dim, c.onSurface, size, lineHeight)
                    } else {
                        Text(
                            line.text,
                            color = lerpColor(dim, c.onSurface, activation),
                            fontSize = size,
                            lineHeight = lineHeight,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RoundedHeavy,
                        )
                    }
                    line.background?.let { bg ->
                        Text(
                            bg,
                            color = lerpColor(dim, c.onSurface.copy(alpha = 0.75f), activation).copy(alpha = 0.6f),
                            fontSize = size * 0.62f,
                            lineHeight = lineHeight * 0.66f,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    romanized?.getOrNull(index)?.let { reading ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            reading,
                            color = lerpColor(dim, c.onSurface.copy(alpha = 0.78f), activation),
                            fontSize = size * 0.6f,
                            lineHeight = lineHeight * 0.64f,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** The line being sung, each word brightening as it is sung (Apple Music's word timing). */
@Composable
private fun WordSweepLine(
    line: LyricLine,
    head: State<Long>,
    offset: Int,
    dim: Color,
    lit: Color,
    size: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
) {
    val pos = head.value + offset
    val text = buildAnnotatedString {
        line.words.forEachIndexed { i, word ->
            val span = (word.endMs - word.startMs).coerceAtLeast(1L)
            val f = ((pos - word.startMs).toFloat() / span).coerceIn(0f, 1f)
            withStyle(SpanStyle(color = lerpColor(dim, lit, f))) { append(word.text) }
            if (i < line.words.size - 1) append(' ')
        }
    }
    Text(
        text,
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = FontWeight.Bold,
        fontFamily = RoundedHeavy,
    )
}

/** Lyrics without timing: the whole text, with Latin readings under lines in other scripts. */
@Composable
internal fun PlainLyricsView(lyrics: LyricsDoc, romanized: List<String?>?) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(end = 8.dp),
    ) {
        lyrics.lines.forEachIndexed { index, line ->
            Text(
                line.text,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.onSurface.copy(alpha = 0.92f),
            )
            romanized?.getOrNull(index)?.let {
                Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
