package com.tensei.ytxtube

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A picture of a few lyric lines over the song's blurred cover, with the cover, title and artist,
 * shared like any photo (Instagram story, WhatsApp, ...).
 */
object LyricsCard {
    private const val W = 1080
    private const val H = 1350

    suspend fun share(context: Context, item: VideoItem, lines: List<String>) {
        if (lines.isEmpty()) return
        val cover = loadCover(context, item)
        val picture = withContext(Dispatchers.Default) { render(item, lines, cover) }
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            File(dir, "lyrics-${item.id}.png").also { f -> f.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share lyrics"))
    }

    private suspend fun loadCover(context: Context, item: VideoItem): Bitmap? = withContext(Dispatchers.IO) {
        val url = highResMusicThumbnail(item.thumbnail) ?: item.thumbnail ?: return@withContext null
        runCatching {
            val request = ImageRequest.Builder(context).data(url).size(900).allowHardware(false).build()
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
    }

    private fun render(item: VideoItem, lines: List<String>, cover: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Background: the cover, heavily blurred (shrunk, then stretched back), and darkened.
        if (cover != null) {
            val tiny = Bitmap.createScaledBitmap(cover, 18, 22, true)
            val wide = Bitmap.createScaledBitmap(tiny, W, H, true)
            canvas.drawBitmap(wide, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawColor(0xFF1E1E24.toInt())
        }
        canvas.drawColor(0x73000000)

        val margin = 96f
        // Cover with rounded corners, and the song next to it.
        val coverSize = 170f
        val top = 120f
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val square = Bitmap.createBitmap(cover, (cover.width - side) / 2, (cover.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, coverSize.toInt(), coverSize.toInt(), true)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            canvas.save()
            canvas.translate(margin, top)
            canvas.drawRoundRect(RectF(0f, 0f, coverSize, coverSize), 26f, 26f, paint)
            canvas.restore()
        }
        val textLeft = margin + (if (cover != null) coverSize + 36f else 0f)
        val textWidth = (W - margin - textLeft).toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 38f
        }
        val title = android.text.TextUtils.ellipsize(item.title, titlePaint, textWidth.toFloat(), android.text.TextUtils.TruncateAt.END).toString()
        val artist = android.text.TextUtils.ellipsize(item.channel, artistPaint, textWidth.toFloat(), android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, textLeft, top + 78f, titlePaint)
        canvas.drawText(artist, textLeft, top + 132f, artistPaint)

        // The lines, big and bold, shrunk until they fit the space below.
        val text = lines.joinToString("\n")
        val area = RectF(margin, top + coverSize + 90f, W - margin, H - 150f)
        var size = 84f
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        fun build(textSize: Float): StaticLayout {
            lyricPaint.textSize = textSize
            return StaticLayout.Builder.obtain(text, 0, text.length, lyricPaint, area.width().toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.12f)
                .build()
        }
        var layout = build(size)
        while (layout.height > area.height() && size > 40f) {
            size -= 4f
            layout = build(size)
        }
        canvas.save()
        canvas.translate(area.left, area.top + (area.height() - layout.height).coerceAtLeast(0f) / 2f)
        layout.draw(canvas)
        canvas.restore()

        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80FFFFFF.toInt()
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("♪  " + item.channel.ifBlank { item.title }, margin, H - 80f, footer)
        return out
    }
}
