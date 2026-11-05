package com.tensei.ytxtube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Lyrics as a page of their own, the way Apple Music shows them: the cover slowly drifting behind
 * everything, the song's small cover and name at the top, the words filling the page, and the
 * playback controls in a liquid glass capsule at the bottom.
 */
@Composable
internal fun AppleLyricsScreen(
    state: PlayerState,
    item: VideoItem,
    lyrics: LyricsDoc?,
    loading: Boolean,
    romanized: List<String?>?,
    canRomanize: Boolean,
    romanizeOn: Boolean,
    onToggleRomanize: () -> Unit,
    onClose: () -> Unit,
    onQueue: () -> Unit,
    top: Dp,
    bottom: Dp,
) {
    // Lyric card: null when not choosing lines, else the chosen line numbers.
    var selection by remember(item.id) { mutableStateOf<Set<Int>?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val cardScope = rememberCoroutineScope()
    BackHandler(onBack = { if (selection != null) selection = null else onClose() })
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        val before = view.keepScreenOn
        if (PlaybackPrefs.keepScreenOnLyrics) view.keepScreenOn = true
        onDispose { view.keepScreenOn = before }
    }
    val backdrop = rememberLayerBackdrop()
    val art = highResMusicThumbnail(item.thumbnail) ?: sharpenFeedThumbnail(null, item.id)
    val drift = rememberInfiniteTransition(label = "lyrics-drift")
    val angle by drift.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing)),
        label = "lyrics-angle",
    )
    val breathe by drift.animateFloat(
        initialValue = 1.45f,
        targetValue = 1.75f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "lyrics-scale",
    )

    Box(Modifier.fillMaxSize()) {
        // The moving, blurred cover: Apple's living background, and what the glass bends.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(Color.Black),
        ) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = angle
                        scaleX = breathe
                        scaleY = breathe
                    }
                    .blur(80.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.5f)),
                        ),
                    ),
            )
        }

        // The words, filling the page and fading out under the header and the controls.
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = top + 76.dp, bottom = bottom + 150.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.07f to Color.Black,
                            0.88f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .widthIn(max = 732.dp)
                    .fillMaxSize(),
            ) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    }
                    lyrics == null || lyrics.lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No lyrics found for this song.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    lyrics.synced -> AppleSyncedLyrics(
                        lyrics = lyrics,
                        positionMs = state.positionMs,
                        playing = state.isPlaying,
                        speed = state.speed,
                        romanized = romanized,
                        onSeek = { state.seekTo(it) },
                        contentColor = Color.White,
                        alignTo = 0.3f,
                        horizontalPadding = 26.dp,
                        selected = selection.orEmpty(),
                        selecting = selection != null,
                        onLongPressLine = { i ->
                            Haptics.long(view)
                            selection = setOf(i)
                        },
                        onToggleLine = { i ->
                            val now = selection ?: emptySet()
                            selection = if (i in now) now - i else if (now.size < 6) now + i else now
                        },
                    )
                    else -> Box(Modifier.padding(horizontal = 26.dp)) {
                        PlainLyricsView(lyrics, romanized, contentColor = Color.White)
                    }
                }
            }
        }

        // Small cover, title and artist at the top.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = top + 10.dp)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = Color.White,
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.channel,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                lyrics?.source?.takeIf { it.isNotBlank() }?.let {
                    Text("Lyrics: $it", color = Color.White.copy(alpha = 0.4f), fontSize = 10.5.sp, maxLines = 1)
                }
            }
            if (canRomanize) {
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .liquidGlass(backdrop, glassSpec(shape = CircleShape, tint = Color.White.copy(alpha = if (romanizeOn) 0.32f else 0.1f), blur = 10.dp, refractionHeight = 6.dp, refractionAmount = 10.dp))
                        .pressable(onClick = onToggleRomanize)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("ABC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .liquidGlass(backdrop, glassSpec(shape = CircleShape, tint = Color.White.copy(alpha = 0.12f), blur = 10.dp, refractionHeight = 6.dp, refractionAmount = 10.dp))
                    .pressable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close lyrics", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Seek bar and controls in a glass capsule at the bottom (or, while choosing lines for a
        // lyric card, Cancel and Share).
        val capsule = RoundedCornerShape(34.dp)
        val chosen = selection
        if (chosen != null && lyrics != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottom + 14.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(capsule)
                    .liquidGlass(backdrop, glassSpec(shape = capsule, tint = Color.Black.copy(alpha = 0.18f)))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Cancel",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pressable { selection = null }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Text(
                    if (chosen.isEmpty()) "Tap lines to pick them" else "${chosen.size} line" + (if (chosen.size == 1) "" else "s") + " (up to 6)",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Share",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .pressable(enabled = chosen.isNotEmpty()) {
                            val lines = chosen.sorted().mapNotNull { lyrics.lines.getOrNull(it)?.text?.takeIf { t -> t.isNotBlank() } }
                            selection = null
                            cardScope.launch { runCatching { LyricsCard.share(context, item, lines) } }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        } else Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom + 14.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(capsule)
                .liquidGlass(backdrop, glassSpec(shape = capsule, tint = Color.Black.copy(alpha = 0.18f)))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            MusicSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { state.seekTo(it) },
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassControl(Icons.Rounded.Lyrics, "Back to the player", 24.dp, Color.White, onClose)
                GlassControl(Icons.Rounded.SkipPrevious, "Previous", 38.dp, Color.White, state::skipPrevious)
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .pressable(pressedScale = 0.92f, onClick = state::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                GlassControl(Icons.Rounded.SkipNext, "Next", 38.dp, Color.White, state::skipNext)
                GlassControl(Icons.Rounded.QueueMusic, "Up next", 24.dp, Color.White, onQueue)
            }
        }
    }
}

@Composable
private fun GlassControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    iconSize: Dp,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}
