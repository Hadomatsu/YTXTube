package com.tensei.ytxtube

import android.util.Log
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * YouTube Premium's 256 kbps audio. YouTube only offers it (Opus itag 774, AAC itag 141) to its
 * Music web client for a Premium account, never in the streams the normal extraction sees. So for
 * a signed-in account this asks YouTube Music's player for the song, unlocks the URL the way the
 * web player does (signature and n parameter, through NewPipe's player code), adds the
 * proof-of-origin token when YouTube wants one, and checks that the stream really answers before
 * it is used. Anything that fails leaves the normal stream in place.
 */
object PremiumAudio {
    private const val TAG = "PremiumAudio"
    private const val MAX_AGE_MS = 4 * 60 * 60 * 1000L

    data class Stream(val url: String, val itag: Int)

    private class Entry(val at: Long, val stream: Stream?)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newFixedThreadPool(2)

    private fun wanted(): Boolean =
        PlaybackPrefs.premiumAudio && Account.signedIn && PlaybackPrefs.audioQuality != AudioQuality.DATA_SAVER &&
            AudioTrackPreference.key == null

    /** The unlocked stream for this video, if one was found and is still fresh. */
    fun get(videoId: String): Stream? {
        if (!wanted() || videoId in failed) return null
        val e = cache[videoId] ?: return null
        if (System.currentTimeMillis() - e.at > MAX_AGE_MS) return null
        return e.stream
    }

    /** Starts looking for the Premium stream; the caller may wait a little on the result. */
    fun prefetch(videoId: String): Future<*>? {
        if (!wanted() || videoId in failed) return null
        cache[videoId]?.let { if (System.currentTimeMillis() - it.at < MAX_AGE_MS) return null }
        return pool.submit {
            val found = runCatching { resolve(videoId) }
                .onFailure { Log.w(TAG, "no premium audio for $videoId: ${it.message}") }
                .getOrNull()
            cache[videoId] = Entry(System.currentTimeMillis(), found)
            Log.i(TAG, "premium audio for $videoId: " + (found?.let { "itag ${it.itag}" } ?: "none"))
        }
    }

    /** The Premium stream was refused while playing: use the normal stream for this video from now on. */
    fun markFailed(videoId: String) {
        failed += videoId
        cache.remove(videoId)
    }

    private fun resolve(videoId: String): Stream? {
        val sts = runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }.getOrNull()
        val po = runCatching { YtxPoTokenProvider.current?.getWebClientPoToken(videoId) }.getOrNull()
        val body = JSONObject()
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        if (sts != null) {
            body.put(
                "playbackContext",
                JSONObject().put("contentPlaybackContext", JSONObject().put("signatureTimestamp", sts)),
            )
        }
        po?.playerRequestPoToken?.takeIf { it.isNotBlank() }?.let {
            body.put("serviceIntegrityDimensions", JSONObject().put("poToken", it))
        }
        val root = InnerTube.postMusic("player", body)
        val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return null
        val all = (0 until formats.length()).mapNotNull { formats.optJSONObject(it) }
        val order = if (PlaybackPrefs.audioQuality == AudioQuality.PREFER_AAC) listOf(141, 774) else listOf(774, 141)
        for (itag in order) {
            val f = all.firstOrNull { it.optInt("itag") == itag } ?: continue
            var url = f.optString("url").takeIf { it.isNotBlank() }
            if (url == null) {
                val cipher = f.optString("signatureCipher").ifBlank { f.optString("cipher") }
                if (cipher.isBlank()) continue
                val q = query(cipher)
                val s = q["s"] ?: continue
                val base = q["url"] ?: continue
                val sp = q["sp"] ?: "signature"
                val sig = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, s)
                url = base + "&" + sp + "=" + URLEncoder.encode(sig, "UTF-8")
            }
            url = runCatching { YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url) }
                .getOrDefault(url)
            val withToken = po?.streamingDataPoToken?.takeIf { it.isNotBlank() }?.let { url + "&pot=" + URLEncoder.encode(it, "UTF-8") }
            for (candidate in listOfNotNull(withToken, url)) {
                if (answers(candidate)) return Stream(candidate, itag)
            }
        }
        return null
    }

    /** A two-byte read: YouTube answers it only for a URL it will really serve. */
    private fun answers(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1")
            .header("User-Agent", Net.USER_AGENT)
            .build()
        Net.client.newCall(request).execute().use { it.code == 206 || it.code == 200 }
    }.getOrDefault(false)

    private fun query(s: String): Map<String, String> = s.split('&').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else part.substring(0, i) to URLDecoder.decode(part.substring(i + 1), "UTF-8")
    }.toMap()
}
