package com.tensei.ytxtube

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.AdaptationSet
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.Stream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors





@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val resolver = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))

    companion object {
        /** The player while the service runs, so a changed setting can reach it straight away. */
        @Volatile private var live: ExoPlayer? = null
        private var equalizer: android.media.audiofx.Equalizer? = null
        private var bass: android.media.audiofx.BassBoost? = null

        /** The phone's equalizer presets (names), known once something has played. */
        var eqPresetNames: List<String> = emptyList()
            private set
        /** The equalizer's bands (centre frequency in Hz) and level range in millibels. */
        var eqBandFreqs: List<Int> = emptyList()
            private set
        var eqLevelRange: IntRange = -1500..1500
            private set
        private var dynamics: Any? = null

        /** Applies the settings a running player can change on the fly (main thread). */
        fun applyLivePrefs() {
            val player = live ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post { applyTo(player) }
        }

        private fun applyTo(player: ExoPlayer) {
            runCatching { player.skipSilenceEnabled = PlaybackPrefs.skipSilence }
            runCatching { player.setAudioAttributes(player.audioAttributes, PlaybackPrefs.pauseForOtherAudio) }
            val session = player.audioSessionId
            if (session != 0 && session != C.AUDIO_SESSION_ID_UNSET) {
                runCatching {
                    val eq = equalizer ?: android.media.audiofx.Equalizer(0, session).also { e ->
                        equalizer = e
                        eqPresetNames = (0 until e.numberOfPresets).map { i -> e.getPresetName(i.toShort()) }
                        eqBandFreqs = (0 until e.numberOfBands).map { b -> e.getCenterFreq(b.toShort()) / 1000 }
                        val range = e.bandLevelRange
                        if (range.size >= 2) eqLevelRange = range[0].toInt()..range[1].toInt()
                    }
                    val preset = PlaybackPrefs.eqPreset
                    if (preset == PlaybackPrefs.EQ_CUSTOM) {
                        // The hand-made curve from Settings, one level per band.
                        for (b in 0 until eq.numberOfBands) {
                            val level = (PlaybackPrefs.eqBandLevels.getOrNull(b) ?: 0).coerceIn(eqLevelRange)
                            eq.setBandLevel(b.toShort(), level.toShort())
                        }
                        eq.enabled = true
                    } else if (preset in 0 until eq.numberOfPresets) {
                        eq.usePreset(preset.toShort())
                        eq.enabled = true
                    } else {
                        eq.enabled = false
                    }
                }
                runCatching {
                    val b = bass ?: android.media.audiofx.BassBoost(0, session).also { bass = it }
                    val strength = PlaybackPrefs.bassBoost
                    if (strength > 0 && b.strengthSupported) {
                        b.setStrength(strength.toShort())
                        b.enabled = true
                    } else {
                        b.enabled = false
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { applyDynamics(session) }
            }
        }

        /**
         * Stable volume and the volume booster, in one dynamics processor: a gentle compressor
         * with make-up gain evens out loud and quiet videos, the input gain boosts, and a limiter
         * at the end keeps a boosted or evened-out signal from clipping.
         */
        @androidx.annotation.RequiresApi(28)
        private fun applyDynamics(session: Int) {
            val stable = PlaybackPrefs.stableVolume
            val boost = PlaybackPrefs.volumeBoost
            var dp = dynamics as? android.media.audiofx.DynamicsProcessing
            if (!stable && boost <= 100) {
                dp?.enabled = false
                return
            }
            if (dp == null) {
                val config = android.media.audiofx.DynamicsProcessing.Config.Builder(
                    android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    false, 0,
                    true, 1,
                    false, 0,
                    true,
                ).build()
                dp = android.media.audiofx.DynamicsProcessing(0, session, config)
                dynamics = dp
            }
            dp.setInputGainAllChannelsTo(20f * kotlin.math.log10(boost / 100f))
            val band = if (stable) {
                android.media.audiofx.DynamicsProcessing.MbcBand(
                    true, 20_000f, 15f, 350f, 3f, -30f, 8f, -90f, 1f, 0f, 9f,
                )
            } else {
                android.media.audiofx.DynamicsProcessing.MbcBand(
                    true, 20_000f, 1f, 1f, 1f, 0f, 0f, -90f, 1f, 0f, 0f,
                )
            }
            dp.setMbcBandAllChannelsTo(0, band)
            dp.setLimiterAllChannelsTo(
                android.media.audiofx.DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f),
            )
            dp.enabled = true
        }

        private fun releaseEffects() {
            runCatching { equalizer?.release() }
            runCatching { bass?.release() }
            runCatching { (dynamics as? android.media.audiofx.AudioEffect)?.release() }
            equalizer = null
            bass = null
            dynamics = null
        }
    }

    override fun onCreate() {
        super.onCreate()

        val sources = YouTubeMediaSourceFactory(this)
        val player = ExoPlayer.Builder(this, SyncedRenderersFactory(this))
            .setMediaSourceFactory(sources)
            .setBandwidthMeter(PlaybackBandwidth.get())


            .setTrackSelector(
                DefaultTrackSelector(
                    this,
                    AdaptiveTrackSelection.Factory(
 2_000,
 25_000,
 25_000,
 0.75f,
                    ),
                ).apply {



                    setParameters(
                        buildUponParameters()
                            .setAllowVideoMixedDecoderSupportAdaptiveness(true)
                            .setAllowVideoNonSeamlessAdaptiveness(true),
                    )
                },
            )


            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Start after a quarter second (quick opens), then keep loading steadily
                    // towards a 50 s runway instead of letting it drain to 2.5 s before
                    // fetching again. After a stall, wait for 2 s of video rather than 0.75 s,
                    // so a slow stream pauses once instead of stuttering every second while
                    // the quality steps down.
                    .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, 64 * 1024))
                    .setBufferDurationsMs(30_000, 50_000, 250, 2_000)
                    .setBackBuffer(10_000, false)
                    // A memory ceiling so a 4K stream cannot fill the app's memory: about 50 s
                    // of 1080p60 fits, 4K stops a little earlier.
                    .setTargetBufferBytes(96 * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
 true,
            )
            .setHandleAudioBecomingNoisy(true)


            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(PlaybackPrefs.seekStepMs)
            .setSeekForwardIncrementMs(PlaybackPrefs.seekStepMs)
            .build()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        player.setVideoFrameMetadataListener { _, releaseTimeNs, _, _ -> AvSync.onFrameReleased(releaseTimeNs) }
        player.addListener(SponsorSkipper(player))
        player.addListener(SpeedKeeper(player))
        player.skipSilenceEnabled = PlaybackPrefs.skipSilence
        live = player
        applyTo(player)


        if (AudioOnly.enabled) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        }

        player.addListener(StreamRecoverer(player, sources, resolver))
        player.addAnalyticsListener(StatsCounter(player))
        player.addListener(CrossFader(player))
        player.addListener(WatchStatsTimer(player))
        resumeSaver = ResumeSaver(player).also(player::addListener)
        watchTime = WatchTimeReporter(player).also(player::addListener)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Settings, Playback: music can keep going after the app is swiped away.
        if (PlaybackPrefs.keepPlayingOnSwipe && live?.isPlaying == true) return
        pauseAllPlayersAndStopSelf()
    }

    private var resumeSaver: ResumeSaver? = null
    private var watchTime: WatchTimeReporter? = null

    override fun onDestroy() {
        live = null
        releaseEffects()
        resumeSaver?.saveNow()
        watchTime?.finish()
        session?.run {
            player.release()
            release()
        }
        session = null
        resolver.shutdown()
        super.onDestroy()
    }

    




    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = resolver.submit<MutableList<MediaItem>> {
            mediaItems.map { item ->
                val local = item.requestMetadata.mediaUri?.takeIf { it.scheme == "file" }
                    ?: item.mediaMetadata.extras?.getString("local_uri")?.let(Uri::parse)
                if (local != null) {

                    item.buildUpon()
                        .setUri(local)
                        .setMediaMetadata(
                            item.mediaMetadata.buildUpon()
                                .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                                .build(),
                        )
                        .build()
                } else {
                    // Premium 256 kbps audio is looked for alongside the normal extraction.
                    val premium = PremiumAudio.prefetch(item.mediaId)
                    val info = StreamCache.getOrLoad(item.mediaId)
                    runCatching { premium?.get(3, java.util.concurrent.TimeUnit.SECONDS) }
                    item.buildUpon()
                        .setUri(YouTube.watchUrl(item.mediaId))
                        .setMediaMetadata(
                            item.mediaMetadata.buildUpon()
                                .setTitle(item.mediaMetadata.title ?: info.name)
                                .setArtist(item.mediaMetadata.artist ?: info.uploaderName)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                                .build(),
                        )
                        .build()
                }
            }.toMutableList()
        }
    }
}


/** Counts the time spent watching, per day and per channel, for Your watch stats. */
private class WatchStatsTimer(private val player: ExoPlayer) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var since = 0L

    private val tick = object : Runnable {
        override fun run() {
            flush()
            if (player.isPlaying) main.postDelayed(this, 15_000)
        }
    }

    private fun flush() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (since > 0L) {
            val seconds = (now - since) / 1000L
            if (seconds > 0) WatchStats.add(seconds, player.mediaMetadata.artist?.toString())
        }
        since = if (player.isPlaying) now else 0L
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        main.removeCallbacks(tick)
        if (isPlaying) {
            since = android.os.SystemClock.elapsedRealtime()
            main.postDelayed(tick, 15_000)
        } else {
            flush()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Time so far belongs to the item that was playing; start counting afresh.
        if (since > 0L) since = android.os.SystemClock.elapsedRealtime()
    }
}


/**
 * Crossfade in the music player: the last seconds of a song fade out and the next one fades in
 * (Settings, Music, Crossfade). Videos, loops and songs you skip to by hand are left alone.
 */
private class CrossFader(private val player: ExoPlayer) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var fadingIn = false
    private var applied = 1f

    private val tick = object : Runnable {
        override fun run() {
            update()
            if (player.isPlaying && active()) main.postDelayed(this, 80)
        }
    }

    private fun active(): Boolean =
        PlaybackPrefs.crossfadeSec > 0 && NowPlayingMode.music && player.repeatMode != Player.REPEAT_MODE_ONE

    private fun setVolume(v: Float) {
        if (kotlin.math.abs(v - applied) < 0.01f) return
        applied = v
        player.volume = v
    }

    private fun update() {
        if (!active()) {
            fadingIn = false
            setVolume(1f)
            return
        }
        val fadeMs = PlaybackPrefs.crossfadeSec * 1000f
        val position = player.currentPosition
        val duration = player.duration
        val fadeOut = if (duration > 0 && duration != C.TIME_UNSET && duration > fadeMs * 3) {
            ((duration - position) / fadeMs).coerceIn(0f, 1f)
        } else 1f
        val fadeIn = if (fadingIn) (position / fadeMs).coerceIn(0f, 1f) else 1f
        if (fadingIn && position >= fadeMs) fadingIn = false
        // A slight curve so the fade sounds even to the ear.
        val v = minOf(fadeOut, fadeIn)
        setVolume(v * v * (3f - 2f * v))
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        main.removeCallbacks(tick)
        if (isPlaying) main.post(tick)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        fadingIn = active() && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        if (!fadingIn) setVolume(1f) else setVolume(0f)
        main.removeCallbacks(tick)
        if (player.isPlaying || fadingIn) main.post(tick)
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            fadingIn = false
            update()
        }
    }
}


/** Dropped frames and stalls of the video playing now, for the stats under the quality list. */
@OptIn(UnstableApi::class)
private class StatsCounter(private val player: ExoPlayer) : androidx.media3.exoplayer.analytics.AnalyticsListener {
    private var seeking = false

    override fun onMediaItemTransition(
        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
        mediaItem: MediaItem?,
        reason: Int,
    ) = PlaybackStats.reset()

    override fun onDroppedVideoFrames(
        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long,
    ) {
        PlaybackStats.droppedFrames += droppedFrames
    }

    override fun onPositionDiscontinuity(
        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) seeking = true
    }

    override fun onPlaybackStateChanged(
        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
        state: Int,
    ) {
        if (state == Player.STATE_BUFFERING && player.playWhenReady && !seeking && player.currentPosition > 1_000L) {
            PlaybackStats.stalls++
        }
        if (state == Player.STATE_READY) seeking = false
    }
}


object StreamRecovery {

    var recovering by mutableStateOf<String?>(null)
        internal set


    var failed by mutableStateOf<Pair<String, String>?>(null)
        internal set

    @Volatile private var handledId: String? = null
    @Volatile private var handledAt = 0L

    /** The service took care of a refused stream for this video; the screen shows no error for it. */
    internal fun markHandled(id: String) {
        handledId = id
        handledAt = android.os.SystemClock.elapsedRealtime()
    }

    fun recentlyHandled(id: String?): Boolean =
        id != null && id == handledId && android.os.SystemClock.elapsedRealtime() - handledAt < 20_000L
}








@OptIn(UnstableApi::class)
private class StreamRecoverer(
    private val player: ExoPlayer,
    private val sources: MediaSource.Factory,
    private val resolver: java.util.concurrent.Executor,
) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var lastId: String? = null
    private var lastPositionMs = 0L
    private var attempts = 0

    override fun onPlayerError(error: PlaybackException) {
        val current = player.currentMediaItem
        // Fell behind what a live still keeps: jump back to the live edge.
        if (current != null && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            main.post {
                if (player.currentMediaItem?.mediaId != current.mediaId) return@post
                player.seekToDefaultPosition()
                player.prepare()
            }
            return
        }
        // The live finished while being watched: carry on as a recording from the same spot
        // (no new extraction; YouTube calls a just-finished live unavailable for a while).
        if (current != null && LiveDvr.ended(current.mediaId)) {
            val id = current.mediaId
            val position = player.currentPosition
            StreamRecovery.markHandled(id)
            main.post {
                if (player.currentMediaItem?.mediaId != id) return@post
                val index = player.currentMediaItemIndex
                player.addMediaSource(index + 1, sources.createMediaSource(current))
                player.removeMediaItem(index)
                player.seekTo(index, position)
                player.prepare()
            }
            return
        }
        // A piece past the newest one was refused: the live may just have finished, or the piece
        // is not there yet. Ask again a little later from the same spot (no new extraction); if
        // the live has finished by then, it carries on as a recording (above).
        val refused = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.dataSpec?.uri
        if (current != null && LiveDvr.active(current.mediaId) &&
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && LiveDvr.pastHead(current.mediaId, refused)
        ) {
            val id = current.mediaId
            val position = player.currentPosition
            StreamRecovery.markHandled(id)
            main.postDelayed({
                if (player.currentMediaItem?.mediaId != id || player.playerError == null) return@postDelayed
                val index = player.currentMediaItemIndex
                player.addMediaSource(index + 1, sources.createMediaSource(current))
                player.removeMediaItem(index)
                player.seekTo(index, position)
                player.prepare()
            }, LiveDvr.retryDelayMs(id))
            return
        }
        // A live played from its numbered pieces (rewind to the start) that fails for any other
        // reason than an expired link: play it from the HLS playlist instead.
        if (current != null && LiveDvr.active(current.mediaId) &&
            (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS || attempts >= 1 && current.mediaId == lastId)
        ) {
            val id = current.mediaId
            LiveDvr.markFailed(id)
            StreamRecovery.markHandled(id)
            main.post {
                if (player.currentMediaItem?.mediaId != id) return@post
                val index = player.currentMediaItemIndex
                player.addMediaSource(index + 1, sources.createMediaSource(current))
                player.removeMediaItem(index)
                player.seekToDefaultPosition(index)
                player.prepare()
            }
            return
        }
        if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return
        val item = player.currentMediaItem ?: return

        if (item.localConfiguration?.uri?.scheme == "file") return
        val id = item.mediaId
        val position = player.currentPosition
        val failedUrl = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
            ?.dataSpec?.uri?.toString()
        val client = StreamHealth.clientOf(failedUrl)
        val premiumFailed = if (failedUrl != null) client == "WEB_REMIX" else PremiumAudio.get(id) != null
        Log.w("YTXTube", "stream refused for $id at ${position / 1000}s (client ${client ?: "?"})")
        if (premiumFailed) {
            StreamRecovery.markHandled(id)
            // The Premium 256 kbps stream was cut off. The normal stream is still good, so carry
            // straight on with it from the same spot, without a new extraction, and leave Premium
            // audio alone for a while so the next songs do not stall the same way.
            Log.w("YTXTube", "premium audio for $id refused at ${position / 1000}s; continuing with the normal stream")
            PremiumAudio.markFailed(id)
            lastId = null
            StreamRecovery.failed = null
            StreamRecovery.recovering = id
            main.post {
                StreamRecovery.recovering = null
                if (player.currentMediaItem?.mediaId != id || player.playerError == null) return@post
                resume(item, position)
            }
            return
        }
        // Retried quietly up to three times in a row; a stream that plays on for a while after
        // a retry starts the count again, so a long video can recover as often as it needs.
        attempts = if (id == lastId && position < lastPositionMs + PROGRESS_MS) attempts + 1 else 1
        if (attempts > MAX_ATTEMPTS) {
            Log.w("YTXTube", "stream refused $attempts times in a row for $id; giving up")
            return
        }
        // Streams from the client YouTube just cut off are avoided from now on where another
        // client has the same quality (the new extraction below already leaves them out).
        StreamHealth.markRefused(failedUrl)
        StreamRecovery.markHandled(id)
        lastId = id
        lastPositionMs = position
        Log.i("YTXTube", "re-extracting $id without ${client ?: "the refused stream"} and resuming at ${position / 1000}s")
        StreamRecovery.failed = null
        StreamRecovery.recovering = id
        StreamCache.invalidate(id)
        resolver.execute {
            // The refused stream may carry a stale token: start the retry from a fresh one.
            runCatching { YtxPoTokenProvider.current?.reset() }
            val result = runCatching { StreamCache.getOrLoad(id) }
            main.post {
                StreamRecovery.recovering = null

                if (player.currentMediaItem?.mediaId != id || player.playerError == null) return@post
                result
                    .onSuccess { resume(item, position) }
                    .onFailure {
                        Log.w("YTXTube", "re-extracting $id failed", it)
                        StreamRecovery.failed = id to YouTube.friendlyError(it)
                    }
            }
        }
    }

    



    private fun resume(item: MediaItem, positionMs: Long) {
        val index = player.currentMediaItemIndex
        player.addMediaSource(index + 1, sources.createMediaSource(item))
        player.removeMediaItem(index)
        player.seekTo(index, positionMs)
        player.prepare()
    }

    private companion object {
        // A stream that played this much after the last refusal counts as a new problem, so the
        // video is silently recovered again; only one that dies straight away gives up.
        const val PROGRESS_MS = 10_000L
        const val MAX_ATTEMPTS = 3
    }
}









@OptIn(UnstableApi::class)
private class WatchTimeReporter(private val player: ExoPlayer) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var videoId: String? = null
    private var session: InnerTube.WatchSession? = null
    private var startedAt = 0L

    private val starts = ArrayList<Double>()
    private val ends = ArrayList<Double>()

    private var stretchFrom: Long? = null
    private var pings = 0
    private val tick = Runnable { onTick() }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val id = mediaItem?.mediaId?.ifBlank { null }

        if (id == videoId) return
        finish()
        videoId = id
        // Incognito: YouTube is not told what is watched, so nothing goes into the history.
        if (id == null || !Account.signedIn || Incognito.enabled) return
        startedAt = android.os.SystemClock.elapsedRealtime()
        if (player.isPlaying) startStretch()
        io.execute {
            val started = runCatching { InnerTube.startWatch(id) }
                .onFailure { Log.w("YTXTube", "watch start for $id", it) }.getOrNull()
            main.post { if (videoId == id) session = started }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (videoId == null || !Account.signedIn || Incognito.enabled) return
        if (isPlaying) {
            startStretch()
        } else {
            endStretch(player.currentPosition)
            send(playing = false, final = false)
        }
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (stretchFrom == null) return
        endStretch(oldPosition.positionMs)

        if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex && player.isPlaying) stretchFrom = newPosition.positionMs
    }


    fun finish() {
        main.removeCallbacks(tick)
        if (stretchFrom != null && player.currentMediaItem?.mediaId == videoId) endStretch(player.currentPosition)
        stretchFrom = null
        send(playing = false, final = true)
        videoId = null
        session = null
        starts.clear(); ends.clear()
        pings = 0
    }

    private fun startStretch() {
        stretchFrom = player.currentPosition
        schedule()
    }

    private fun endStretch(atMs: Long) {
        val from = stretchFrom ?: return
        stretchFrom = null
        main.removeCallbacks(tick)
        if (atMs > from + 250) { starts += from / 1000.0; ends += atMs / 1000.0 }
    }

    private fun schedule() {
        main.removeCallbacks(tick)
        main.postDelayed(tick, PING_DELAYS_MS.getOrElse(pings) { PING_DELAYS_MS.last() })
    }

    private fun onTick() {
        if (!player.isPlaying) return
        val now = player.currentPosition
        endStretch(now)
        stretchFrom = now
        send(playing = true, final = false)
        schedule()
    }


    private fun send(playing: Boolean, final: Boolean) {
        val s = session ?: return
        if (starts.isEmpty() && !final) return
        pings++
        val position = player.currentPosition / 1000.0
        val elapsed = (android.os.SystemClock.elapsedRealtime() - startedAt) / 1000.0
        val st = starts.toList()
        val et = ends.toList()
        starts.clear(); ends.clear()
        io.execute {
            runCatching { InnerTube.pingWatchtime(s, position, st, et, playing, elapsed, final) }
                .onFailure { Log.w("YTXTube", "watch time for ${s.videoId}", it) }
        }
    }

    private companion object {
        val PING_DELAYS_MS = longArrayOf(10_000, 10_000, 10_000, 40_000).toList()
    }
}






private class SpeedKeeper(private val player: ExoPlayer) : Player.Listener {
    private var videoId: String? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val id = mediaItem?.mediaId?.ifBlank { null } ?: return
        if (id == videoId) return
        videoId = id
        val channel = mediaItem?.mediaMetadata?.artist?.toString()
        player.setPlaybackSpeed(ChannelSpeeds.get(channel) ?: PlaybackSpeed.forNewVideo)
    }
}









@OptIn(UnstableApi::class)
private class SponsorSkipper(private val player: ExoPlayer) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val id = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        io.execute {
            val segments = SponsorBlock.fetch(id)
            main.post {
                SponsorBlock.forUi[id] = segments

                if (player.currentMediaItem?.mediaId != id) return@post
                val index = player.currentMediaItemIndex
                segments.forEach { segment ->
                    player.createMessage { _, _ -> skip(id, segment) }
                        .setLooper(Looper.getMainLooper())
                        .setPosition(index, segment.startMs)
                        .send()
                }
            }
        }
    }

    private fun skip(videoId: String, segment: SponsorBlock.Segment) {

        if (!SponsorBlock.enabled || segment.action != SponsorBlock.Action.Auto) return
        if (player.currentMediaItem?.mediaId != videoId) return
        if (player.currentPosition > segment.endMs - 500) return
        Log.i("YTXTube", "SponsorBlock: skipping ${segment.category.key} ${segment.startMs}-${segment.endMs} in $videoId")
        ramp(from = 1f, to = 0f, durationMs = 300) {
            player.seekTo(segment.endMs)
            SponsorBlock.lastSkip = SponsorBlock.Skip(videoId, segment)
            ramp(from = 0f, to = 1f, durationMs = 450) {}
        }
    }

    private fun ramp(from: Float, to: Float, durationMs: Long, then: () -> Unit) {
        val start = android.os.SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                val t = ((android.os.SystemClock.uptimeMillis() - start).toFloat() / durationMs).coerceIn(0f, 1f)
                player.volume = from + (to - from) * t
                if (t < 1f) main.postDelayed(this, 16) else then()
            }
        }
        main.post(step)
    }
}







@OptIn(UnstableApi::class)
private class YouTubeMediaSourceFactory(context: Context) : MediaSource.Factory {
    private val dataSourceFactory: DataSource.Factory = StreamRequests.factory(context)
    private val fallback = DefaultMediaSourceFactory(dataSourceFactory)

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory =
        apply { fallback.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory =
        apply { fallback.setLoadErrorHandlingPolicy(policy) }

    override fun getSupportedTypes(): IntArray = fallback.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        buildSource(mediaItem)

    private fun buildSource(mediaItem: MediaItem): MediaSource {

        if (mediaItem.localConfiguration?.uri?.scheme == "file") {
            return fallback.createMediaSource(mediaItem)
        }
        val info = StreamCache.get(mediaItem.mediaId) ?: return fallback.createMediaSource(mediaItem)
        val isLive = info.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
        val hlsUrl = info.hlsUrl?.ifBlank { null }
        // HLS is a live fallback only. A VOD HLS manifest carries just YouTube's default audio
        // track, so using it for normal videos silently ignored both the picked language and the
        // picked quality. VOD goes through the generated DASH/progressive path below.
        // A live with rewind to its start, from its numbered pieces (HLS below if that fails).
        if (isLive) LiveDvr.source(mediaItem, dataSourceFactory)?.let { return it }
        if (isLive && !hlsUrl.isNullOrBlank()) {
            Log.i("YTXTube", "media source ${mediaItem.mediaId}: playing live HLS stream from $hlsUrl")
            return HlsMediaSource.Factory(dataSourceFactory).createMediaSource(
                MediaItem.Builder()
                    .setUri(hlsUrl)
                    .setMediaId(mediaItem.mediaId)
                    .setMediaMetadata(mediaItem.mediaMetadata)
                    .build(),
            )
        }
        val picked = StreamSelection.pick(info)
        val premium = PremiumAudio.get(mediaItem.mediaId)
        fun audioSource(fallback: Stream): MediaSource =
            if (premium != null) {
                Log.i("YTXTube", "media source ${mediaItem.mediaId}: premium audio itag ${premium.itag}")
                // Loaded in short ranges through a DASH description, like the normal streams:
                // one long open request is what YouTube cuts off after a couple of minutes.
                val dash = premium.itagItem?.let { itagItem ->
                    runCatching {
                        val xml = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(premium.url, itagItem, info.duration)
                        val manifest = DashManifestParser().parse(Uri.parse(premium.url), ByteArrayInputStream(xml.toByteArray()))
                        DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest, mediaItem)
                    }.onFailure { Log.w("YTXTube", "premium audio manifest failed for ${mediaItem.mediaId}: ${it.message}") }
                        .getOrNull()
                }
                dash ?: ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem.buildUpon().setUri(premium.url).build())
            } else {
                source(fallback, info.duration, mediaItem)
            }

        // Nothing separable or muxed was extracted: HLS is still better than a black screen.
        if (picked.video == null && picked.muxed == null && !hlsUrl.isNullOrBlank()) {
            Log.i("YTXTube", "media source ${mediaItem.mediaId}: no progressive streams, falling back to HLS")
            return HlsMediaSource.Factory(dataSourceFactory).createMediaSource(
                MediaItem.Builder()
                    .setUri(hlsUrl)
                    .setMediaId(mediaItem.mediaId)
                    .setMediaMetadata(mediaItem.mediaMetadata)
                    .build(),
            )
        }

        if (QualityPreference.height == null && picked.audio != null) {
            val adaptive = adaptiveVideo(info, mediaItem)
            if (adaptive != null) {
                Log.i("YTXTube", "media source ${mediaItem.mediaId}: adaptive video up to ${NetworkSpeed.linkHeight()}p + audio itag ${picked.audio.itag}")
                return MergingMediaSource(true, true, adaptive, audioSource(picked.audio))
            }
        }
        Log.i(
            "YTXTube",
            "media source ${mediaItem.mediaId}: " + if (picked.usesSeparate) "video itag ${picked.video?.itag} (${picked.video?.height}p) + audio itag ${picked.audio?.itag}"
            else "combined fallback itag ${picked.muxed?.itag} (${picked.muxed?.height}p); video=${picked.video != null} audio=${picked.audio != null}; " +
                "audio offered: " + info.audioStreams.joinToString { "${it.itag}/${it.format}/${it.deliveryMethod}/url=${it.isUrl}/${it.audioTrackType}" },
        )
        val video = picked.video
        val audio = picked.audio
        return when {
            video != null && audio != null -> MergingMediaSource(
 true,
 true,
                source(video, info.duration, mediaItem),
                audioSource(audio),
            )
            picked.muxed != null -> source(picked.muxed, info.duration, mediaItem)


            else -> fallback.createMediaSource(mediaItem)
        }
    }

    




    private fun adaptiveVideo(info: org.schabi.newpipe.extractor.stream.StreamInfo, mediaItem: MediaItem): MediaSource? {
        val durationSec = info.duration.takeIf { it > 0 } ?: return null
        // Build the ladder up to what the link itself could carry and let ExoPlayer's
        // bandwidth meter choose the starting rung. It starts conservatively and climbs
        // as real throughput is measured, so fast connections still reach high quality.
        val maxTier = NetworkSpeed.linkHeight()
        val ladder = StreamSelection.autoLadder(info, maxTier)
        val manifests = ladder.mapNotNull { stream ->
            runCatching {
                val xml = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, stream.itagItem!!, durationSec)
                DashManifestParser().parse(Uri.parse(stream.content), ByteArrayInputStream(xml.toByteArray()))
            }.getOrNull()
        }
        if (manifests.size < 2) return null
        val representations = manifests.map { it.getPeriod(0).adaptationSets[0].representations[0] }
        val base = manifests.first()
        val manifest = DashManifest(
            base.availabilityStartTimeMs, base.durationMs, base.minBufferTimeMs, false,
            C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, null, null, null, null,
            listOf(Period("0", 0, listOf(AdaptationSet(0, C.TRACK_TYPE_VIDEO, representations, emptyList(), emptyList(), emptyList())))),
        )
        return DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest, mediaItem)
    }

    private fun source(stream: Stream, durationSec: Long, mediaItem: MediaItem): MediaSource {
        val url = stream.content
        val itag = stream.itagItem
        if (itag != null && durationSec > 0) {
            val dash = runCatching {
                val xml = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(url, itag, durationSec)
                val manifest = DashManifestParser().parse(Uri.parse(url), ByteArrayInputStream(xml.toByteArray()))
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest, mediaItem)
            }.getOrNull()
            if (dash != null) return dash
        }

        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem.buildUpon().setUri(url).build())
    }
}
