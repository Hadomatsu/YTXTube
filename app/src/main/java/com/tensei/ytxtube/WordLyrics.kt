package com.tensei.ytxtube

import android.icu.text.Transliterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/*
 * Word-by-word lyrics and romanization, after BitChord (GPL-3.0, github.com/kushagrasinghx/BitChord):
 *  - The lyrics services themselves live in the bclyrics package (BitChord's providers).
 *  - Google Translate's romanization mode (dt=rm) reads Japanese kanji properly, which Android's
 *    built-in transliterator cannot (it reads them as Chinese).
 */

/** One sung word with the moment it starts and ends. */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

/**
 * Latin-letter readings for lyrics in other scripts. Google's romanization reads Japanese kanji the
 * Japanese way; when it cannot be reached, Android's own transliterator is used (fine for kana,
 * Korean, Cyrillic, Greek, Arabic and more, but it reads kanji as Chinese).
 */
object LyricsRomanizer {
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private val marker = Regex("\\uE000[^\\uE001]*\\uE001")
    private val cache = object : LinkedHashMap<String, List<String?>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String?>>?) = size > 20
    }

    fun needsRomanization(text: String): Boolean = text.codePoints().anyMatch { cp ->
        Character.isLetter(cp) && Character.UnicodeScript.of(cp) != Character.UnicodeScript.LATIN
    }

    /** One reading per line (null where a line is already Latin), or null when nothing needs it. */
    suspend fun romanize(key: String, lines: List<String>): List<String?>? = withContext(Dispatchers.IO) {
        if (lines.none(::needsRomanization)) return@withContext null
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        val wanted = lines.indices.filter { needsRomanization(lines[it]) }
        val readings = arrayOfNulls<String>(lines.size)
        val fromGoogle = runCatching { google(wanted.map { lines[it] }) }.getOrNull()
        wanted.forEachIndexed { n, index ->
            readings[index] = fromGoogle?.getOrNull(n)?.takeIf { it.isNotBlank() } ?: icu(lines[index])
        }
        val result = readings.toList()
        synchronized(cache) { cache[key] = result }
        result
    }

    private fun icu(text: String): String? {
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        return runCatching {
            Transliterator.getInstance("Any-Latin; Latin-ASCII").transliterate(text)
        }.getOrNull()?.takeIf { it != text }
    }

    /** Sends the lines in batches separated by private-use markers, and splits the answer back up. */
    private fun google(texts: List<String>): List<String>? {
        val out = ArrayList<String>(texts.size)
        var start = 0
        while (start < texts.size) {
            var end = start
            var length = 0
            while (end < texts.size && (end == start || length + texts[end].length < 3000)) {
                length += texts[end].length + 12
                end++
            }
            val batch = texts.subList(start, end)
            val payload = buildString {
                batch.forEachIndexed { i, t ->
                    if (i > 0) append('\n').append("").append(i.toString().padStart(4, '0')).append("").append('\n')
                    append(t)
                }
            }
            val body = FormBody.Builder()
                .add("client", "dict-chrome-ex")
                .add("sl", "auto")
                .add("tl", "en")
                .add("dt", "rm")
                .add("q", payload)
                .build()
            val request = Request.Builder().url(ENDPOINT).header("User-Agent", "YTXTube").post(body).build()
            val raw = Net.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return null
                r.body?.string().orEmpty()
            }
            val root = JSONArray(raw)
            val segments = root.optJSONArray(0) ?: return null
            val joined = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONArray(i) ?: continue
                if (seg.length() > 3 && !seg.isNull(3)) joined.append(seg.optString(3))
            }
            val parts = joined.toString().split(marker).map { it.trim() }
            if (parts.size != batch.size) return null
            out += parts
            start = end
        }
        return out
    }
}
