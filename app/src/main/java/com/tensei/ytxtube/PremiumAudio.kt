package com.tensei.ytxtube

import android.util.Log
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
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
 * proof-of-origin token when YouTube wants one, and checks that the stream really answers, at the
 * start and further in, before it is used. It is played in short ranges like the normal streams.
 * Anything that fails leaves the normal stream in place, and a stream cut off while playing puts
 * Premium audio on hold for a while so songs do not keep stopping.
 */
object PremiumAudio {
    private const val TAG = "PremiumAudio"
    private const val MAX_AGE_MS = 4 * 60 * 60 * 1000L
    private const val HOLD_AFTER_FAILURE_MS = 45 * 60 * 1000L

    /** [itagItem] describes the file (ranges, codec) so it can be played in short ranges. */
    data class Stream(val url: String, val itag: Int, val itagItem: ItagItem?)

    private class Entry(val at: Long, val stream: Stream?)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newFixedThreadPool(2)

    @Volatile
    private var heldUntil = 0L

    private fun wanted(): Boolean =
        PlaybackPrefs.premiumAudio && Account.signedIn && PlaybackPrefs.audioQuality != AudioQuality.DATA_SAVER &&
            AudioTrackPreference.key == null && System.currentTimeMillis() >= heldUntil

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
        cache.clear()
        heldUntil = System.currentTimeMillis() + HOLD_AFTER_FAILURE_MS
        Log.w(TAG, "premium audio cut off for $videoId; using the normal streams for a while")
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
            val length = f.optString("contentLength").toLongOrNull() ?: 0L
            for (candidate in listOfNotNull(withToken, url)) {
                // The start and a spot three quarters in: a stream YouTube means to cut off
                // answers the first but not the second.
                if (answers(candidate, 0L) && (length < 400_000L || answers(candidate, length * 3 / 4))) {
                    return Stream(candidate, itag, itagItemOf(f, itag))
                }
            }
        }
        return null
    }

    /** What the DASH description needs: codec, bitrate, channels and the init and index ranges. */
    private fun itagItemOf(f: JSONObject, itag: Int): ItagItem? = runCatching {
        val mime = f.optString("mimeType")
        val codec = Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.get(1) ?: return null
        val init = f.optJSONObject("initRange") ?: return null
        val index = f.optJSONObject("indexRange") ?: return null
        val format = if (mime.startsWith("audio/webm")) MediaFormat.WEBMA_OPUS else MediaFormat.M4A
        val bitrate = f.optInt("bitrate").takeIf { it > 0 } ?: return null
        ItagItem(itag, ItagItem.ItagType.AUDIO, format, f.optInt("averageBitrate", bitrate) / 1000).apply {
            setBitrate(bitrate)
            setCodec(codec)
            setAudioChannels(f.optInt("audioChannels", 2).takeIf { it > 0 } ?: 2)
            f.optString("audioSampleRate").toIntOrNull()?.let { setSampleRate(it) }
            setInitStart(init.optString("start").toInt())
            setInitEnd(init.optString("end").toInt())
            setIndexStart(index.optString("start").toInt())
            setIndexEnd(index.optString("end").toInt())
            f.optString("approxDurationMs").toLongOrNull()?.let { setApproxDurationMs(it) }
            f.optString("contentLength").toLongOrNull()?.let { setContentLength(it) }
        }
    }.getOrNull()

    /** A two-byte read at [from]: YouTube answers it only for a URL it will really serve there. */
    private fun answers(url: String, from: Long): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$from-${from + 1}")
            .header("User-Agent", Net.USER_AGENT)
            .build()
        Net.client.newCall(request).execute().use { it.code == 206 || it.code == 200 }
    }.getOrDefault(false)

    private fun query(s: String): Map<String, String> = s.split('&').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else part.substring(0, i) to URLDecoder.decode(part.substring(i + 1), "UTF-8")
    }.toMap()
}
