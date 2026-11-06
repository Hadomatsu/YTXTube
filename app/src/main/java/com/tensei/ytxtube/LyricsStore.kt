package com.tensei.ytxtube

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Lyrics, ready before you open them: a song's lyrics are looked up as soon as it starts (and the
 * next song's a little later), and kept on the phone, so opening the lyrics again, even after a
 * restart, shows them at once. When a better version arrives later (word by word timing), the
 * lyrics on screen switch to it.
 */
object LyricsStore {
    private const val TAG = "LyricsStore"
    private const val MAX_FILES = 400
    private const val NONE_TTL_MS = 6 * 60 * 60 * 1000L

    private val docs = mutableStateMapOf<String, LyricsDoc>()
    private val loading = mutableStateMapOf<String, Boolean>()
    private val noneAt = ConcurrentHashMap<String, Long>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val dir: File by lazy { File(YTXTubeApplication.appContext.filesDir, "lyrics").apply { mkdirs() } }

    /** The lyrics shown for this song, or null while not known (yet). */
    fun doc(videoId: String): LyricsDoc? = docs[videoId]

    fun isLoading(videoId: String): Boolean = loading[videoId] == true

    /** Looks the song's lyrics up now if they are not known yet. */
    fun load(item: VideoItem, durationSec: Long = item.durationSec) {
        val id = item.id
        if (id.isBlank() || docs.containsKey(id) || jobs.containsKey(id)) return
        noneAt[id]?.let { if (System.currentTimeMillis() - it < NONE_TTL_MS) return }
        loading[id] = true
        val job = scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { read(id) }
                if (saved != null) {
                    docs[id] = saved
                    return@launch
                }
                val found = runCatching {
                    InnerTube.musicLyrics(id, item.title, item.channel, durationSec) { better ->
                        scope.launch {
                            docs[id] = better
                            withContext(Dispatchers.IO) { write(id, better) }
                        }
                    }
                }.onFailure { Log.w(TAG, "lyrics for $id: ${it.message}") }.getOrNull()
                if (found != null && found.lines.isNotEmpty()) {
                    // A word-timed upgrade may already have landed; keep that one.
                    if (docs[id]?.wordSynced != true) docs[id] = found
                    withContext(Dispatchers.IO) { write(id, docs[id] ?: found) }
                } else {
                    noneAt[id] = System.currentTimeMillis()
                }
            } finally {
                loading.remove(id)
                jobs.remove(id)
            }
        }
        jobs[id] = job
    }

    /** Same as [load], for songs that are about to play. */
    fun prefetch(item: VideoItem, durationSec: Long = item.durationSec) {
        if (item.localUri != null && item.id.length != 11) return
        load(item, durationSec)
    }

    // ------------------------------------------------------------------- saved on the phone

    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    private fun read(id: String): LyricsDoc? = runCatching {
        val f = file(id)
        if (!f.exists()) return null
        val root = JSONObject(f.readText())
        val arr = root.optJSONArray("lines") ?: return null
        val lines = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val w = o.optJSONArray("w")
            LyricLine(
                timeMs = o.optLong("t", -1L),
                text = o.optString("x"),
                words = if (w == null) emptyList() else (0 until w.length()).mapNotNull { k ->
                    w.optJSONArray(k)?.let { LyricWord(it.optLong(0), it.optLong(1), it.optString(2)) }
                },
                endMs = o.optLong("e", -1L),
                background = o.optString("b").ifBlank { null },
            )
        }
        f.setLastModified(System.currentTimeMillis())
        if (lines.isEmpty()) null else LyricsDoc(lines, root.optString("source"))
    }.getOrNull()

    private fun write(id: String, doc: LyricsDoc) {
        runCatching {
            val arr = JSONArray()
            doc.lines.forEach { line ->
                val o = JSONObject().put("t", line.timeMs).put("x", line.text)
                if (line.endMs >= 0) o.put("e", line.endMs)
                line.background?.let { o.put("b", it) }
                if (line.words.isNotEmpty()) {
                    val w = JSONArray()
                    line.words.forEach { w.put(JSONArray().put(it.startMs).put(it.endMs).put(it.text)) }
                    o.put("w", w)
                }
                arr.put(o)
            }
            file(id).writeText(JSONObject().put("source", doc.source).put("lines", arr).toString())
            trim()
        }.onFailure { Log.w(TAG, "could not save lyrics for $id: ${it.message}") }
    }

    private fun trim() {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }
}
