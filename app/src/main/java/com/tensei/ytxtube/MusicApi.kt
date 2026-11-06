package com.tensei.ytxtube

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/*
 * YouTube Music, asked the way music.youtube.com itself asks (the WEB_REMIX client), with the
 * signed-in account when there is one. Everything the Music tab shows comes from here: the home
 * shelves and mood chips, Explore, the library, full playlists and albums (every page, not just
 * the first), artist pages, search with its filters, and the radio that keeps the queue going.
 */

enum class MusicKind { Song, Video, Album, Playlist, Artist, Podcast, Episode, Nav }

@Immutable
data class MusicRef(val name: String, val browseId: String?)

@Immutable
data class MusicItem(
    val kind: MusicKind,
    val title: String,
    val subtitle: String = "",
    val thumbnail: String? = null,
    val videoId: String? = null,
    /** Album (MPREb…), playlist (VL…), artist (UC…), podcast, or a page to open (moods, charts). */
    val browseId: String? = null,
    val browseParams: String? = null,
    /** What the play button plays: the album's or playlist's own list, or a mix. */
    val playlistId: String? = null,
    val watchParams: String? = null,
    val artists: List<MusicRef> = emptyList(),
    val album: MusicRef? = null,
    val durationSec: Long = 0,
    val explicit: Boolean = false,
    /** A 16:9 picture (music videos) rather than a square cover. */
    val wide: Boolean = false,
    /** Mood and genre buttons carry a colour stripe. */
    val color: Long? = null,
) {
    val key: String get() = videoId ?: browseId?.let { it + (browseParams ?: "") } ?: playlistId ?: "t:$title"
    val playable: Boolean get() = videoId != null && (kind == MusicKind.Song || kind == MusicKind.Video || kind == MusicKind.Episode)
    val opensPage: Boolean get() = browseId != null && !playable

    fun artistLine(): String =
        artists.joinToString(", ") { it.name }.ifBlank { subtitle.split(" • ").firstOrNull().orEmpty() }

    fun toVideoItem(): VideoItem? {
        val id = videoId ?: return null
        val artistId = artists.firstOrNull { it.browseId?.startsWith("UC") == true }?.browseId
        return VideoItem(
            id = id,
            title = title,
            channel = artistLine(),
            channelUrl = artistId?.let { "https://www.youtube.com/channel/$it" },
            thumbnail = musicArt(thumbnail, 544) ?: sharpenFeedThumbnail(null, id),
            avatar = null,
            durationSec = durationSec,
            views = -1L,
            uploaded = album?.name ?: "YouTube Music",
        )
    }
}

enum class MusicLayout { Carousel, QuickPicks, List, Grid, TopResult, Description }

@Immutable
data class MusicSection(
    val title: String,
    val items: List<MusicItem>,
    val layout: MusicLayout,
    val strapline: String? = null,
    val thumbnail: String? = null,
    /** "More" / "See all": a page to open. */
    val more: MusicItem? = null,
    /** Search shelves: the filter that shows all of them. */
    val searchParams: String? = null,
    val description: String? = null,
)

@Immutable
data class MusicChip(val title: String, val browseId: String?, val params: String?, val selected: Boolean = false)

@Immutable
data class MusicPage(
    val title: String = "",
    val sections: List<MusicSection> = emptyList(),
    val chips: List<MusicChip> = emptyList(),
    val continuation: String? = null,
)

/** What a continuation brought: more shelves (home, explore) or more items for the last shelf. */
@Immutable
data class MusicMore(val sections: List<MusicSection>, val items: List<MusicItem>, val continuation: String?)

@Immutable
data class MusicCollection(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val secondSubtitle: String,
    val description: String?,
    val thumbnail: String?,
    val author: MusicRef?,
    val isAlbum: Boolean,
    val playlistId: String?,
    val tracks: List<MusicItem>,
    val continuation: String?,
    val related: List<MusicSection>,
)

@Immutable
data class MusicArtist(
    val browseId: String,
    val name: String,
    val description: String?,
    val thumbnail: String?,
    val subscribers: String?,
    val subscribed: Boolean,
    val channelId: String?,
    val shuffle: MusicItem?,
    val radio: MusicItem?,
    val sections: List<MusicSection>,
)

@Immutable
data class MusicQueuePage(val items: List<MusicItem>, val continuation: String?, val automix: MusicItem?)

@Immutable
data class MusicSuggestions(val queries: List<String>, val items: List<MusicItem>)

/** A YouTube Music picture at a given size; covers come in any size YouTube is asked for. */
fun musicArt(url: String?, size: Int = 544): String? {
    if (url.isNullOrBlank()) return null
    val u = if (url.startsWith("//")) "https:$url" else url
    if (u.contains("googleusercontent.com") || u.contains("ggpht.com")) {
        val sized = u.replace(Regex("=w\\d+-h\\d+[^&?/]*$"), "=w$size-h$size-l90-rj")
            .replace(Regex("=s\\d+[^&?/]*$"), "=s$size")
        return sized
    }
    return u
}

/** Where more songs come from once Up next runs low: the rest of a playlist, or a radio. */
class MusicFeeder(val isRadio: Boolean, private val load: suspend () -> List<VideoItem>) {
    suspend operator fun invoke(): List<VideoItem> = load()
}

object MusicFeeds {
    /** YouTube Music's radio for a song (or a playlist / artist radio when [playlistId] is given). */
    fun radio(videoId: String?, playlistId: String? = null, params: String? = null): MusicFeeder {
        val list = playlistId ?: videoId?.let { "RDAMVM$it" }
        val p = params ?: if (playlistId == null) "wAEB" else null
        var token: String? = null
        var started = false
        var done = list == null
        return MusicFeeder(isRadio = true) {
            if (done) emptyList() else {
                val page = if (!started) YTMusic.queue(videoId, list, p)
                else YTMusic.queueMore(videoId, list, p, token ?: "")
                started = true
                token = page.continuation
                if (token == null) done = true
                page.items.mapNotNull { it.toVideoItem() }
            }
        }
    }

    /** The pages of a playlist or album after the ones already shown. */
    fun collection(browseId: String, continuation: String?): MusicFeeder {
        var token = continuation
        return MusicFeeder(isRadio = false) {
            val t = token
            if (t == null) emptyList() else {
                val more = YTMusic.collectionMore(browseId, t)
                token = more.continuation
                more.items.mapNotNull { it.toVideoItem() }
            }
        }
    }
}

object YTMusic {
    private const val BASE = "https://music.youtube.com/youtubei/v1/"
    private const val ORIGIN = "https://music.youtube.com"
    private val JSON_TYPE = "application/json".toMediaType()

    // The search filters, for when YouTube does not send its own filter chips.
    const val FILTER_SONGS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    const val FILTER_VIDEOS = "EgWKAQIQAWoMEA4QChADEAQQCRAF"
    const val FILTER_ALBUMS = "EgWKAQIYAWoMEA4QChADEAQQCRAF"
    const val FILTER_ARTISTS = "EgWKAQIgAWoMEA4QChADEAQQCRAF"
    const val FILTER_PLAYLISTS = "EgWKAQIoAWoMEA4QChADEAQQCRAF"

    @Volatile private var version: String? = null

    /** The client version YouTube Music expects: the web app's own build, a day or two old. */
    private fun clientVersion(): String {
        version?.let { return it }
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.add(java.util.Calendar.DAY_OF_YEAR, -2)
        val v = String.format(
            java.util.Locale.US, "1.%04d%02d%02d.01.00",
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
        version = v
        return v
    }

    internal fun post(endpoint: String, body: JSONObject, query: String = "", signedIn: Boolean = Account.signedIn): JSONObject {
        val auth = if (signedIn) Account.authorization(ORIGIN) else null
        val hl = LanguageSettings.effectiveLanguageCode()
        val gl = LanguageSettings.effectiveCountryCode()
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", clientVersion())
            .put("hl", hl)
            .put("gl", gl)
        Account.visitorData()?.let { client.put("visitorData", it) }
        val payload = JSONObject(body.toString()).put(
            "context",
            JSONObject().put("client", client).put("user", JSONObject().put("lockedSafetyMode", false)),
        )
        val cookie = if (auth != null) Account.cookieHeader() else "PREF=hl=$hl&gl=$gl"
        val request = Request.Builder()
            .url("$BASE$endpoint?prettyPrint=false$query")
            .post(payload.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", Net.USER_AGENT)
            .header("Accept-Language", "$hl-$gl,$hl;q=0.9,en;q=0.8")
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", clientVersion())
            .header("X-Goog-AuthUser", "0")
            .header("Cookie", cookie)
            .apply {
                Account.visitorData()?.let { header("X-Goog-Visitor-Id", it) }
                if (auth != null) header("Authorization", auth)
            }
            .build()
        var code = 0
        var text = ""
        Net.client.newCall(request).execute().use { r ->
            if (auth != null) Account.absorbSetCookies(r.headers("Set-Cookie"))
            code = r.code
            text = r.body?.string().orEmpty()
        }
        // A session YouTube Music refuses still gets the public pages.
        if ((code == 401 || code == 403) && auth != null) return post(endpoint, body, query, signedIn = false)
        if (code !in 200..299) throw IOException("YouTube Music returned $code")
        return JSONObject(text)
    }

    /** A continuation, asked for the way that kind of token wants (URL or body), then the other way. */
    private fun <T> postCont(
        endpoint: String,
        base: JSONObject,
        token: String,
        parse: (JSONObject) -> T,
        empty: (T) -> Boolean,
    ): T {
        val raw = token.substring(2)
        val enc = java.net.URLEncoder.encode(raw, "UTF-8")
        val legacy = token.startsWith("L:")
        val first = runCatching {
            if (legacy) post(endpoint, base, "&ctoken=$enc&continuation=$enc&type=next")
            else post(endpoint, JSONObject().put("continuation", raw))
        }.mapCatching(parse)
        first.getOrNull()?.let { if (!empty(it)) return it }
        val second = runCatching {
            if (legacy) post(endpoint, JSONObject().put("continuation", raw))
            else post(endpoint, base, "&ctoken=$enc&continuation=$enc&type=next")
        }.mapCatching(parse)
        second.getOrNull()?.let { return it }
        return first.getOrThrow()
    }

    // ---------------------------------------------------------------- pages

    suspend fun home(params: String? = null): MusicPage = withContext(Dispatchers.IO) {
        val body = JSONObject().put("browseId", "FEmusic_home")
        if (params != null) body.put("params", params)
        parsePage(post("browse", body))
    }

    suspend fun browse(browseId: String, params: String? = null): MusicPage = withContext(Dispatchers.IO) {
        val body = JSONObject().put("browseId", browseId)
        if (params != null) body.put("params", params)
        parsePage(post("browse", body))
    }

    suspend fun browseMore(browseId: String, params: String?, token: String): MusicMore = withContext(Dispatchers.IO) {
        val body = JSONObject().put("browseId", browseId)
        if (params != null) body.put("params", params)
        postCont("browse", body, token, ::parseMore) { it.sections.isEmpty() && it.items.isEmpty() }
    }

    suspend fun collection(browseId: String): MusicCollection = withContext(Dispatchers.IO) {
        val id = if (browseId.startsWith("PL") || browseId.startsWith("RD") || browseId.startsWith("OLAK")) "VL$browseId" else browseId
        parseCollection(post("browse", JSONObject().put("browseId", id)), id)
    }

    suspend fun collectionMore(browseId: String, token: String): MusicMore = withContext(Dispatchers.IO) {
        postCont("browse", JSONObject().put("browseId", browseId), token, ::parseMore) { it.items.isEmpty() }
    }

    suspend fun artist(browseId: String): MusicArtist = withContext(Dispatchers.IO) {
        parseArtist(post("browse", JSONObject().put("browseId", browseId)), browseId)
    }

    suspend fun search(query: String, params: String? = null): MusicPage = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query)
        if (params != null) body.put("params", params)
        parseSearch(post("search", body))
    }

    suspend fun searchMore(query: String, params: String?, token: String): MusicMore = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query)
        if (params != null) body.put("params", params)
        postCont("search", body, token, ::parseMore) { it.items.isEmpty() }
    }

    suspend fun suggestions(input: String): MusicSuggestions = withContext(Dispatchers.IO) {
        val root = post("music/get_search_suggestions", JSONObject().put("input", input))
        val queries = LinkedHashSet<String>()
        val items = LinkedHashMap<String, MusicItem>()
        root.walk { key, obj ->
            when (key) {
                "searchSuggestionRenderer" -> {
                    val q = obj.optJSONObject("navigationEndpoint")?.optJSONObject("searchEndpoint")?.optString("query")
                        ?.ifBlank { null } ?: runsText(obj.runs("suggestion"))
                    if (q.isNotBlank()) queries.add(q)
                }
                "musicResponsiveListItemRenderer" -> parseResponsive(obj)?.let { items.putIfAbsent(it.key, it) }
            }
        }
        MusicSuggestions(queries.toList().take(8), items.values.toList().take(4))
    }

    /** The queue YouTube Music plays: a song's radio, a playlist, or a mix. */
    suspend fun queue(videoId: String?, playlistId: String?, params: String?): MusicQueuePage = withContext(Dispatchers.IO) {
        parseQueue(post("next", queueBody(videoId, playlistId, params)))
    }

    suspend fun queueMore(videoId: String?, playlistId: String?, params: String?, token: String): MusicQueuePage =
        withContext(Dispatchers.IO) {
            if (token.isEmpty()) return@withContext MusicQueuePage(emptyList(), null, null)
            postCont("next", queueBody(videoId, playlistId, params), token, ::parseQueue) { it.items.isEmpty() }
        }

    private fun queueBody(videoId: String?, playlistId: String?, params: String?) = JSONObject().apply {
        if (videoId != null) put("videoId", videoId)
        if (playlistId != null) put("playlistId", playlistId)
        if (params != null) put("params", params)
        put("isAudioOnly", true)
        put("enablePersistentPlaylistPanel", true)
        put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
    }

    // ---------------------------------------------------------------- library (signed in)

    suspend fun libraryPlaylists(): MusicPage = browse("FEmusic_liked_playlists")
    suspend fun libraryAlbums(): MusicPage = browse("FEmusic_liked_albums")
    suspend fun libraryArtists(): MusicPage = withContext(Dispatchers.IO) {
        val subs = runCatching { browse("FEmusic_library_corpus_artists") }.getOrNull()
        if (subs != null && subs.sections.any { it.items.isNotEmpty() }) subs else browse("FEmusic_library_corpus_track_artists")
    }
    suspend fun history(): MusicPage = browse("FEmusic_history")

    /** Saves (or removes) an album or playlist in the library. */
    suspend fun savePlaylist(playlistId: String, save: Boolean) = withContext(Dispatchers.IO) {
        post(if (save) "like/like" else "like/removelike", JSONObject().put("target", JSONObject().put("playlistId", playlistId)))
        Unit
    }

    // ---------------------------------------------------------------- parsing

    private fun JSONObject.runs(key: String): JSONArray? = optJSONObject(key)?.optJSONArray("runs")

    private fun runsText(runs: JSONArray?): String {
        if (runs == null) return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString()
    }

    private fun textOf(obj: JSONObject?, key: String): String {
        val o = obj?.optJSONObject(key) ?: return obj?.optString(key).orEmpty()
        o.optString("simpleText").ifBlank { null }?.let { return it }
        return runsText(o.optJSONArray("runs"))
    }

    /** The largest picture under this object. */
    private fun bestThumb(obj: JSONObject?): String? {
        if (obj == null) return null
        var found: JSONArray? = null
        fun look(o: JSONObject) {
            if (found != null) return
            o.optJSONArray("thumbnails")?.let { if (it.length() > 0) { found = it; return } }
            val keys = o.keys()
            while (keys.hasNext() && found == null) {
                when (val v = o.opt(keys.next())) {
                    is JSONObject -> look(v)
                    is JSONArray -> for (i in 0 until v.length()) { (v.opt(i) as? JSONObject)?.let(::look); if (found != null) break }
                }
            }
        }
        look(obj)
        val arr = found ?: return null
        var best: String? = null
        var bestW = -1
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val url = t.optString("url").ifBlank { null } ?: continue
            val w = t.optInt("width", 0)
            if (w >= bestW) { bestW = w; best = url }
        }
        return best?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    /** The first object under [key], depth first. */
    private fun find(root: JSONObject?, key: String): JSONObject? {
        if (root == null) return null
        root.optJSONObject(key)?.let { return it }
        val keys = root.keys()
        while (keys.hasNext()) {
            when (val v = root.opt(keys.next())) {
                is JSONObject -> find(v, key)?.let { return it }
                is JSONArray -> for (i in 0 until v.length()) {
                    (v.opt(i) as? JSONObject)?.let { o -> find(o, key)?.let { return it } }
                }
            }
        }
        return null
    }

    private fun browseOf(ep: JSONObject?): JSONObject? = ep?.optJSONObject("browseEndpoint")
    private fun watchOf(ep: JSONObject?): JSONObject? = ep?.optJSONObject("watchEndpoint")
    private fun pageTypeOf(browse: JSONObject?): String = browse?.optJSONObject("browseEndpointContextSupportedConfigs")
        ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
    private fun videoTypeOf(watch: JSONObject?): String = watch?.optJSONObject("watchEndpointMusicSupportedConfigs")
        ?.optJSONObject("watchEndpointMusicConfig")?.optString("musicVideoType").orEmpty()

    private val DURATION = Regex("^\\d{1,2}(:\\d{2}){1,2}$")

    private fun parseDuration(text: String): Long {
        val t = text.trim()
        if (!DURATION.matches(t)) return 0
        return t.split(":").fold(0L) { acc, part -> acc * 60 + (part.toLongOrNull() ?: 0) }
    }

    /** "1.2M views", "12 songs", "3 vues": numbers with a word, never an artist name. */
    private val COUNT = Regex("\\d[\\d.,]*\\s?[KMBkmb]?\\s+\\p{L}{3,}")

    private val TYPE_LABELS = setOf(
        "song", "video", "titre", "vidéo", "nummer", "titel", "canción", "vídeo", "música", "brano",
    )

    private class Byline(
        val artists: List<MusicRef>,
        val album: MusicRef?,
        val durationSec: Long,
        val text: String,
    )

    /** Splits "Artist • Album • 3:45" style runs into who, which album, how long, and what to show. */
    private fun byline(runs: JSONArray?): Byline {
        if (runs == null || runs.length() == 0) return Byline(emptyList(), null, 0, "")
        val groups = ArrayList<ArrayList<JSONObject>>()
        var current = ArrayList<JSONObject>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            if (run.optString("text").trim() == "•") {
                if (current.isNotEmpty()) groups.add(current)
                current = ArrayList()
            } else current.add(run)
        }
        if (current.isNotEmpty()) groups.add(current)
        val artists = ArrayList<MusicRef>()
        var album: MusicRef? = null
        var duration = 0L
        val shown = ArrayList<String>()
        var looseArtists: List<MusicRef>? = null
        groups.forEachIndexed { index, group ->
            val text = group.joinToString("") { it.optString("text") }.trim()
            if (text.isEmpty()) return@forEachIndexed
            val d = parseDuration(text)
            if (d > 0) { duration = d; return@forEachIndexed }
            shown.add(text)
            var linked = false
            group.forEach { run ->
                val browse = browseOf(run.optJSONObject("navigationEndpoint"))
                val id = browse?.optString("browseId")?.ifBlank { null }
                val type = pageTypeOf(browse)
                val name = run.optString("text").trim()
                when {
                    id == null -> Unit
                    id.startsWith("MPREb") || type.contains("ALBUM") -> { album = MusicRef(name, id); linked = true }
                    id.startsWith("UC") || type.contains("ARTIST") || type.contains("USER_CHANNEL") -> {
                        artists.add(MusicRef(name, id)); linked = true
                    }
                }
            }
            if (!linked && looseArtists == null && index <= 1 && text.lowercase() !in TYPE_LABELS &&
                !text.matches(Regex("^\\d{4}$")) && !COUNT.containsMatchIn(text)
            ) {
                looseArtists = text.split(", ", " & ", " x ").map { MusicRef(it.trim(), null) }.filter { it.name.isNotEmpty() }
            }
        }
        return Byline(
            artists = if (artists.isNotEmpty()) artists else looseArtists.orEmpty(),
            album = album,
            durationSec = duration,
            text = shown.joinToString(" • "),
        )
    }

    private fun kindOf(pageType: String, browseId: String?, videoId: String?, videoType: String): MusicKind? = when {
        browseId != null && (pageType.contains("ALBUM") || browseId.startsWith("MPREb")) -> MusicKind.Album
        browseId != null && (pageType.contains("ARTIST") || pageType.contains("USER_CHANNEL") || browseId.startsWith("UC")) -> MusicKind.Artist
        browseId != null && (pageType.contains("PODCAST") || browseId.startsWith("MPSP")) -> MusicKind.Podcast
        browseId != null && browseId.startsWith("MPED") -> MusicKind.Episode
        browseId != null && (pageType.contains("PLAYLIST") || browseId.startsWith("VL")) -> MusicKind.Playlist
        videoId != null -> when {
            videoType.contains("PODCAST_EPISODE") -> MusicKind.Episode
            videoType.contains("OMV") || videoType.contains("UGC") || videoType.contains("OFFICIAL_SOURCE") -> MusicKind.Video
            else -> MusicKind.Song
        }
        browseId != null -> MusicKind.Nav
        else -> null
    }

    private fun isExplicit(badges: JSONArray?): Boolean = badges?.toString()?.contains("MUSIC_EXPLICIT_BADGE") == true

    internal fun parseResponsive(r: JSONObject): MusicItem? {
        if (r.optString("musicItemRendererDisplayPolicy").contains("GREY_OUT")) return null
        val flex = r.optJSONArray("flexColumns") ?: return null
        fun col(i: Int): JSONObject? = flex.optJSONObject(i)?.let {
            it.optJSONObject("musicResponsiveListItemFlexColumnRenderer") ?: it.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
        }
        val titleRuns = col(0)?.runs("text")
        val title = runsText(titleRuns).trim()
        if (title.isEmpty()) return null
        val rest = JSONArray()
        for (i in 1 until flex.length()) {
            val runs = col(i)?.runs("text") ?: continue
            if (runs.length() == 0) continue
            if (rest.length() > 0) rest.put(JSONObject().put("text", " • "))
            for (k in 0 until runs.length()) rest.put(runs.get(k))
        }
        val by = byline(rest)
        val fixed = r.optJSONArray("fixedColumns")?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.runs("text")
        val duration = by.durationSec.takeIf { it > 0 } ?: parseDuration(runsText(fixed))
        val nav = r.optJSONObject("navigationEndpoint")
        val play = r.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")?.optJSONObject("playNavigationEndpoint")
        val titleNav = titleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
        val watch = watchOf(play) ?: watchOf(titleNav) ?: watchOf(nav)
        val watchPlaylist = play?.optJSONObject("watchPlaylistEndpoint") ?: nav?.optJSONObject("watchPlaylistEndpoint")
        val browse = browseOf(nav) ?: browseOf(titleNav)
        val browseId = browse?.optString("browseId")?.ifBlank { null }
        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.ifBlank { null }
            ?: watch?.optString("videoId")?.ifBlank { null }
        val kind = kindOf(pageTypeOf(browse), browseId, videoId, videoTypeOf(watch)) ?: return null
        val playable = kind == MusicKind.Song || kind == MusicKind.Video || kind == MusicKind.Episode
        val playlistId = watchPlaylist?.optString("playlistId")?.ifBlank { null }
            ?: watch?.optString("playlistId")?.ifBlank { null }
            ?: browseId?.takeIf { it.startsWith("VL") }?.removePrefix("VL")
        return MusicItem(
            kind = kind,
            title = title,
            subtitle = by.text,
            thumbnail = bestThumb(r.optJSONObject("thumbnail")),
            videoId = if (playable) videoId else null,
            browseId = if (playable) null else browseId,
            playlistId = playlistId,
            watchParams = (watchPlaylist ?: watch)?.optString("params")?.ifBlank { null },
            artists = by.artists,
            album = by.album,
            durationSec = duration,
            explicit = isExplicit(r.optJSONArray("badges")),
            wide = kind == MusicKind.Video,
        )
    }

    internal fun parseTwoRow(r: JSONObject): MusicItem? {
        val title = runsText(r.runs("title")).trim()
        if (title.isEmpty()) return null
        val by = byline(r.runs("subtitle"))
        val nav = r.optJSONObject("navigationEndpoint")
        val play = r.optJSONObject("thumbnailOverlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")?.optJSONObject("playNavigationEndpoint")
        val browse = browseOf(nav)
        val browseId = browse?.optString("browseId")?.ifBlank { null }
        val watch = watchOf(nav) ?: watchOf(play)
        val watchPlaylist = nav?.optJSONObject("watchPlaylistEndpoint") ?: play?.optJSONObject("watchPlaylistEndpoint")
        val videoId = watch?.optString("videoId")?.ifBlank { null }
        val kind = kindOf(pageTypeOf(browse), browseId, if (browse == null) videoId else null, videoTypeOf(watch))
            ?: if (watchPlaylist != null) MusicKind.Playlist else return null
        val playable = kind == MusicKind.Song || kind == MusicKind.Video || kind == MusicKind.Episode
        return MusicItem(
            kind = kind,
            title = title,
            subtitle = by.text,
            thumbnail = bestThumb(r.optJSONObject("thumbnailRenderer")),
            videoId = if (playable) (videoId ?: browseId?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")) else null,
            browseId = if (playable) null else browseId,
            browseParams = browse?.optString("params")?.ifBlank { null },
            playlistId = watchPlaylist?.optString("playlistId")?.ifBlank { null }
                ?: watch?.optString("playlistId")?.ifBlank { null }
                ?: browseId?.takeIf { it.startsWith("VL") }?.removePrefix("VL"),
            watchParams = (watchPlaylist ?: watch)?.optString("params")?.ifBlank { null },
            artists = by.artists,
            album = by.album,
            explicit = isExplicit(r.optJSONArray("subtitleBadges")),
            wide = r.optString("aspectRatio").contains("16_9") || kind == MusicKind.Video,
        )
    }

    private fun parseNavButton(r: JSONObject): MusicItem? {
        val title = runsText(r.runs("buttonText")).trim().ifEmpty { return null }
        val browse = browseOf(r.optJSONObject("clickCommand")) ?: return null
        val id = browse.optString("browseId").ifBlank { return null }
        val stripe = r.optJSONObject("solid")?.optLong("leftStripeColor")?.takeIf { it != 0L }
        return MusicItem(MusicKind.Nav, title, browseId = id, browseParams = browse.optString("params").ifBlank { null }, color = stripe)
    }

    private fun parsePanelVideo(r: JSONObject): MusicItem? {
        if (r.has("unplayableText")) return null
        val videoId = r.optString("videoId").ifBlank { return null }
        val title = textOf(r, "title").trim().ifEmpty { return null }
        val by = byline(r.runs("longBylineText") ?: r.runs("shortBylineText"))
        val watch = watchOf(r.optJSONObject("navigationEndpoint"))
        val type = videoTypeOf(watch)
        return MusicItem(
            kind = if (type.contains("OMV") || type.contains("UGC")) MusicKind.Video else MusicKind.Song,
            title = title,
            subtitle = by.text,
            thumbnail = bestThumb(r.optJSONObject("thumbnail")),
            videoId = videoId,
            artists = by.artists,
            album = by.album,
            durationSec = parseDuration(textOf(r, "lengthText")),
            explicit = isExplicit(r.optJSONArray("badges")),
        )
    }

    private fun parseCardShelf(card: JSONObject): MusicSection? {
        val titleRuns = card.runs("title")
        val title = runsText(titleRuns).trim()
        val nav = titleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint") ?: card.optJSONObject("onTap")
        val by = byline(card.runs("subtitle"))
        val browse = browseOf(nav)
        val browseId = browse?.optString("browseId")?.ifBlank { null }
        val watch = watchOf(nav)
        val videoId = watch?.optString("videoId")?.ifBlank { null }
        val kind = kindOf(pageTypeOf(browse), browseId, videoId, videoTypeOf(watch))
        val items = ArrayList<MusicItem>()
        if (kind != null && title.isNotEmpty()) {
            val playable = kind == MusicKind.Song || kind == MusicKind.Video || kind == MusicKind.Episode
            items.add(
                MusicItem(
                    kind = kind,
                    title = title,
                    subtitle = by.text,
                    thumbnail = bestThumb(card.optJSONObject("thumbnail")),
                    videoId = if (playable) videoId else null,
                    browseId = if (playable) null else browseId,
                    playlistId = watch?.optString("playlistId")?.ifBlank { null },
                    artists = by.artists,
                    album = by.album,
                    durationSec = by.durationSec,
                    wide = kind == MusicKind.Video,
                ),
            )
        }
        card.optJSONArray("contents")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer")?.let(::parseResponsive)?.let(items::add)
            }
        }
        if (items.isEmpty()) return null
        val header = card.optJSONObject("header")?.optJSONObject("musicCardShelfHeaderBasicRenderer")
        return MusicSection(runsText(header?.runs("title")).ifBlank { "Top result" }, items.distinctBy { it.key }, MusicLayout.TopResult)
    }

    private fun parseItems(arr: JSONArray?): List<MusicItem> {
        if (arr == null) return emptyList()
        val out = LinkedHashMap<String, MusicItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val item = o.optJSONObject("musicTwoRowItemRenderer")?.let(::parseTwoRow)
                ?: o.optJSONObject("musicResponsiveListItemRenderer")?.let(::parseResponsive)
                ?: o.optJSONObject("musicNavigationButtonRenderer")?.let(::parseNavButton)
                ?: o.optJSONObject("playlistPanelVideoRenderer")?.let(::parsePanelVideo)
                ?: continue
            out.putIfAbsent(item.key, item)
        }
        return out.values.toList()
    }

    private fun moreOf(endpoint: JSONObject?, title: String): MusicItem? {
        val browse = browseOf(endpoint) ?: return null
        val id = browse.optString("browseId").ifBlank { return null }
        val type = pageTypeOf(browse)
        val kind = when {
            id.startsWith("VL") || type.contains("PLAYLIST") -> MusicKind.Playlist
            id.startsWith("MPREb") -> MusicKind.Album
            id.startsWith("UC") && browse.optString("params").isBlank() -> MusicKind.Artist
            else -> MusicKind.Nav
        }
        return MusicItem(kind, title, browseId = id, browseParams = browse.optString("params").ifBlank { null })
    }

    internal fun parseSection(sec: JSONObject): MusicSection? {
        (sec.optJSONObject("musicCarouselShelfRenderer") ?: sec.optJSONObject("musicImmersiveCarouselShelfRenderer"))?.let { shelf ->
            val header = shelf.optJSONObject("header")?.let {
                it.optJSONObject("musicCarouselShelfBasicHeaderRenderer") ?: it.optJSONObject("musicImmersiveCarouselShelfBasicHeaderRenderer")
            }
            val titleRuns = header?.runs("title")
            val title = runsText(titleRuns).trim()
            val contents = shelf.optJSONArray("contents")
            val items = parseItems(contents)
            if (items.isEmpty()) return null
            var responsive = 0
            if (contents != null) for (i in 0 until contents.length()) {
                if (contents.optJSONObject(i)?.has("musicResponsiveListItemRenderer") == true) responsive++
            }
            val moreEp = header?.optJSONObject("moreContentButton")?.optJSONObject("buttonRenderer")?.optJSONObject("navigationEndpoint")
                ?: titleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
            return MusicSection(
                title = title,
                items = items,
                layout = if (responsive * 2 > items.size) MusicLayout.QuickPicks else MusicLayout.Carousel,
                strapline = runsText(header?.runs("strapline")).ifBlank { null },
                thumbnail = bestThumb(header?.optJSONObject("thumbnail")),
                more = moreOf(moreEp, title),
            )
        }
        sec.optJSONObject("musicShelfRenderer")?.let { shelf ->
            val titleRuns = shelf.runs("title")
            val items = parseItems(shelf.optJSONArray("contents"))
            if (items.isEmpty()) return null
            val title = runsText(titleRuns).trim()
            return MusicSection(
                title = title,
                items = items,
                layout = MusicLayout.List,
                more = moreOf(titleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint"), title)
                    ?: moreOf(shelf.optJSONObject("bottomEndpoint"), title),
                searchParams = shelf.optJSONObject("bottomEndpoint")?.optJSONObject("searchEndpoint")?.optString("params")?.ifBlank { null },
            )
        }
        sec.optJSONObject("musicPlaylistShelfRenderer")?.let { shelf ->
            val items = parseItems(shelf.optJSONArray("contents"))
            if (items.isEmpty()) return null
            return MusicSection("", items, MusicLayout.List)
        }
        sec.optJSONObject("gridRenderer")?.let { grid ->
            val items = parseItems(grid.optJSONArray("items"))
            if (items.isEmpty()) return null
            val title = runsText(grid.optJSONObject("header")?.optJSONObject("gridHeaderRenderer")?.runs("title")).trim()
            return MusicSection(title, items, MusicLayout.Grid)
        }
        sec.optJSONObject("musicCardShelfRenderer")?.let { return parseCardShelf(it) }
        sec.optJSONObject("musicDescriptionShelfRenderer")?.let { d ->
            val text = runsText(d.runs("description")).trim()
            if (text.isEmpty()) return null
            return MusicSection(runsText(d.runs("header")).trim(), emptyList(), MusicLayout.Description, description = text)
        }
        sec.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::parseSection)?.let { return it }
        }
        return null
    }

    private fun parseSections(arr: JSONArray?): List<MusicSection> {
        if (arr == null) return emptyList()
        val out = ArrayList<MusicSection>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::parseSection)?.let(out::add)
        return out
    }

    /** "L:" tokens go in the URL (older pages), "C:" tokens in the body (newer ones). */
    private fun continuationOf(obj: JSONObject?): String? {
        if (obj == null) return null
        obj.optJSONArray("continuations")?.optJSONObject(0)?.let { c ->
            for (k in listOf("nextContinuationData", "nextRadioContinuationData", "reloadContinuationData")) {
                c.optJSONObject(k)?.optString("continuation")?.ifBlank { null }?.let { return "L:$it" }
            }
        }
        for (key in listOf("contents", "items", "continuationItems")) {
            val arr = obj.optJSONArray(key) ?: continue
            val last = arr.optJSONObject(arr.length() - 1) ?: continue
            last.optJSONObject("continuationItemRenderer")?.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")?.optString("token")?.ifBlank { null }?.let { return "C:$it" }
        }
        return null
    }

    private fun chipsOf(sectionList: JSONObject?): List<MusicChip> {
        val chips = sectionList?.optJSONObject("header")?.optJSONObject("chipCloudRenderer")?.optJSONArray("chips") ?: return emptyList()
        val out = ArrayList<MusicChip>()
        for (i in 0 until chips.length()) {
            val chip = chips.optJSONObject(i)?.optJSONObject("chipCloudChipRenderer") ?: continue
            val title = textOf(chip, "text").trim()
            if (title.isEmpty()) continue
            val ep = chip.optJSONObject("navigationEndpoint")
            val browse = browseOf(ep)
            val search = ep?.optJSONObject("searchEndpoint")
            out.add(
                MusicChip(
                    title = title,
                    browseId = browse?.optString("browseId")?.ifBlank { null },
                    params = browse?.optString("params")?.ifBlank { null } ?: search?.optString("params")?.ifBlank { null },
                    selected = chip.optBoolean("isSelected"),
                ),
            )
        }
        return out
    }

    private fun headerTitle(root: JSONObject): String {
        val header = root.optJSONObject("header")
        if (header != null) {
            for (k in listOf("musicHeaderRenderer", "musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer", "musicDetailHeaderRenderer", "musicResponsiveHeaderRenderer")) {
                header.optJSONObject(k)?.let { return textOf(it, "title").trim() }
            }
        }
        return textOf(find(root, "musicResponsiveHeaderRenderer"), "title").trim()
    }

    internal fun parsePage(root: JSONObject): MusicPage {
        val sl = find(root.optJSONObject("contents"), "sectionListRenderer")
        val sections = parseSections(sl?.optJSONArray("contents"))
        val cont = continuationOf(sl) ?: sections.lastOrNull()?.let {
            // A library grid or list carries its own next page.
            val arr = sl?.optJSONArray("contents")
            val last = arr?.optJSONObject(arr.length() - 1)
            continuationOf(last?.optJSONObject("gridRenderer") ?: last?.optJSONObject("musicShelfRenderer")
                ?: last?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.optJSONObject(0)?.let {
                    it.optJSONObject("gridRenderer") ?: it.optJSONObject("musicShelfRenderer")
                })
        }
        return MusicPage(headerTitle(root), sections, chipsOf(sl), cont)
    }

    internal fun parseMore(root: JSONObject): MusicMore {
        val cc = root.optJSONObject("continuationContents")
        if (cc != null) {
            cc.optJSONObject("sectionListContinuation")?.let { s ->
                return MusicMore(parseSections(s.optJSONArray("contents")), emptyList(), continuationOf(s))
            }
            for (k in listOf("musicPlaylistShelfContinuation", "musicShelfContinuation", "gridContinuation", "musicCarouselShelfContinuation")) {
                cc.optJSONObject(k)?.let { s ->
                    val arr = s.optJSONArray("contents") ?: s.optJSONArray("items")
                    return MusicMore(emptyList(), parseItems(arr), continuationOf(s))
                }
            }
        }
        val actions = root.optJSONArray("onResponseReceivedActions")
        if (actions != null) {
            for (i in 0 until actions.length()) {
                val a = actions.optJSONObject(i) ?: continue
                val append = a.optJSONObject("appendContinuationItemsAction") ?: a.optJSONObject("reloadContinuationItemsCommand") ?: continue
                val arr = append.optJSONArray("continuationItems") ?: continue
                val sections = parseSections(arr)
                val items = parseItems(arr)
                return MusicMore(if (items.isEmpty()) sections else emptyList(), items, continuationOf(append))
            }
        }
        return MusicMore(emptyList(), emptyList(), null)
    }

    internal fun parseCollection(root: JSONObject, browseId: String): MusicCollection {
        val header = find(root, "musicResponsiveHeaderRenderer") ?: find(root, "musicDetailHeaderRenderer")
            ?: find(root, "musicImmersiveHeaderRenderer") ?: find(root, "musicVisualHeaderRenderer")
        val title = textOf(header, "title").trim()
        val subtitle = textOf(header, "subtitle").trim()
        val second = textOf(header, "secondSubtitle").trim()
        val strap = header?.runs("straplineTextOne") ?: header?.runs("subtitle")
        var author: MusicRef? = null
        if (strap != null) for (i in 0 until strap.length()) {
            val run = strap.optJSONObject(i) ?: continue
            val b = browseOf(run.optJSONObject("navigationEndpoint")) ?: continue
            val id = b.optString("browseId").ifBlank { null } ?: continue
            if (id.startsWith("UC") || pageTypeOf(b).contains("ARTIST") || pageTypeOf(b).contains("USER_CHANNEL")) {
                author = MusicRef(run.optString("text"), id); break
            }
        }
        if (author == null) {
            val strapText = runsText(header?.runs("straplineTextOne")).trim()
            if (strapText.isNotEmpty()) author = MusicRef(strapText, null)
        }
        val description = header?.optJSONObject("description")?.let { d ->
            d.optJSONObject("musicDescriptionShelfRenderer")?.let { runsText(it.runs("description")) } ?: runsText(d.optJSONArray("runs"))
        }?.trim()?.ifEmpty { null }
        val thumb = bestThumb(header?.optJSONObject("thumbnail"))
        val isAlbum = browseId.startsWith("MPREb")
        val shelf = find(root, "musicPlaylistShelfRenderer") ?: find(root.optJSONObject("contents"), "musicShelfRenderer")
        val rows = shelf?.optJSONArray("contents")
        var tracks = parseItems(rows).filter { it.playable }
        if (isAlbum) {
            val albumRef = MusicRef(title, browseId)
            tracks = tracks.map { t ->
                t.copy(
                    thumbnail = t.thumbnail ?: thumb,
                    album = t.album ?: albumRef,
                    artists = t.artists.ifEmpty { listOfNotNull(author) },
                    subtitle = t.subtitle.ifBlank { author?.name.orEmpty() },
                )
            }
        }
        val related = ArrayList<MusicSection>()
        root.walk { key, obj -> if (key == "musicCarouselShelfRenderer") parseSection(JSONObject().put(key, obj))?.let(related::add) }
        val canonical = root.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")?.optString("urlCanonical").orEmpty()
        val playlistId = shelf?.optString("playlistId")?.ifBlank { null }
            ?: Regex("[?&]list=([A-Za-z0-9_-]+)").find(canonical)?.groupValues?.get(1)
            ?: tracks.firstNotNullOfOrNull { it.playlistId }
            ?: browseId.takeIf { it.startsWith("VL") }?.removePrefix("VL")
        return MusicCollection(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            secondSubtitle = second,
            description = description,
            thumbnail = thumb,
            author = author,
            isAlbum = isAlbum,
            playlistId = playlistId,
            tracks = tracks.distinctBy { it.videoId },
            continuation = continuationOf(shelf),
            related = related.distinctBy { it.title },
        )
    }

    internal fun parseArtist(root: JSONObject, browseId: String): MusicArtist {
        val header = root.optJSONObject("header")?.let { h ->
            h.optJSONObject("musicImmersiveHeaderRenderer") ?: h.optJSONObject("musicVisualHeaderRenderer")
                ?: h.optJSONObject("musicHeaderRenderer") ?: h.optJSONObject("musicResponsiveHeaderRenderer")
        } ?: find(root, "musicImmersiveHeaderRenderer") ?: find(root, "musicVisualHeaderRenderer")
        val sub = header?.optJSONObject("subscriptionButton")?.optJSONObject("subscribeButtonRenderer")
        fun endpointItem(button: String, label: String): MusicItem? {
            val ep = header?.optJSONObject(button)?.optJSONObject("buttonRenderer")?.optJSONObject("navigationEndpoint") ?: return null
            val wp = ep.optJSONObject("watchPlaylistEndpoint")
            val w = watchOf(ep)
            val pid = wp?.optString("playlistId")?.ifBlank { null } ?: w?.optString("playlistId")?.ifBlank { null }
            return MusicItem(
                MusicKind.Playlist, label,
                videoId = null,
                playlistId = pid ?: return null,
                watchParams = (wp ?: w)?.optString("params")?.ifBlank { null },
            )
        }
        val sl = find(root.optJSONObject("contents"), "sectionListRenderer")
        return MusicArtist(
            browseId = browseId,
            name = textOf(header, "title").trim(),
            description = textOf(header, "description").trim().ifEmpty { null },
            thumbnail = bestThumb(header?.optJSONObject("thumbnail") ?: header?.optJSONObject("foregroundThumbnail")),
            subscribers = sub?.let { textOf(it, "subscriberCountText").ifBlank { textOf(it, "shortSubscriberCountText") } }?.trim()?.ifEmpty { null },
            subscribed = sub?.optBoolean("subscribed") == true,
            channelId = sub?.optString("channelId")?.ifBlank { null } ?: browseId.takeIf { it.startsWith("UC") },
            shuffle = endpointItem("playButton", "Shuffle"),
            radio = endpointItem("startRadioButton", "Radio"),
            sections = parseSections(sl?.optJSONArray("contents")),
        )
    }

    internal fun parseSearch(root: JSONObject): MusicPage {
        val sl = find(root.optJSONObject("contents"), "sectionListRenderer")
        val arr = sl?.optJSONArray("contents")
        val sections = parseSections(arr)
        var cont: String? = null
        if (arr != null) for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.optJSONObject("musicShelfRenderer")?.let { s -> continuationOf(s)?.let { cont = it } }
        }
        return MusicPage("", sections, chipsOf(sl), cont)
    }

    internal fun parseQueue(root: JSONObject): MusicQueuePage {
        val panel = find(root, "playlistPanelRenderer") ?: find(root, "playlistPanelContinuation")
            ?: return MusicQueuePage(emptyList(), null, null)
        val contents = panel.optJSONArray("contents")
        val items = LinkedHashMap<String, MusicItem>()
        var automix: MusicItem? = null
        if (contents != null) for (i in 0 until contents.length()) {
            val o = contents.optJSONObject(i) ?: continue
            // A song and its music video come as a pair; only the one shown is part of the queue.
            val video = o.optJSONObject("playlistPanelVideoRenderer")
                ?: o.optJSONObject("playlistPanelVideoWrapperRenderer")?.optJSONObject("primaryRenderer")?.optJSONObject("playlistPanelVideoRenderer")
            if (video != null) {
                parsePanelVideo(video)?.let { items.putIfAbsent(it.key, it) }
                continue
            }
            o.optJSONObject("automixPreviewVideoRenderer")?.let { a ->
                val ep = find(a, "watchPlaylistEndpoint")
                val pid = ep?.optString("playlistId")?.ifBlank { null }
                if (pid != null) automix = MusicItem(MusicKind.Playlist, "Autoplay", playlistId = pid, watchParams = ep.optString("params").ifBlank { null })
            }
        }
        return MusicQueuePage(items.values.toList(), continuationOf(panel), automix)
    }
}
