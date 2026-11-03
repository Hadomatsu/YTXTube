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
 *  - BetterLyrics answers with Apple Music's own TTML for a title, artist and duration: one <p> per
 *    sung line, one timed <span> per syllable. That timing is what drives the Apple-style sweep.
 *  - Google Translate's romanization mode (dt=rm) reads Japanese kanji properly, which Android's
 *    built-in transliterator cannot (it reads them as Chinese).
 */

/** One sung word with the moment it starts and ends. */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

object AppleLyrics {
    private const val BETTER_LYRICS = "https://lyrics-api.boidu.dev/getLyrics"

    /** Word-timed lyrics for the song, or null when BetterLyrics does not have it. */
    suspend fun fetch(title: String, artist: String, durationSec: Long): List<LyricLine>? = withContext(Dispatchers.IO) {
        runCatching {
            val url = BETTER_LYRICS.toHttpUrl().newBuilder()
                .addQueryParameter("s", title)
                .addQueryParameter("a", artist)
                .apply { if (durationSec > 0) addQueryParameter("d", durationSec.toString()) }
                .build()
            val request = Request.Builder().url(url).header("User-Agent", "YTXTube").build()
            val body = Net.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                r.body?.string().orEmpty()
            }
            val ttml = unwrap(body) ?: return@runCatching null
            TtmlLyrics.parse(ttml).takeIf { lines -> lines.any { it.text.isNotBlank() } }
        }.getOrNull()
    }

    /** The answer is TTML, sometimes inside one or two JSON envelopes. */
    private fun unwrap(raw: String): String? {
        val value = raw.replace("﻿", "").trim()
        if (value.isEmpty()) return null
        if (value.startsWith("<")) return value.takeIf { it.contains("<tt", ignoreCase = true) }
        val found = runCatching { find(JSONObject(value)) }.getOrNull() ?: return null
        val text = found.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
        return text.takeIf { it.contains("<tt", ignoreCase = true) }
    }

    private fun find(obj: JSONObject): String? {
        for (key in listOf("ttml", "lyrics", "content", "data", "result")) {
            when (val v = obj.opt(key)) {
                is String -> if (v.contains("tt", ignoreCase = true)) {
                    val trimmed = v.trim()
                    if (trimmed.startsWith("{")) runCatching { find(JSONObject(trimmed)) }.getOrNull()?.let { return it }
                    return v
                }
                is JSONObject -> find(v)?.let { return it }
                is JSONArray -> for (i in 0 until v.length()) (v.opt(i) as? JSONObject)?.let { o -> find(o)?.let { return it } }
            }
        }
        return null
    }
}

/** Apple Music's word-timed lyric format. */
object TtmlLyrics {
    private val SKIPPED_ROLES = setOf("x-translation", "x-roman")
    private const val BACKGROUND_ROLE = "x-bg"

    fun parse(ttml: String): List<LyricLine> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Third-party document: never fetch anything it points at.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { isExpandEntityReferences = false }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        val paragraphs = document.getElementsByTagName("p")
        val lines = ArrayList<LyricLine>(paragraphs.length)
        for (i in 0 until paragraphs.length) {
            val p = paragraphs.item(i) as? Element ?: continue
            lineFrom(p)?.let(lines::add)
        }
        lines.sortedBy { it.timeMs }
    }.getOrDefault(emptyList())

    private fun Element.attr(name: String): String {
        getAttribute(name).takeIf { it.isNotEmpty() }?.let { return it }
        val local = name.substringAfter(':')
        val all = attributes ?: return ""
        for (i in 0 until all.length) {
            val a = all.item(i) ?: continue
            if (a.nodeName == name || a.nodeName == local || a.localName == local) return a.nodeValue.orEmpty()
        }
        return ""
    }

    private fun lineFrom(p: Element): LyricLine? {
        val pieces = mutableListOf<Piece>()
        val backing = mutableListOf<Piece>()
        collect(p, pieces, backing)
        val words = words(pieces)
        val backingText = words(backing).joinToString(" ") { it.text }.takeIf { it.isNotBlank() }
        val begin = time(p.getAttribute("begin"))
        val end = time(p.getAttribute("end"))
        if (words.isEmpty()) {
            val text = p.textContent?.trim().orEmpty()
            if (text.isEmpty() || begin == null) return null
            return LyricLine(timeMs = begin, text = text, endMs = end ?: -1L)
        }
        return LyricLine(
            timeMs = minOf(begin ?: words.first().startMs, words.first().startMs),
            text = words.joinToString(" ") { it.text },
            words = words,
            endMs = maxOf(end ?: -1L, words.last().endMs),
            background = backingText,
        )
    }

    private fun collect(node: Node, out: MutableList<Piece>, backing: MutableList<Piece>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            when (val child = children.item(i)) {
                is Element -> {
                    val role = child.attr("ttm:role")
                    if (role in SKIPPED_ROLES) continue
                    val sink = if (role == BACKGROUND_ROLE) backing else out
                    val begin = time(child.getAttribute("begin"))
                    val end = time(child.getAttribute("end"))
                    if (begin != null && end != null && !hasTimedChild(child)) {
                        sink += Piece.Timed(child.textContent.orEmpty(), begin, end)
                    } else {
                        collect(child, sink, backing)
                    }
                }
                else -> if (child.nodeType == Node.TEXT_NODE) {
                    val text = child.textContent.orEmpty()
                    if (text.isNotEmpty()) out += Piece.Text(text)
                }
            }
        }
    }

    private fun hasTimedChild(e: Element): Boolean {
        val children = e.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.getAttribute("begin").isNotEmpty() || hasTimedChild(child)) return true
        }
        return false
    }

    /** Syllables of one word are adjacent spans with no space between them; whitespace ends a word. */
    private fun words(pieces: List<Piece>): List<LyricWord> {
        val out = mutableListOf<LyricWord>()
        val current = StringBuilder()
        var start = 0L
        var end = 0L
        var timed = false
        fun flush() {
            val text = current.toString().trim()
            current.setLength(0)
            if (text.isNotEmpty() && timed) out += LyricWord(start, end, text)
            timed = false
        }
        pieces.forEach { piece ->
            when (piece) {
                is Piece.Text -> when {
                    piece.text.isBlank() -> flush()
                    timed -> current.append(piece.text)
                    else -> Unit
                }
                is Piece.Timed -> {
                    if (piece.text.isBlank()) return@forEach
                    if (piece.text.first().isWhitespace()) flush()
                    if (current.isEmpty()) start = piece.start
                    current.append(piece.text.trim())
                    end = piece.end
                    timed = true
                    if (piece.text.last().isWhitespace()) flush()
                }
            }
        }
        flush()
        return out
    }

    /** `27.395`, `1:05.20`, `1:02:03.4`, `12s` or `340ms`, in milliseconds. */
    fun time(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms")) return raw.dropLast(2).toDoubleOrNull()?.toLong()
        val parts = raw.removeSuffix("s").split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { m -> parts[1].toDoubleOrNull()?.let { m * 60 + it } }
            3 -> parts[0].toDoubleOrNull()?.let { h ->
                parts[1].toDoubleOrNull()?.let { m -> parts[2].toDoubleOrNull()?.let { h * 3600 + m * 60 + it } }
            }
            else -> null
        } ?: return null
        return (seconds * 1000).toLong()
    }

    private sealed interface Piece {
        data class Text(val text: String) : Piece
        data class Timed(val text: String, val start: Long, val end: Long) : Piece
    }
}

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
