package com.tensei.ytxtube

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.source.MediaSource
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.DeliveryType
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Rewinding a live stream all the way to its start (YouTube keeps up to 12 hours).
 *
 * The HLS playlist YouTube gives other players only lists about the last hour. YouTube's own app
 * asks for the live's pieces by their number instead: piece 0 is the start, and every piece is a
 * few seconds long. This does the same: one request tells how many pieces there are now, and a
 * live DASH description built here lets the player ask for any piece by number, so the seek bar
 * reaches back to the start. If anything about it fails, the live plays from the HLS playlist
 * as before.
 */
@OptIn(UnstableApi::class)
object LiveDvr {
    private const val TAG = "LiveDvr"
    private const val SCHEME = "ytxlive"
    private const val WINDOW_SEC = 12 * 60 * 60L
    private const val RETRY_AFTER_FAIL_MS = 30 * 60_000L

    private class Rep(
        val id: String,
        val url: String,
        val mime: String,
        val codecs: String?,
        val bandwidth: Int,
        val width: Int,
        val height: Int,
        val fps: Int,
        val sampleRate: Int,
        val channels: Int,
    )

    private class Plan(val segmentSec: Int, val startMs: Long, val video: List<Rep>, val audio: Rep, head: Long) {
        /** The newest piece YouTube has told us about, and when that number last went up. */
        @Volatile var head: Long = head
        @Volatile var headMovedAt: Long = System.currentTimeMillis()
        /** The live has finished: no new pieces. The recording then plays like a normal video. */
        @Volatile var ended = false

        fun noteHead(n: Long) {
            if (n > head) {
                head = n
                headMovedAt = System.currentTimeMillis()
            }
        }
    }

    private val plans = ConcurrentHashMap<String, Plan>()
    private val failedAt = ConcurrentHashMap<String, Long>()

    private fun failedRecently(id: String): Boolean =
        failedAt[id]?.let { System.currentTimeMillis() - it < RETRY_AFTER_FAIL_MS } == true

    /** Whether this live is (going to be) played from its numbered pieces. */
    fun active(id: String): Boolean = plans.containsKey(id) && !failedRecently(id)

    /** The numbered pieces did not work for this live: play it from the HLS playlist for a while. */
    fun markFailed(id: String) {
        Log.w(TAG, "falling back to the HLS playlist for $id")
        failedAt[id] = System.currentTimeMillis()
        plans.remove(id)
    }

    /** A refused piece newer than the newest one known: the live may just have finished. */
    fun pastHead(id: String?, failed: Uri?): Boolean {
        val p = plans[id ?: return false] ?: return false
        val sq = failed?.getQueryParameter("sq")?.toLongOrNull() ?: return false
        return sq > p.head
    }

    /** How long to wait before asking for that piece again. */
    fun retryDelayMs(id: String?): Long = (plans[id ?: ""]?.segmentSec ?: 5).let { maxOf(2L * it, 6L) * 1000L }

    /** The live this was playing has finished; it carries on as a recording. */
    fun ended(id: String?): Boolean = id != null && plans[id]?.ended == true

    /** Fell back within the last few seconds (the error that caused it is not shown). */
    fun justFellBack(id: String?): Boolean =
        id != null && failedAt[id]?.let { System.currentTimeMillis() - it < 8_000L } == true

    /**
     * Looks up how far the live has got (one small request), on a background thread, right after
     * the live is extracted.
     */
    fun prepare(id: String, info: StreamInfo) {
        if (info.streamType != StreamType.LIVE_STREAM || failedRecently(id)) return
        runCatching {
            val audio = info.audioStreams
                .filter { it.isUrl && it.itagItem != null && it.format == MediaFormat.M4A && it.itagItem!!.targetDurationSec > 0 }
                .maxByOrNull { it.averageBitrate }
                ?: return
            val maxHeight = maxOf(NetworkSpeed.linkHeight(), 480)
            val videos = info.videoOnlyStreams
                .filter {
                    it.isUrl && it.itagItem != null && it.format == MediaFormat.MPEG_4 &&
                        it.itagItem!!.targetDurationSec > 0 && it.itagItem!!.height in 1..maxHeight
                }
                .sortedBy { it.itagItem!!.height }
                .distinctBy { it.itagItem!!.height }
            if (videos.isEmpty()) return
            val segmentSec = audio.itagItem!!.targetDurationSec
            val response = YoutubeDashManifestCreatorsUtils.getInitializationResponse(audio.content, audio.itagItem!!, DeliveryType.LIVE)
            if (response.responseCode() != 200) {
                Log.i(TAG, "$id: the start is not available (HTTP ${response.responseCode()}), using the HLS playlist")
                return
            }
            val head = response.responseHeaders().entries
                .firstOrNull { it.key.equals("X-Head-Seqnum", ignoreCase = true) }
                ?.value?.firstOrNull()?.toLongOrNull()
            if (head == null || head < 2) {
                Log.i(TAG, "$id: no piece count, using the HLS playlist")
                return
            }
            // Piece N started N pieces after the start. One piece of margin, so the newest piece
            // is only asked for once it is surely there.
            val start = System.currentTimeMillis() - head * segmentSec * 1000L
            plans[id] = Plan(segmentSec, start, videos.map(::videoRep), audioRep(audio), head)
            Log.i(TAG, "$id: ${head + 1} pieces of ${segmentSec}s, rewind to the start enabled (${videos.size} qualities)")
        }.onFailure { Log.w(TAG, "$id: ${it.message}") }
    }

    private fun videoRep(s: VideoStream): Rep {
        val item = s.itagItem!!
        return Rep(
            id = "v" + item.id, url = s.content, mime = "video/mp4", codecs = item.codec,
            bandwidth = item.bitrate.coerceAtLeast(1), width = item.width, height = item.height,
            fps = item.fps, sampleRate = 0, channels = 0,
        )
    }

    private fun audioRep(s: AudioStream): Rep {
        val item = s.itagItem!!
        return Rep(
            id = "a" + item.id, url = s.content, mime = "audio/mp4", codecs = item.codec,
            bandwidth = item.bitrate.coerceAtLeast(1), width = 0, height = 0, fps = 0,
            sampleRate = item.sampleRate, channels = item.audioChannels.coerceAtLeast(1),
        )
    }

    /** The player for this live, or null to use the HLS playlist. Called where sources are built. */
    fun source(mediaItem: MediaItem, segments: DataSource.Factory): MediaSource? {
        val id = mediaItem.mediaId
        if (!active(id)) return null
        val item = MediaItem.Builder()
            .setUri(Uri.parse("$SCHEME://dvr/$id"))
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .setMediaId(id)
            .setMediaMetadata(mediaItem.mediaMetadata)
            .build()
        Log.i(TAG, "media source $id: live from numbered pieces, rewind to the start" + if (ended(id)) " (finished: recording)" else "")
        val watched = DataSource.Factory { SegmentSource(segments.createDataSource(), id) }
        return DashMediaSource.Factory(DefaultDashChunkSource.Factory(watched), DataSource.Factory { ManifestSource() })
            .createMediaSource(item)
    }

    private fun iso(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ms))

    private fun esc(s: String?): String = (s ?: "")
        .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    /**
     * The DASH description: every piece by number. While the live runs it grows with the clock;
     * once the live has finished it is a fixed recording from the first piece to the last.
     */
    private fun mpd(id: String): String? {
        val p = plans[id] ?: return null
        val now = System.currentTimeMillis()
        val d = p.segmentSec
        val delay = maxOf(3 * d, 6)
        fun template(rep: Rep) =
            "<SegmentTemplate media=\"${esc(rep.url)}&amp;sq=\$Number\$\" startNumber=\"0\" timescale=\"1000\" duration=\"${d * 1000}\"/>"
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        if (p.ended) {
            sb.append(
                "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" type=\"static\" " +
                    "mediaPresentationDuration=\"PT${(p.head + 1) * d}S\" minBufferTime=\"PT${d}S\">",
            )
        } else sb.append(
            "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-live:2011\" type=\"dynamic\" " +
                "availabilityStartTime=\"${iso(p.startMs)}\" publishTime=\"${iso(now)}\" minimumUpdatePeriod=\"PT${maxOf(2 * d, 6)}S\" " +
                "timeShiftBufferDepth=\"PT${WINDOW_SEC}S\" suggestedPresentationDelay=\"PT${delay}S\" minBufferTime=\"PT${d}S\">",
        )
        sb.append("<Period id=\"0\" start=\"PT0S\">")
        sb.append("<AdaptationSet id=\"0\" contentType=\"video\" mimeType=\"video/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\">")
        p.video.forEach { r ->
            sb.append(
                "<Representation id=\"${r.id}\" codecs=\"${esc(r.codecs)}\" bandwidth=\"${r.bandwidth}\" " +
                    "width=\"${r.width}\" height=\"${r.height}\"" + (if (r.fps > 0) " frameRate=\"${r.fps}\"" else "") + ">",
            )
            sb.append(template(r))
            sb.append("</Representation>")
        }
        sb.append("</AdaptationSet>")
        val a = p.audio
        sb.append("<AdaptationSet id=\"1\" contentType=\"audio\" mimeType=\"audio/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\">")
        sb.append(
            "<Representation id=\"${a.id}\" codecs=\"${esc(a.codecs)}\" bandwidth=\"${a.bandwidth}\"" +
                (if (a.sampleRate > 0) " audioSamplingRate=\"${a.sampleRate}\"" else "") + ">",
        )
        sb.append("<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"${a.channels}\"/>")
        sb.append(template(a))
        sb.append("</Representation></AdaptationSet>")
        sb.append("</Period>")
        // The player's clock is the phone's clock, the same one the start time came from.
        if (!p.ended) sb.append("<UTCTiming schemeIdUri=\"urn:mpeg:dash:utc:direct:2014\" value=\"${iso(now)}\"/>")
        sb.append("</MPD>")
        return sb.toString()
    }

    /**
     * The pieces themselves, watched on the way: each answer says how far the live has got. A
     * piece past the newest one that is refused, while that number has not moved for a while,
     * means the live has finished.
     */
    private class SegmentSource(private val upstream: DataSource, private val id: String) : DataSource by upstream {
        override fun open(dataSpec: DataSpec): Long {
            val sq = dataSpec.uri.getQueryParameter("sq")?.toLongOrNull()
            try {
                val length = upstream.open(dataSpec)
                upstream.responseHeaders.entries
                    .firstOrNull { it.key.equals("X-Head-Seqnum", ignoreCase = true) }
                    ?.value?.firstOrNull()?.toLongOrNull()
                    ?.let { plans[id]?.noteHead(it) }
                return length
            } catch (e: androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                val p = plans[id]
                if (p != null && sq != null && sq > p.head && !p.ended) {
                    val still = System.currentTimeMillis() - p.headMovedAt
                    if (still > maxOf(2L * p.segmentSec, 10L) * 1000L) {
                        Log.i(TAG, "$id: the live has finished at piece ${p.head}; playing on as a recording")
                        p.ended = true
                    }
                }
                throw e
            }
        }
    }

    /** Serves the description above to the player, made fresh each time it is asked for. */
    private class ManifestSource : BaseDataSource(false) {
        private var data: ByteArray? = null
        private var position = 0
        private var uri: Uri? = null

        override fun open(dataSpec: DataSpec): Long {
            uri = dataSpec.uri
            transferInitializing(dataSpec)
            val id = dataSpec.uri.lastPathSegment ?: throw IOException("no live id")
            val bytes = (mpd(id) ?: throw IOException("no live plan for $id")).toByteArray()
            data = bytes
            position = dataSpec.position.toInt().coerceIn(0, bytes.size)
            transferStarted(dataSpec)
            return (bytes.size - position).toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val d = data ?: return C.RESULT_END_OF_INPUT
            val left = d.size - position
            if (left <= 0) return C.RESULT_END_OF_INPUT
            val n = minOf(length, left)
            System.arraycopy(d, position, buffer, offset, n)
            position += n
            bytesTransferred(n)
            return n
        }

        override fun getUri(): Uri? = uri

        override fun close() {
            if (data != null) {
                data = null
                transferEnded()
            }
            uri = null
        }
    }
}
