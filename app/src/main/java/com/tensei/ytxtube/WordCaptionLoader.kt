package com.tensei.ytxtube

import android.util.Log
import java.util.Collections

/**
 * Downloads a caption track and turns it into a [WordTimeline].
 *
 * Finished timelines are kept in memory by URL, and the player prefetches the likely track as
 * soon as a video opens, so switching captions on is instant instead of waiting on the network.
 */
object WordCaptionLoader {
    private const val TAG = "YTXTubeWordCaptions"
    private const val MAX_ENTRIES = 12

    private val cache: MutableMap<String, WordTimeline> = Collections.synchronizedMap(
        object : LinkedHashMap<String, WordTimeline>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WordTimeline>?) = size > MAX_ENTRIES
        },
    )

    /** A timeline that is already downloaded, or null. Safe to call from the main thread. */
    fun cached(url: String): WordTimeline? = cache[CaptionTracks.asVtt(url)]

    // One lock per URL, so the prefetch and a CC tap that arrive together download the file once.
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** Blocking download + parse. Returns an empty timeline when nothing usable came back. */
    fun load(url: String): WordTimeline {
        val vttUrl = CaptionTracks.asVtt(url)
        cache[vttUrl]?.let { return it }
        val lock = locks.getOrPut(vttUrl) { Any() }
        return synchronized(lock) {
            cache[vttUrl] ?: download(url, vttUrl)
        }
    }

    private fun download(url: String, vttUrl: String): WordTimeline {
        // WebVTT first: auto-generated captions carry a timestamp for every word in it.
        val vtt = SubtitleParser.fetchRaw(vttUrl)?.let { raw ->
            if (raw.contains("-->")) WordCaptionParser.parseVtt(raw) else null
        }
        if (vtt != null && !vtt.isEmpty) {
            cache[vttUrl] = vtt
            return vtt
        }

        // Anything else the extractor handed us (TTML / XML): no word times, so spread each cue.
        if (url != vttUrl) {
            val plain = SubtitleParser.fetchAndParse(url)
            if (plain.isNotEmpty()) {
                val words = plain.flatMap { WordCaptionParser.plainWords(it.text.replace('\n', ' '), it.startMs, it.endMs) }
                val timeline = WordTimeline(words)
                if (!timeline.isEmpty) {
                    cache[vttUrl] = timeline
                    return timeline
                }
            }
        }
        Log.w(TAG, "No usable captions at $vttUrl")
        return WordTimeline(emptyList())
    }
}
