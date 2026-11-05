package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** One chapter of a video: where it starts and its title. */
data class Chapter(val startMs: Long, val title: String)

object Chapters {
    private val LINE = Regex("""^\s*[\[(]?((?:\d{1,2}:)?\d{1,2}:\d{2})[\])]?\s*[-–—:|.•]*\s*(\S.*?)\s*$""")

    /**
     * Chapters written in the description, the way YouTube itself reads them: a line per chapter
     * starting with its time, the first at 0:00, at least three, each at least ten seconds long.
     */
    fun fromDescription(description: String, durationMs: Long): List<Chapter> {
        val out = ArrayList<Chapter>()
        description.lineSequence().forEach { line ->
            val m = LINE.find(line) ?: return@forEach
            val ms = Timestamps.parse(m.groupValues[1]) ?: return@forEach
            if (out.isNotEmpty() && ms <= out.last().startMs) return@forEach
            out += Chapter(ms, m.groupValues[2].trim())
        }
        if (out.size < 3 || out.first().startMs != 0L) return emptyList()
        for (i in 1 until out.size) if (out[i].startMs - out[i - 1].startMs < 10_000L) return emptyList()
        if (durationMs > 0 && out.last().startMs >= durationMs) return emptyList()
        return out
    }

    /** Index of the chapter playing at [ms], or -1. */
    fun indexAt(chapters: List<Chapter>, ms: Long): Int = chapters.indexOfLast { it.startMs <= ms }
}

/** Times like 4:32 or 1:02:45 in text, made tappable. */
object Timestamps {
    private val PATTERN = Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?![\d:])""")

    private fun toMs(m: MatchResult): Long? {
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val min = m.groupValues[2].toIntOrNull() ?: return null
        val s = m.groupValues[3].toIntOrNull() ?: return null
        if (s >= 60) return null
        if (m.groupValues[1].isNotEmpty() && min >= 60) return null
        return ((h * 3600L) + min * 60L + s) * 1000L
    }

    fun parse(text: String): Long? = PATTERN.matchEntire(text.trim())?.let(::toMs)

    fun contains(text: String): Boolean = PATTERN.containsMatchIn(text)

    /** The text with every time in it as a link that seeks there. */
    fun annotate(text: String, color: Color, onSeek: (Long) -> Unit): AnnotatedString = buildAnnotatedString {
        var last = 0
        PATTERN.findAll(text).forEach { m ->
            val ms = toMs(m) ?: return@forEach
            append(text.substring(last, m.range.first))
            withLink(
                LinkAnnotation.Clickable(
                    "t$ms",
                    TextLinkStyles(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)),
                ) { onSeek(ms) },
            ) { append(m.value) }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

/** The speed you last used on each channel, picked up again on its next video. */
object ChannelSpeeds {
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("channel_speeds", Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean("__enabled", true)
    }

    fun updateEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("__enabled", on)?.apply()
    }

    private fun key(channel: String?): String? =
        channel?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { "c:$it" }

    fun get(channel: String?): Float? {
        if (!enabled) return null
        val k = key(channel) ?: return null
        return prefs?.getFloat(k, -1f)?.takeIf { it > 0f }
    }

    fun save(channel: String?, speed: Float) {
        if (!enabled) return
        val k = key(channel) ?: return
        val edit = prefs?.edit() ?: return
        if (abs(speed - PlaybackPrefs.defaultSpeed) < 0.01f) edit.remove(k) else edit.putFloat(k, speed)
        edit.apply()
    }
}

/** Whether the music player is the one playing now (the service cannot see the screen state). */
object NowPlayingMode {
    @Volatile var music = false
}

/** Watching without leaving a trace: no history, no resume points, no search history. */
object Incognito {
    var enabled by mutableStateOf(false)
        private set

    fun set(on: Boolean) { enabled = on }
}

/** What the Home and Subscriptions feeds leave out, and channels and words blocked everywhere. */
object FeedFilters {
    data class BlockedChannel(val id: String?, val name: String)

    private var prefs: SharedPreferences? = null

    var hideShorts by mutableStateOf(true)
        private set
    var watchedMarks by mutableStateOf(true)
        private set
    var blockedChannels by mutableStateOf<List<BlockedChannel>>(emptyList())
        private set
    var keywords by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("feed_filters", Context.MODE_PRIVATE).also { prefs = it }
        hideShorts = p.getBoolean("hide_shorts", true)
        watchedMarks = p.getBoolean("watched_marks", true)
        blockedChannels = runCatching {
            val arr = JSONArray(p.getString("channels", "[]"))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                BlockedChannel(o.optString("id").ifBlank { null }, o.optString("name"))
            }
        }.getOrDefault(emptyList())
        keywords = runCatching {
            val arr = JSONArray(p.getString("keywords", "[]"))
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        val ch = JSONArray()
        blockedChannels.forEach { ch.put(JSONObject().put("id", it.id ?: "").put("name", it.name)) }
        prefs?.edit()
            ?.putString("channels", ch.toString())
            ?.putString("keywords", JSONArray(keywords).toString())
            ?.apply()
    }

    fun updateHideShorts(on: Boolean) { hideShorts = on; prefs?.edit()?.putBoolean("hide_shorts", on)?.apply() }
    fun updateWatchedMarks(on: Boolean) { watchedMarks = on; prefs?.edit()?.putBoolean("watched_marks", on)?.apply() }

    fun block(video: VideoItem) {
        if (video.channel.isBlank() && video.channelId() == null) return
        if (isChannelBlocked(video)) return
        blockedChannels = blockedChannels + BlockedChannel(video.channelId(), video.channel.trim())
        persist()
    }

    fun unblock(channel: BlockedChannel) {
        blockedChannels = blockedChannels - channel
        persist()
    }

    fun addKeyword(word: String) {
        val k = word.trim().lowercase()
        if (k.isEmpty() || k in keywords) return
        keywords = keywords + k
        persist()
    }

    fun removeKeyword(word: String) {
        keywords = keywords - word
        persist()
    }

    private fun isChannelBlocked(v: VideoItem): Boolean {
        val id = v.channelId()
        val name = v.channel.trim()
        return blockedChannels.any { (it.id != null && it.id == id) || (name.isNotEmpty() && it.name.equals(name, ignoreCase = true)) }
    }

    fun isBlocked(v: VideoItem): Boolean {
        if (blockedChannels.isEmpty() && keywords.isEmpty()) return false
        if (isChannelBlocked(v)) return true
        if (keywords.isEmpty()) return false
        val title = v.title.lowercase()
        val channel = v.channel.lowercase()
        return keywords.any { title.contains(it) || channel.contains(it) }
    }

    fun isShort(v: VideoItem): Boolean = v.isShort || v.durationSec in 1..60

    /** Home and Subscriptions: no Shorts (when chosen) and nothing blocked. */
    fun forFeed(videos: List<VideoItem>): List<VideoItem> =
        videos.filter { !(hideShorts && isShort(it)) && !isBlocked(it) }

    /** Search and More videos: only the block list applies. */
    fun withoutBlocked(videos: List<VideoItem>): List<VideoItem> =
        if (blockedChannels.isEmpty() && keywords.isEmpty()) videos else videos.filterNot(::isBlocked)
}

/** One-tap translation of comments in another language. */
object CommentTranslator {
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"

    private val ENGLISH = setOf(
        "the", "a", "an", "and", "or", "is", "are", "was", "were", "i", "you", "he", "she", "it", "we",
        "they", "this", "that", "to", "of", "in", "on", "for", "with", "my", "your", "be", "have", "has",
        "not", "no", "but", "so", "just", "what", "like", "im", "i'm", "dont", "don't", "can", "do", "if",
        "at", "me", "all", "love", "good", "video", "lol", "its", "it's", "bro", "how", "why", "when",
        "one", "more", "from", "get", "will", "would", "there", "his", "her", "our", "out", "up", "about",
        "really", "great", "best", "first", "know", "think", "make", "as", "by", "who", "been", "only",
        "too", "than", "then", "now", "even", "much", "very", "also", "here", "see", "game", "guy", "guys",
    )

    /** A guess at whether a comment is in another language, so Translate only shows where it helps. */
    fun looksForeign(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.length < 6) return false
        val nonLatin = letters.count { Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN }
        if (nonLatin * 3 > letters.length) return true
        val words = text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
        if (words.size < 4) return false
        val english = words.count { it in ENGLISH }
        return english * 10 < words.size
    }

    suspend fun translate(text: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = runCatching { LanguageSettings.effectiveLanguageCode() }.getOrNull()
                ?.substringBefore('-')?.ifBlank { null } ?: "en"
            val body = FormBody.Builder()
                .add("client", "gtx")
                .add("sl", "auto")
                .add("tl", target)
                .add("dt", "t")
                .add("q", text)
                .build()
            val request = Request.Builder().url(ENDPOINT).header("User-Agent", Net.USER_AGENT).post(body).build()
            val raw = Net.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                r.body?.string().orEmpty()
            }
            val segments = JSONArray(raw).optJSONArray(0) ?: return@runCatching null
            buildString {
                for (i in 0 until segments.length()) {
                    segments.optJSONArray(i)?.optString(0)?.let { append(it) }
                }
            }.trim().ifBlank { null }
        }.getOrNull()
    }
}


/** Opens a playlist page (search results, links). */
val LocalPlaylistOpener = staticCompositionLocalOf<(PlaylistRef) -> Unit> { {} }

enum class SearchType(val label: String, val code: Int) { VIDEOS("Videos", 1), CHANNELS("Channels", 2), PLAYLISTS("Playlists", 3) }
enum class SearchDate(val label: String, val code: Int) {
    ANY("Any time", 0), HOUR("Last hour", 1), TODAY("Today", 2), WEEK("This week", 3), MONTH("This month", 4), YEAR("This year", 5),
}
enum class SearchLength(val label: String, val code: Int) {
    ANY("Any length", 0), SHORT("Under 4 minutes", 1), MEDIUM("4 to 20 minutes", 3), LONG("Over 20 minutes", 2),
}
enum class SearchSort(val label: String, val code: Int) {
    RELEVANCE("Relevance", 0), DATE("Upload date", 2), VIEWS("View count", 3), RATING("Rating", 1),
}

/** Search filters, sent to YouTube the way its own search page encodes them. */
data class SearchFilter(
    val type: SearchType = SearchType.VIDEOS,
    val date: SearchDate = SearchDate.ANY,
    val length: SearchLength = SearchLength.ANY,
    val sort: SearchSort = SearchSort.RELEVANCE,
) {
    val isDefault: Boolean get() = this == SearchFilter()

    fun params(): String {
        val inner = ArrayList<Int>()
        if (type == SearchType.VIDEOS && date.code != 0) { inner += 0x08; inner += date.code }
        inner += 0x10; inner += type.code
        if (type == SearchType.VIDEOS && length.code != 0) { inner += 0x18; inner += length.code }
        val bytes = ArrayList<Int>()
        if (sort.code != 0) { bytes += 0x08; bytes += sort.code }
        bytes += 0x12; bytes += inner.size
        bytes.addAll(inner)
        val raw = bytes.map { it.toByte() }.toByteArray()
        val b64 = android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
        return java.net.URLEncoder.encode(b64, "UTF-8")
    }
}

data class SearchChannel(val id: String, val name: String, val avatar: String?, val detail: String?)

data class SearchResults(
    val videos: List<VideoItem>,
    val channels: List<SearchChannel>,
    val playlists: List<PlaylistRef>,
    val next: String?,
)
