package com.tensei.ytxtube

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The colour of a video's thumbnail, for the soft glow under it in the feeds. Worked out from a
 * tiny copy of the same picture the card already loaded (so it comes from Coil's disk cache, not
 * the network), favouring the coloured parts so a mostly dark picture still gives its real colour
 * instead of a muddy grey, and kept per video so scrolling back shows it at once.
 */
object ThumbTint {
    private val cache = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 500
    }

    fun cached(videoId: String): Int? = synchronized(cache) { cache[videoId] }

    private fun put(videoId: String, color: Int) = synchronized(cache) { cache[videoId] = color }

    /** Candidate addresses in the same order the card's thumbnail tries them. */
    fun candidates(url: String?, videoId: String?): List<String> {
        val id = videoId?.takeIf { it.isNotBlank() } ?: url?.let(::ytVideoIdFromThumbUrl)
        return buildList {
            add(if (id != null) sharpenFeedThumbnail(url, id) else url)
            add(url)
            if (id != null) add("https://i.ytimg.com/vi/$id/hqdefault.jpg")
        }.filterNotNull().distinct()
    }

    suspend fun load(context: android.content.Context, videoId: String, url: String?): Int? {
        cached(videoId)?.let { return it }
        return withContext(Dispatchers.IO) {
            for (candidate in candidates(url, videoId)) {
                val color = runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(candidate)
                        .size(24)
                        .allowHardware(false)
                        .build()
                    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                        ?: return@runCatching null
                    average(drawable.toBitmap(12, 7, Bitmap.Config.ARGB_8888))
                }.getOrNull()
                if (color != null) {
                    put(videoId, color)
                    return@withContext color
                }
            }
            null
        }
    }

    /** Average colour, weighted towards saturated mid-tone pixels; black bars barely count. */
    private fun average(bm: Bitmap): Int {
        val hsl = FloatArray(3)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var total = 0.0
        for (y in 0 until bm.height) {
            for (x in 0 until bm.width) {
                val p = bm.getPixel(x, y)
                ColorUtils.colorToHSL(p, hsl)
                val mid = 1f - abs(hsl[2] - 0.5f) * 2f
                val weight = if (hsl[2] < 0.06f || hsl[2] > 0.97f) 0.02 else 0.12 + hsl[1] * mid
                r += android.graphics.Color.red(p) * weight
                g += android.graphics.Color.green(p) * weight
                b += android.graphics.Color.blue(p) * weight
                total += weight
            }
        }
        if (total <= 0.0) return android.graphics.Color.DKGRAY
        return android.graphics.Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
    }

    /** The thumbnail colour made calm enough to sit behind text on this theme's background. */
    fun forBackground(raw: Int, background: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(raw, hsl)
        hsl[1] = (hsl[1] * 1.3f).coerceAtMost(0.9f)
        hsl[2] = if (background.luminance() > 0.5f) 0.86f else hsl[2].coerceIn(0.26f, 0.42f)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

/**
 * Paints the card's info area with the thumbnail's colour at the top, fading smoothly into the
 * page background by [fadeEndPx] (measured from the top of the area; the whole area when it is not
 * known yet), so the card's edge never shows. Until the colour is known it is just the background.
 */
@Composable
fun Modifier.thumbnailGlow(
    videoId: String,
    url: String?,
    strength: Float = 0.62f,
    fadeEndPx: () -> Float = { Float.NaN },
): Modifier {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    val raw by produceState(ThumbTint.cached(videoId), videoId, url) {
        if (value == null) value = ThumbTint.load(context, videoId, url)
    }
    val target = remember(raw, background) {
        raw?.let { lerp(background, ThumbTint.forBackground(it, background), strength) } ?: background
    }
    val top by animateColorAsState(target, tween(420), label = "thumb-glow")
    return drawBehind {
        val end = fadeEndPx().takeIf { !it.isNaN() && it > 0f }?.coerceAtMost(size.height) ?: size.height
        // An eased fade, so there is no visible line where the colour runs out.
        val brush = Brush.verticalGradient(
            0f to top,
            0.3f to lerp(background, top, 0.55f),
            0.6f to lerp(background, top, 0.2f),
            0.85f to lerp(background, top, 0.04f),
            1f to background,
            startY = 0f,
            endY = end,
        )
        drawRect(brush)
    }
}
