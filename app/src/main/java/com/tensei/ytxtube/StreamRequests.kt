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
