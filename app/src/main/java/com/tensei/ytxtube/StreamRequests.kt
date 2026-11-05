package com.tensei.ytxtube

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Stream requests made the way YouTube's own players (and NewPipe) make them. A video's streams
 * can come from YouTube's Android, iOS or visionOS client, and YouTube serves a stream at full
 * speed only when it is asked for the way that client would: visionOS streams need the visionOS
 * user agent, every request is a POST with YouTube's tiny body, and each one carries a request
 * number. Asked for with a desktop browser's user agent, a visionOS stream could trickle in barely
 * faster than it plays, which is the "lags every second" on one 1080p video but not another.
 */
@OptIn(UnstableApi::class)
object StreamRequests {
    private val visionOsAgent: String? by lazy {
        runCatching { YoutubeParsingHelper.getVisionOsUserAgent(null) }.getOrNull()
    }
    private val POST_BODY = byteArrayOf(0x78, 0)

    /** The HTTP(S) layer for every YouTube player in the app; local files are passed through. */
    fun factory(context: Context): DataSource.Factory =
        DefaultDataSource.Factory(context, Shaped.Factory(OkHttpDataSource.Factory(Net.client)))

    private fun isVideoPlayback(uri: Uri): Boolean =
        uri.host?.endsWith(".googlevideo.com") == true && uri.path?.startsWith("/videoplayback") == true

    private class Shaped(private val upstream: DataSource) : DataSource {
        private var requestNumber = 0

        override fun addTransferListener(transferListener: TransferListener) =
            upstream.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long = upstream.open(shape(dataSpec))

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)

        override fun getUri(): Uri? = upstream.uri

        override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

        override fun close() = upstream.close()

        private fun shape(spec: DataSpec): DataSpec {
            val uri = spec.uri
            if (!isVideoPlayback(uri)) {
                return spec.withAdditionalHeaders(mapOf("User-Agent" to Net.USER_AGENT))
            }
            val url = uri.toString()
            val numbered = if ("&rn=" in url) url else url + "&rn=" + requestNumber++
            val agent = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) visionOsAgent ?: Net.USER_AGENT
            else Net.USER_AGENT
            return spec.buildUpon()
                .setUri(Uri.parse(numbered))
                .setHttpMethod(DataSpec.HTTP_METHOD_POST)
                .setHttpBody(POST_BODY)
                .setHttpRequestHeaders(spec.httpRequestHeaders + mapOf("User-Agent" to agent))
                .build()
        }

        class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
            override fun createDataSource(): DataSource = Shaped(upstream.createDataSource())
        }
    }
}

/** Counters for the stats under the quality list, reset for every new video. */
object PlaybackStats {
    var droppedFrames by mutableIntStateOf(0)
        internal set
    var stalls by mutableIntStateOf(0)
        internal set

    internal fun reset() {
        droppedFrames = 0
        stalls = 0
    }
}

/**
 * Learns which YouTube client's streams get cut off. A video's streams come from several of
 * YouTube's apps (Android, visionOS, iOS); when one of them is refused mid-video (HTTP 403, often a
 * minute or so in), streams from that client are left out for a few hours wherever another
 * client offers the same thing, so the next videos do not stop the same way. Kept across restarts.
 */
object StreamHealth {
    private const val HOLD_MS = 3 * 60 * 60 * 1000L
    private val clientParam = Regex("[?&]c=([A-Za-z0-9_]+)")
    private val refusedUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences("stream_health", Context.MODE_PRIVATE).also { prefs = it }
        val now = System.currentTimeMillis()
        p.all.forEach { (client, until) -> if (until is Long && until > now) refusedUntil[client] = until }
    }

    fun clientOf(url: String?): String? = url?.let { clientParam.find(it)?.groupValues?.get(1) }

    /** Remembers that this stream's client was refused; returns the client's name. */
    fun markRefused(url: String?): String? {
        val client = clientOf(url) ?: return null
        val until = System.currentTimeMillis() + HOLD_MS
        refusedUntil[client] = until
        prefs?.edit()?.putLong(client, until)?.apply()
        return client
    }

    fun isRefused(url: String?): Boolean {
        val client = clientOf(url) ?: return false
        val until = refusedUntil[client] ?: return false
        return until > System.currentTimeMillis()
    }

    /** The streams without the refused clients, or all of them when nothing else is left. */
    fun <T : org.schabi.newpipe.extractor.stream.Stream> avoidRefused(streams: List<T>): List<T> =
        if (refusedUntil.isEmpty()) streams else streams.filterNot { isRefused(it.content) }.ifEmpty { streams }
}
