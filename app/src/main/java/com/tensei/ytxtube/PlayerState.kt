package com.tensei.ytxtube

import android.content.ComponentName
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext





@Stable
class PlayerState(private val scope: CoroutineScope) {
    var controller by mutableStateOf<MediaController?>(null)
        internal set


    var current by mutableStateOf<VideoItem?>(null)
        private set
    var details by mutableStateOf<VideoDetails?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    var speed by mutableFloatStateOf(1f)
        private set
    var loopEnabled by mutableStateOf(false)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set
    var isMusicMode by mutableStateOf(false)
    var autoPlayEnabled by mutableStateOf(PlaybackPrefs.autoplay)
        private set
    /** What the audio track really is right now, for example "OPUS · 141 KBPS". */
    var playingAudio by mutableStateOf<String?>(null)
        private set
    /** Songs played before this one in music mode, newest last, so Previous can go back. */
    private val musicHistory = ArrayList<VideoItem>()
    var videoZoom by mutableFloatStateOf(1f)
    var zoomToFill by mutableStateOf(false)

    fun resetZoom() { videoZoom = 1f; zoomToFill = false }

    fun toggleZoomToFill() {
        zoomToFill = !zoomToFill
        actionMessage = if (zoomToFill) "Zoomed to fill screen" else "Fit to screen"
    }

    fun toggleAutoPlay() {
        autoPlayEnabled = !autoPlayEnabled
        PlaybackPrefs.updateAutoplay(autoPlayEnabled)
        actionMessage = if (autoPlayEnabled) "Autoplay on" else "Autoplay off"
    }

    fun toggleShuffle() {
        if (isMusicMode) {
            // Music: Up next itself is shuffled, and put back in order when shuffle goes off.
            shuffleEnabled = !shuffleEnabled
            if (shuffleEnabled) {
                unshuffledMusic = musicQueue
                musicQueue = musicQueue.shuffled()
            } else {
                val order = unshuffledMusic
                if (order != null) {
                    val left = musicQueue.map { it.id }.toHashSet()
                    val ordered = order.filter { it.id in left }
                    val orderedIds = ordered.map { it.id }.toHashSet()
                    musicQueue = ordered + musicQueue.filter { it.id !in orderedIds }
                }
                unshuffledMusic = null
            }
            actionMessage = if (shuffleEnabled) "Shuffle on" else "Shuffle off"
            return
        }
        val player = controller ?: return
        val next = !player.shuffleModeEnabled
        player.shuffleModeEnabled = next
        shuffleEnabled = next
        actionMessage = if (shuffleEnabled) "Shuffle on" else "Shuffle off"
    }

    fun skipNext() {

        if (isMusicMode && musicQueue.isNotEmpty()) {
            openMusicQueue(musicQueue, 0, expanded)
            return
        }
        if (!isMusicMode && queue.isNotEmpty()) {
            playFromQueue(0)
        } else {
            autoPlayNext()
        }
    }

    /**
     * Restarts the song, or, within its first three seconds, goes back to the song before it (music
     * mode), the way every music player does. The current song goes back to the front of Up next.
     */
    fun skipPrevious() {
        val player = controller ?: return
        val now = current
        if (player.currentPosition > 3000 || !isMusicMode || musicHistory.isEmpty() || now == null) {
            player.seekTo(0)
            return
        }
        val previous = musicHistory.removeAt(musicHistory.size - 1)
        val upNext = listOf(now) + musicQueue
        goingBack = true
        try {
            openMusicQueue(listOf(previous) + upNext, 0, expanded)
        } finally {
            goingBack = false
        }
    }
    private var goingBack = false

    /** Mini player swiped right: the video or song before this one (or back to its start). */
    fun swipePrevious() {
        val player = controller ?: return
        if (isMusicMode) {
            if (musicHistory.isEmpty()) { player.seekTo(0); return }
            val now = current ?: return
            val previous = musicHistory.removeAt(musicHistory.size - 1)
            goingBack = true
            try {
                openMusicQueue(listOf(previous, now) + musicQueue, 0, expanded)
            } finally {
                goingBack = false
            }
            return
        }
        if (videoHistory.isEmpty()) { player.seekTo(0); return }
        val previous = videoHistory.removeAt(videoHistory.size - 1)
        goingBack = true
        try {
            open(previous, expand = expanded)
        } finally {
            goingBack = false
        }
    }

    /** Up next, reordered by dragging. */
    fun moveInQueue(from: Int, to: Int) {
        if (from == to) return
        if (isMusicMode) {
            val list = musicQueue.toMutableList()
            if (from !in list.indices || to !in list.indices) return
            list.add(to, list.removeAt(from))
            musicQueue = list
            return
        }
        val player = controller ?: return
        val base = player.currentMediaItemIndex + 1
        if (base + from >= player.mediaItemCount || base + to >= player.mediaItemCount) return
        player.moveMediaItem(base + from, base + to)
    }

    fun removeFromMusicQueue(index: Int) {
        val gone = musicQueue.getOrNull(index)
        musicQueue = musicQueue.filterIndexed { i, _ -> i != index }
        if (gone != null) unshuffledMusic = unshuffledMusic?.filter { it.id != gone.id }
        topUpMusicQueue()
    }

    /** The countdown card at the end of a video, when Autoplay is on. */
    private fun startUpNext() {
        val next = pickAutoplayNext()
        if (next == null) {
            autoPlayNext()
            return
        }
        upNextJob?.cancel()
        upNext = next
        upNextSeconds = 5
        upNextJob = scope.launch {
            while (upNextSeconds > 0) {
                kotlinx.coroutines.delay(1_000)
                upNextSeconds--
            }
            val n = upNext ?: return@launch
            upNext = null
            open(n, expand = expanded)
        }
    }

    fun cancelUpNext() {
        upNextJob?.cancel()
        upNextJob = null
        upNext = null
    }

    fun playUpNextNow() {
        val n = upNext ?: return
        cancelUpNext()
        open(n, expand = expanded)
    }

    private fun pickAutoplayNext(): VideoItem? {
        val curr = current ?: return null
        val rel = FeedFilters.withoutBlocked(details?.related.orEmpty()).filter { it.id != curr.id }
        return rel.firstOrNull { it.channel.equals(curr.channel, ignoreCase = true) } ?: rel.firstOrNull()
    }

    fun toggleLoop() {
        val player = controller ?: return
        val next = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        player.repeatMode = next
        loopEnabled = next == Player.REPEAT_MODE_ONE
        actionMessage = if (loopEnabled) "Loop on" else "Loop off"
    }
    var speedPanelOpen by mutableStateOf(false)

    var holdSpeedActive by mutableStateOf(false)
        internal set

    var scrubPreviewMs by mutableStateOf<Long?>(null)
        internal set
    var scrubDragging by mutableStateOf(false)
        internal set
    private var resumePlayAfterScrub = false
    private var speedBeforeHold: Float? = null
    var videoControlsVisible by mutableStateOf(false)
        internal set

    init {
        // The playback service needs to know when the music player is in use (crossfade).
        scope.launch { snapshotFlow { isMusicMode }.collect { NowPlayingMode.music = it } }

        scope.launch {
            snapshotFlow { StreamRecovery.recovering to StreamRecovery.failed }.collect { (_, failed) ->
                syncFromPlayer()
                if (failed != null && failed.first == current?.id) {
                    error = failed.second
                    isBuffering = false
                }
            }
        }
    }
    var videoAspect by mutableFloatStateOf(16f / 9f)
        private set

    private var aspectFromFrames = false

    



    /**
     * True on a wide screen (an unfolded foldable, a tablet). The video then always gets a 16:9
     * stage across the full width: a tall stage for a vertical video would be taller than the
     * screen, and the page below it (details, comments, more videos) needs the room.
     */
    var wideStage by mutableStateOf(false)

    val stageAspect: Float get() = when {
        AudioOnly.enabled -> AudioStageAspect
        wideStage -> StageFrameAspect
        else -> min(videoAspect.coerceIn(StageMinAspect, StageMaxAspect), StageFrameAspect)
    }
    var firstFrameRendered by mutableStateOf(false)
        private set

    var playingFormat by mutableStateOf<String?>(null)
        private set

    var decodedHeight by mutableStateOf<Int?>(null)
        private set

    var expanded by mutableStateOf(false)

    var fullscreen by mutableStateOf(false)

    /** Portrait videos go edge-to-edge without rotating the device, exactly like YouTube. */
    var portraitFullscreen by mutableStateOf(false)

    /**
     * How far a swipe up on the windowed video has dragged the player toward fullscreen (0 to 1).
     * The page dims and the video grows while the finger is down, so the move follows the finger.
     */
    val enterDrag = androidx.compose.animation.core.Animatable(0f)

    /** The same for a swipe down in fullscreen: the video shrinks away from the finger. */
    val exitDrag = androidx.compose.animation.core.Animatable(0f)

    /** How dark the page already was when a swipe committed, so the entrance continues from there. */
    var enterFrom = 0f

    val portraitVideo get() = videoAspect < 1.02f

    var sheet by mutableStateOf<PlayerSheet?>(null)

    var downloadFor by mutableStateOf<VideoItem?>(null)

    // Tapping a time in a comment jumps the video there.
    val comments = CommentsState(scope).also { it.onTimestamp = { ms -> seekTo(ms) } }

    /** Polled only while a live stream's chat sheet is open. */
    val liveChat = LiveChatState(scope)



    var accountState by mutableStateOf<VideoAccountState?>(null)
        private set
    private var originalLike = LikeStatus.None
    var inWatchLater by mutableStateOf(false)
        private set

    /** Audio tracks offered by the current video, Original first. */
    var audioOptions by mutableStateOf<List<AudioTrackOption>>(emptyList())
        private set

    /** The cue text Media3's text renderer is currently showing, or null when captions are off. */
    var captionText by mutableStateOf<String?>(null)
        private set

    /** Second caption language under the first (dual subtitles), or null. */
    var captionText2 by mutableStateOf<String?>(null)
        private set
    private var timeline2: WordTimeline? = null
    private var secondJob: kotlinx.coroutines.Job? = null
    private var mainCaptionUrl: String? = null

    /** "Most replayed" intensities for the current video (empty when YouTube has none). */
    var heatmap by mutableStateOf<List<Float>>(emptyList())
        private set

    /** Where playback resumed when the video was reopened, for the "Start over" pill. */
    var resumedAt by mutableStateOf<Long?>(null)

    /** The video autoplay is about to open, while its countdown runs. */
    var upNext by mutableStateOf<VideoItem?>(null)
        private set
    var upNextSeconds by mutableStateOf(0)
        private set
    private var upNextJob: kotlinx.coroutines.Job? = null

    /** Videos played before this one (video mode), for swiping back on the mini player. */
    private val videoHistory = ArrayList<VideoItem>()

    private var likeOverrides by mutableStateOf<Map<String, LikeStatus>>(emptyMap())
    private var likeDeltas by mutableStateOf<Map<String, Long>>(emptyMap())
    private var dislikeDeltas by mutableStateOf<Map<String, Long>>(emptyMap())

    val currentLike: LikeStatus
        get() = current?.id?.let { likeOverrides[it] } ?: accountState?.like ?: LikeStatus.None

    var actionMessage by mutableStateOf<String?>(null)

    private var loadJob: Job? = null
    private var relatedJob: Job? = null
    private val RELATED_MIN = 8

    /** True while the related videos are being fetched separately (the extractor had none). */
    var relatedLoading by mutableStateOf(false)
        private set

    /** The related videos could not be loaded after a few tries; the UI offers a retry. */
    var relatedFailed by mutableStateOf(false)
        private set
    private var captionFallbackFor: String? = null
    private var captionFallbackRunning = false

    private var pendingStart: VideoItem? = null





    private val known = HashMap<String, VideoItem>()

    var queue by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    var musicQueue by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    fun open(item: VideoItem, expand: Boolean = true) {
        val before = current
        if (!goingBack && !isMusicMode && before != null && before.id != item.id) {
            videoHistory.add(before)
            if (videoHistory.size > 100) videoHistory.removeAt(0)
        }
        isMusicMode = false
        musicMore = null
        musicGen++
        AudioOnly.set(false)
        runCatching { applyVideoTrack() }
        if (expand) expanded = true
        if (current?.id == item.id && error == null) return
        known[item.id] = item
        show(item, startPlayback = true)
    }

    fun openMusic(item: VideoItem, expand: Boolean = true) {
        startMusic(listOf(item), 0, MusicFeeds.radio(item.id), expand = expand)
    }

    // Up next the YouTube Music way: once it runs low, more songs come from the rest of the
    // playlist or album being played, then (with Autoplay on) from YouTube Music's radio for the
    // last song, so it never just stops or falls back to ordinary YouTube videos.
    private var musicMore: MusicFeeder? = null
    private var musicGen = 0
    private var unshuffledMusic: List<VideoItem>? = null
    private val musicFill = kotlinx.coroutines.sync.Mutex()

    /**
     * Plays [items] from [index] as a new music queue. [more] brings the songs after them (the
     * rest of a playlist, or a radio); [shuffle] plays them in random order.
     */
    fun startMusic(
        items: List<VideoItem>,
        index: Int,
        more: MusicFeeder? = null,
        shuffle: Boolean = false,
        expand: Boolean = true,
    ) {
        if (items.isEmpty()) return
        val distinct = items.distinctBy { it.id }
        val first = if (shuffle) distinct.random() else distinct.getOrNull(index.coerceIn(0, distinct.size - 1)) ?: return
        var list = distinct
        var start = distinct.indexOfFirst { it.id == first.id }.coerceAtLeast(0)
        if (shuffle) shuffleEnabled = true
        unshuffledMusic = null
        if (shuffleEnabled) {
            val after = distinct.filter { it.id != first.id }
            unshuffledMusic = distinct.drop(start + 1)
            list = listOf(first) + after.shuffled()
            start = 0
        }
        list.forEach { known[it.id] = it }
        musicGen++
        musicMore = more
        openMusicQueue(list, start, expand)
    }

    /** A radio of songs like this one, starting with it. */
    fun startRadio(item: VideoItem) {
        startMusic(listOf(item), 0, MusicFeeds.radio(item.id), expand = expanded)
        actionMessage = "Radio: ${item.title}"
    }

    fun addToMusicQueue(item: VideoItem) {
        if (current == null || !isMusicMode) { openMusic(item, expand = false); return }
        known[item.id] = item
        musicQueue = musicQueue.filter { it.id != item.id } + item
        unshuffledMusic = unshuffledMusic?.let { it.filter { q -> q.id != item.id } + item }
        actionMessage = "Added to queue"
    }

    private fun topUpMusicQueue() {
        if (!isMusicMode || musicQueue.size >= 5 || musicFill.isLocked) return
        scope.launch { fillMusicQueue() }
    }

    private suspend fun fillMusicQueue() {
        musicFill.lock()
        try {
            repeat(4) {
                if (!isMusicMode || musicQueue.size >= 5) return
                val gen = musicGen
                val feeder: MusicFeeder = musicMore ?: run {
                    if (!autoPlayEnabled) return
                    val seed = musicQueue.lastOrNull() ?: current ?: return
                    MusicFeeds.radio(seed.id).also { musicMore = it }
                }
                val got = runCatching { feeder() }.getOrDefault(emptyList())
                // A new queue was started meanwhile: these songs belong to the old one.
                if (gen != musicGen) return@repeat
                if (got.isEmpty()) {
                    musicMore = null
                    if (feeder.isRadio) return
                    return@repeat
                }
                val seen = HashSet<String>()
                current?.id?.let(seen::add)
                musicQueue.forEach { seen.add(it.id) }
                musicHistory.takeLast(50).forEach { seen.add(it.id) }
                val fresh = got.filter { seen.add(it.id) }
                fresh.forEach { known[it.id] = it }
                if (fresh.isEmpty()) return@repeat
                musicQueue = musicQueue + if (shuffleEnabled && !feeder.isRadio) fresh.shuffled() else fresh
                unshuffledMusic = unshuffledMusic?.plus(fresh)
                if (musicQueue.size >= 5) return
            }
        } finally {
            musicFill.unlock()
        }
    }

    



    fun openMusicQueue(items: List<VideoItem>, index: Int, expand: Boolean = true) {
        val item = items.getOrNull(index) ?: return
        val before = current
        if (!goingBack && isMusicMode && before != null && before.id != item.id) {
            musicHistory.add(before)
            if (musicHistory.size > 100) musicHistory.removeAt(0)
        }
        isMusicMode = true
        applyVideoTrack()
        musicQueue = items.drop(index + 1).filter { it.id != item.id }
        unshuffledMusic = unshuffledMusic?.filter { it.id != item.id }
        if (expand) expanded = true
        topUpMusicQueue()
        if (current?.id == item.id && error == null) return
        known[item.id] = item
        show(item, startPlayback = true)
    }

    fun playNextMusic(item: VideoItem) {
        if (isMusicMode && current != null) {
            known[item.id] = item
            musicQueue = listOf(item) + musicQueue.filter { it.id != item.id }
            unshuffledMusic = unshuffledMusic?.let { listOf(item) + it.filter { q -> q.id != item.id } }
            actionMessage = "Playing next"
            return
        }
        val player = controller ?: return
        known[item.id] = item
        if (current == null || player.mediaItemCount == 0) { openMusic(item, expand = false); return }
        player.addMediaItem(player.currentMediaItemIndex + 1, mediaItemFor(item))
        actionMessage = "Playing next"
        continueIfEnded(player)
    }

    




    private var openedAt = 0L

    private fun show(item: VideoItem, startPlayback: Boolean) {
        endHoldSpeed()
        openedAt = android.os.SystemClock.elapsedRealtime()
        Log.i("YTXTubeTime", "open ${item.id}")
        if (startPlayback) Net.prewarmMediaConnection()
        current = item
        if (isMusicMode) RecentMusic.record(item)
        details = null
        error = null
        firstFrameRendered = false
        videoAspect = 16f / 9f
        aspectFromFrames = false
        decodedHeight = null
        playingFormat = null
        playingAudio = null
        loading = true
        sheet = null
        comments.clearFor(item.id)
        liveChat.stop()
        relatedJob?.cancel()
        relatedLoading = false
        relatedFailed = false
        captionFallbackFor = null
        captionFallbackRunning = false
        accountState = null
        inWatchLater = false
        audioOptions = emptyList()
        captionText = null
        timeline = null
        captionText2 = null
        timeline2 = null
        secondJob?.cancel()
        mainCaptionUrl = null
        heatmap = emptyList()
        resumedAt = null
        cancelUpNext()
        if (!isMusicMode && !item.isLive && item.localUri == null) {
            ContinueWatching.record(item)
            scope.launch {
                val h = runCatching { InnerTube.heatmap(item.id) }.getOrDefault(emptyList())
                if (current?.id == item.id) heatmap = h
            }
        }
        // The app's accent follows the video's colours (Settings).
        scope.launch {
            val raw = runCatching { ThumbTint.load(YTXTubeApplication.appContext, item.id, item.thumbnail) }.getOrNull()
            if (current?.id == item.id) VideoAccent.raw = raw
        }
        speedPanelOpen = false
        videoZoom = 1f
        portraitFullscreen = false
        if (item.localUri != null) {

            details = VideoDetails(
                item = item,
                description = "",
                likes = 0,
                dislikes = -1,
                subscribers = 0,
                related = emptyList(),
                playable = true,
                quality = null,
                qualities = emptyList(),
                aspect = null,
            )
            loading = false
            if (startPlayback) startPlayback(item)
            return
        }
        if (Account.signedIn) {
            scope.launch {
                runCatching { InnerTube.videoState(item.id) }
                    .onSuccess { if (current?.id == item.id) { accountState = it; originalLike = it.like } }
                    .onFailure { Log.w(TAG, "account state for ${item.id}", it) }
            }
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            runCatching { YouTube.details(item.id) }
                .onSuccess { d ->
                    Log.i("YTXTubeTime", "details +${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
                    details = d
                    refreshTracks()
                    // The extractor sometimes hands back only a couple of related videos, so top
                    // short lists up as well, not just empty ones.
                    if (d.related.size < RELATED_MIN) loadRelatedFallback(item.id, d.item.title)
                    else prefetchRelated(d.related)

                    if (!aspectFromFrames) d.aspect?.let { videoAspect = it }


                    comments.load(item.id)
                    known[item.id] = d.item
                    d.related.forEach { known[it.id] = it }
                    if (!d.playable) error = "No playable stream was found for this video."
                    else if (startPlayback) startPlayback(d.item)

                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val dislikes = if (PlaybackPrefs.showDislikes) fetchDislikeCount(item.id) else -1
                        if (current?.id == item.id && dislikes >= 0) {
                            details = details?.copy(dislikes = dislikes)
                        }
                    }
                }
                .onFailure {
                    Log.w(TAG, "details failed for ${item.id}", it)
                    error = YouTube.friendlyError(it)

                    comments.load(item.id)
                }
            loading = false
        }
    }

    /**
     * The extractor sometimes returns no related videos, which left "More videos" empty. Ask YouTube
     * directly, a few times, without holding up the video itself.
     */
    private fun loadRelatedFallback(id: String, title: String) {
        relatedJob?.cancel()
        relatedFailed = false
        relatedLoading = true
        relatedJob = scope.launch {
            var found: List<VideoItem> = emptyList()
            for (attempt in 0 until 3) {
                if (current?.id != id) return@launch
                found = runCatching { YouTube.fallbackRelated(id, title) }.getOrDefault(emptyList())
                if (found.isNotEmpty()) break
                delay(1_000L * (attempt + 1))
            }
            if (current?.id != id) return@launch
            relatedLoading = false
            val shown = details?.takeIf { it.item.id == id } ?: return@launch
            // Keep what the extractor gave (it comes first) and add what was found after it.
            val merged = (shown.related + found).distinctBy { it.id }.filter { it.id != id }
            if (merged.isEmpty()) {
                relatedFailed = true
                return@launch
            }
            if (found.isEmpty()) return@launch
            merged.forEach { known[it.id] = it }
            details = shown.copy(related = merged)
            prefetchRelated(merged)
        }
    }

    fun retryRelated() {
        val d = details ?: return
        loadRelatedFallback(d.item.id, d.item.title)
    }

    /** Resolve the first couple of related videos in the background, so tapping one starts faster. */
    private fun prefetchRelated(list: List<VideoItem>) {
        val ids = list.asSequence().filter { !it.isLive }.take(2).map { it.id }.toList()
        if (ids.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            delay(3_000) // let this video get going first
            ids.forEach { StreamCache.prefetch(it) }
        }
    }

    private fun mediaItemFor(item: VideoItem): MediaItem {
        val localUri = item.localUri
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.channel)
            .setArtworkUri(item.thumbnail?.toUri())
            .apply {
                if (localUri != null) {
                    setExtras(android.os.Bundle().apply { putString("local_uri", localUri) })
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setMediaMetadata(metadata)
            .apply {
                if (localUri != null) {


                    setRequestMetadata(
                        MediaItem.RequestMetadata.Builder().setMediaUri(localUri.toUri()).build(),
                    )
                }
            }
            .build()
    }

    private fun startPlayback(item: VideoItem) {
        val player = controller ?: run { pendingStart = item; return }

        val start = if (PlaybackPrefs.resume) ResumeStore.startPosition(item.id) else 0L
        resumedAt = if (start > 0L && !isMusicMode) start else null
        replaceCurrent(player, item, positionMs = start)
        player.play()
        if (selectedSubtitleLanguage != null) setSubtitleLanguage(selectedSubtitleLanguage, userInitiated = false)
        refreshTracks()
    }

    




    private fun replaceCurrent(player: MediaController, item: VideoItem, positionMs: Long) {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(mediaItemFor(item), positionMs)
        } else {


            swapping = true
            try {
                val index = player.currentMediaItemIndex
                player.addMediaItem(index + 1, mediaItemFor(item))
                player.seekTo(index + 1, positionMs)
                player.removeMediaItem(index)
            } finally {
                swapping = false
            }
        }
        player.prepare()
    }


    fun playNext(item: VideoItem) {
        val player = controller ?: return
        known[item.id] = item
        if (current == null || player.mediaItemCount == 0) { open(item, expand = false); return }
        player.addMediaItem(player.currentMediaItemIndex + 1, mediaItemFor(item))
        actionMessage = "Playing next"
        continueIfEnded(player)
    }


    private fun continueIfEnded(player: MediaController) {
        if (player.playbackState == Player.STATE_ENDED && player.currentMediaItemIndex + 1 < player.mediaItemCount) {
            player.seekTo(player.currentMediaItemIndex + 1, 0L)
            player.play()
        }
    }

    fun addToQueue(item: VideoItem) {
        val player = controller ?: return
        known[item.id] = item
        if (current == null || player.mediaItemCount == 0) { open(item, expand = false); return }
        player.addMediaItem(mediaItemFor(item))
        actionMessage = "Added to queue"
        continueIfEnded(player)
    }


    fun playFromQueue(index: Int) {
        val player = controller ?: return
        player.seekTo(player.currentMediaItemIndex + 1 + index, 0L)
        player.play()
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        player.removeMediaItem(player.currentMediaItemIndex + 1 + index)
    }

    fun clearQueue() {
        val player = controller ?: return
        val from = player.currentMediaItemIndex + 1
        if (from < player.mediaItemCount) player.removeMediaItems(from, player.mediaItemCount)
    }

    private fun rebuildQueue() {
        val player = controller ?: return
        val from = player.currentMediaItemIndex + 1
        queue = (from until player.mediaItemCount).map { i -> itemFor(player.getMediaItemAt(i)) }
    }

    private fun itemFor(media: MediaItem): VideoItem = known[media.mediaId] ?: VideoItem(
        id = media.mediaId,
        title = media.mediaMetadata.title?.toString().orEmpty(),
        channel = media.mediaMetadata.artist?.toString().orEmpty(),
        channelUrl = null,
        thumbnail = media.mediaMetadata.artworkUri?.toString(),
        avatar = null,
        durationSec = 0,
        views = -1,
        uploaded = null,
    ).also { known[media.mediaId] = it }

    private fun autoPlayNext() {
        val curr = current ?: return
        val currId = curr.id

        if (isMusicMode && musicQueue.isNotEmpty()) {
            skipNext()
            return
        }
        if (isMusicMode) {
            // Wait for the playlist's next page or the radio, then carry on.
            scope.launch {
                fillMusicQueue()
                if (current?.id == currId && musicQueue.isNotEmpty()) skipNext()
            }
            return
        }
        val relList = details?.related.orEmpty().filter { it.id != currId }

        val next = if (isMusicMode) {
            relList.firstOrNull { isMusicTrack(it) } ?: relList.firstOrNull()
        } else {
            relList.firstOrNull { it.channel.equals(curr.channel, ignoreCase = true) } ?: relList.firstOrNull()
        }
        if (next != null) {
            actionMessage = "Up next: ${next.title}"
            if (isMusicMode) openMusic(next, expand = expanded) else open(next, expand = expanded)
        } else {
            scope.launch {
                runCatching { YouTube.details(currId) }
                    .onSuccess { d ->
                        val list = d.related.filter { it.id != currId }
                        val rel = if (isMusicMode) {
                            list.firstOrNull { isMusicTrack(it) } ?: list.firstOrNull()
                        } else {
                            list.firstOrNull { it.channel.equals(curr.channel, ignoreCase = true) } ?: list.firstOrNull()
                        }
                        if (rel != null) {
                            actionMessage = "Up next: ${rel.title}"
                            if (isMusicMode) openMusic(rel, expand = expanded) else open(rel, expand = expanded)
                        }
                    }
            }
        }
    }


    private var swapping = false

    private fun onPlayerMovedTo(media: MediaItem) {
        val player = controller ?: return
        if (swapping || media.mediaId.isBlank()) return

        if (player.currentMediaItemIndex > 0) player.removeMediaItems(0, player.currentMediaItemIndex)
        if (media.mediaId != current?.id) show(itemFor(media), startPlayback = false)
    }


    val segments: List<SponsorBlock.Segment>
        get() = current?.id?.let { SponsorBlock.forUi[it] }.orEmpty()


    val askSegment: SponsorBlock.Segment?
        get() = if (!SponsorBlock.enabled) null else segments.firstOrNull {
            it.action == SponsorBlock.Action.Button && positionMs in it.startMs until it.endMs - 500
        }

    fun skipSegment(segment: SponsorBlock.Segment) = seekTo(segment.endMs)

    fun undoSkip(skip: SponsorBlock.Skip) {
        if (skip.videoId == current?.id) seekTo(skip.segment.startMs)
        SponsorBlock.lastSkip = null
    }


    fun refreshAccountState() {
        val item = current ?: return
        accountState = null
        inWatchLater = false
        if (!Account.signedIn) return
        scope.launch {
            runCatching { InnerTube.videoState(item.id) }
                .onSuccess { if (current?.id == item.id) { accountState = it; originalLike = it.like } }
                .onFailure { Log.w(TAG, "account state for ${item.id}", it) }
        }
    }


    val displayLikes: Long
        get() {
            val base = details?.likes ?: return -1
            val delta = current?.id?.let { likeDeltas[it] } ?: 0L
            return (base + delta).coerceAtLeast(0)
        }

    val displayDislikes: Long
        get() {
            val base = details?.dislikes ?: return -1
            if (base < 0) return -1
            val delta = current?.id?.let { dislikeDeltas[it] } ?: 0L
            return (base + delta).coerceAtLeast(0)
        }

    private fun likeWeight(status: LikeStatus): Long = when (status) {
        LikeStatus.Like -> 1L
        LikeStatus.Dislike -> -1L
        LikeStatus.None -> 0L
    }

    private fun dislikeWeight(status: LikeStatus): Long = if (status == LikeStatus.Dislike) 1L else 0L

    /**
     * Tapping a thumb is optimistic for everyone, signed in or not. The visible count moves
     * immediately; the network call only decides whether the choice survives a restart.
     */
    fun setLike(target: LikeStatus) {
        val id = current?.id ?: return
        val before = currentLike
        val next = if (before == target) LikeStatus.None else target
        likeOverrides = likeOverrides + (id to next)
        likeDeltas = likeDeltas + (id to ((likeDeltas[id] ?: 0L) + likeWeight(next) - likeWeight(before)))
        dislikeDeltas = dislikeDeltas + (id to ((dislikeDeltas[id] ?: 0L) + dislikeWeight(next) - dislikeWeight(before)))
        actionMessage = when (next) {
            LikeStatus.Like -> "Liked"
            LikeStatus.Dislike -> "Not for me"
            LikeStatus.None -> "Rating removed"
        }

        if (!Account.signedIn) return
        val previousAccount = accountState
        accountState = previousAccount?.copy(like = next)
        scope.launch {
            runCatching { InnerTube.setLike(id, next) }
                .onFailure {
                    if (current?.id == id) {
                        likeOverrides = likeOverrides + (id to before)
                        likeDeltas = likeDeltas + (id to ((likeDeltas[id] ?: 0L) - likeWeight(next) + likeWeight(before)))
                        dislikeDeltas = dislikeDeltas + (id to ((dislikeDeltas[id] ?: 0L) - dislikeWeight(next) + dislikeWeight(before)))
                        accountState = previousAccount
                        actionMessage = "Couldn't update your like."
                    }
                }
        }
    }

    fun toggleLike() = setLike(LikeStatus.Like)

    fun toggleDislike() = setLike(LikeStatus.Dislike)

    fun toggleSubscribe() {
        val before = accountState ?: return
        val channelId = before.channelId
            ?: details?.item?.channelUrl?.substringAfter("/channel/", "")?.takeIf { it.startsWith("UC") }
            ?: return
        val subscribe = before.subscribed != true
        accountState = before.copy(subscribed = subscribe, channelId = channelId)
        scope.launch {
            runCatching { InnerTube.setSubscribed(channelId, subscribe) }
                .onFailure { accountState = before; actionMessage = "Couldn't update your subscription." }
        }
    }

    fun toggleWatchLater() {
        val id = current?.id ?: return
        val add = !inWatchLater
        inWatchLater = add
        scope.launch {
            runCatching { InnerTube.setInWatchLater(id, add) }
                .onSuccess { actionMessage = if (add) "Saved to Watch later" else "Removed from Watch later" }
                .onFailure { inWatchLater = !add; actionMessage = "Couldn't update Watch later."; Log.w(TAG, "watch later $id", it) }
        }
    }


    fun saveToWatchLater(video: VideoItem) {
        scope.launch {
            runCatching { InnerTube.setInWatchLater(video.id, true) }
                .onSuccess {
                    if (current?.id == video.id) inWatchLater = true
                    actionMessage = "Saved to Watch later"
                }
                .onFailure { actionMessage = "Couldn't save to Watch later."; Log.w(TAG, "watch later ${video.id}", it) }
        }
    }

    



    fun setQuality(height: Int?, preferHdr: Boolean = QualityPreference.hdr) {
        QualityPreference.set(height, preferHdr)
        val player = controller ?: return
        val item = details?.item ?: current ?: return

        // The service rebuilds the source from the exact rendition StreamSelection
        // picked. Forcing a min/max height here would filter that rendition out when
        // an HDR row falls back to a lower SDR height, leaving the player with no
        // video track at all.
        if (item.isLive) {
            details = details?.copy(quality = height?.let { "${it}p" } ?: "Live")
            return
        }

        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        replaceCurrent(player, item, position)
        player.playWhenReady = playWhenReady
        val info = StreamCache.get(item.id) ?: return
        val picked = StreamSelection.pick(info)
        details = details?.copy(
            quality = picked.playedHeight?.let { "${it}p${if (picked.hdr) " HDR" else ""}" },
        )
    }

    /** Refresh the audio-track list from the resolved stream info. */
    fun refreshTracks() {
        val id = current?.id ?: return
        val cached = StreamCache.get(id)
        if (cached != null) {
            syncAudioPreference(cached)
            audioOptions = StreamSelection.audioOptions(cached)
            prepareCaptions(id)
            return
        }
        scope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { StreamCache.getOrLoad(id) }.getOrNull()
            } ?: return@launch
            if (current?.id != id) return@launch
            syncAudioPreference(info)
            audioOptions = StreamSelection.audioOptions(info)
            prepareCaptions(id)
        }
    }

    /** A saved language that this video does not offer falls back to Original, not silence. */
    private fun syncAudioPreference(info: org.schabi.newpipe.extractor.stream.StreamInfo) {
        val wanted = AudioTrackPreference.key ?: return
        val offered = StreamSelection.audioTracks(info)
        if (offered.none { it.key == wanted || wanted.startsWith(it.key) }) AudioTrackPreference.reset()
    }

    /**
     * Swap the audio rendition without losing the place. The service re-reads the persisted
     * preference when it builds the next media source.
     */
    fun setAudioTrack(option: AudioTrackOption?) {
        AudioTrackPreference.set(if (option == null || option.isOriginal) null else option.key, option?.label ?: "Original")
        val player = controller ?: return
        val item = details?.item ?: current ?: return
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        replaceCurrent(player, item, position)
        player.playWhenReady = playWhenReady
        actionMessage = "Audio: ${option?.label ?: "Original"}"
    }

    var selectedSubtitleLanguage by mutableStateOf<String?>(null)
        private set

    /**
     * Word-by-word caption timeline for the selected track. Not Compose state on purpose:
     * [captionText] is what the UI watches, and it only changes when a new word appears.
     */
    private var timeline: WordTimeline? = null
    private var subtitleFetchJob: kotlinx.coroutines.Job? = null

    /**
     * Turn captions on for [code] (or off with null).
     *
     * [userInitiated] is false when the app re-applies the current choice (new video, tracks
     * finished loading); those calls stay quiet and do not overwrite the saved preference.
     */
    fun setSubtitleLanguage(code: String?, trackUrl: String? = null, userInitiated: Boolean = true) {
        selectedSubtitleLanguage = code
        subtitleFetchJob?.cancel()
        if (userInitiated) {
            CaptionPreference.set(code != null)
            if (code != null) CaptionPreference.setLanguage(code)
        }
        val p = controller
        try {
            if (code == null) {
                timeline2 = null
                captionText2 = null
                secondJob?.cancel()
                mainCaptionUrl = null
                p?.trackSelectionParameters = p?.trackSelectionParameters
                    ?.buildUpon()
                    ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    ?.build() ?: return
                timeline = null
                captionText = null
                if (userInitiated) actionMessage = "Captions off"
                return
            } else {
                p?.trackSelectionParameters = p?.trackSelectionParameters
                    ?.buildUpon()
                    ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    ?.setPreferredTextLanguage(code)
                    ?.build() ?: return
                if (userInitiated) actionMessage = "Captions on"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting subtitle language", e)
        }

        val videoId = current?.id ?: return
        val language: String = code ?: return
        val url = trackUrl ?: run {
            val info = StreamCache.get(videoId)
            info?.subtitles?.firstOrNull { it.languageTag == language }?.content
                ?: CaptionTracks.get(videoId)?.let { CaptionTracks.pick(it, language) }?.url
        }

        mainCaptionUrl = url
        loadSecondCaptions()

        // Already downloaded (prefetched when the video opened): show it right now.
        val ready = url?.let { WordCaptionLoader.cached(it) }
        if (ready != null) {
            timeline = ready
            updateCaption()
            return
        }

        // Not downloaded yet. Whatever was loaded belongs to another video or language.
        timeline = null
        captionText = null
        if (url == null) return
        subtitleFetchJob = scope.launch(Dispatchers.IO) {
            val loaded = WordCaptionLoader.load(url)
            withContext(Dispatchers.Main) {
                if (selectedSubtitleLanguage == code && current?.id == videoId && !loaded.isEmpty) {
                    timeline = loaded
                    updateCaption()
                }
            }
        }
    }

    fun setCaptions(on: Boolean) {
        if (!on) {
            setSubtitleLanguage(null)
        } else if (selectedSubtitleLanguage == null) {
            toggleCaptions()
        }
    }

    /** Caption tracks the current video offers (empty until its streams have been resolved). */
    private fun captionTracksFor(videoId: String): List<CaptionTrack> =
        CaptionTracks.get(videoId)
            ?: StreamCache.get(videoId)?.let { CaptionTracks.fromStreamInfo(it) }.orEmpty()

    /**
     * One tap on the CC button: off turns captions on in the language used last time (falling
     * back to a human-written track, then whatever the video has); on turns them off.
     */
    fun toggleCaptions() {
        if (selectedSubtitleLanguage != null) {
            setSubtitleLanguage(null)
            return
        }
        val videoId = current?.id ?: return
        val tracks = captionTracksFor(videoId)
        if (tracks.isEmpty()) {
            actionMessage = if (StreamCache.get(videoId) == null || captionFallbackRunning) "Captions are still loading" else "No captions for this video"
            return
        }
        val track = CaptionTracks.pick(tracks, CaptionPreference.languageTag)
            ?: tracks.firstOrNull { !it.autoGenerated }
            ?: tracks.first()
        setSubtitleLanguage(track.languageTag, track.url)
    }

    /**
     * Runs once a video's streams are known. Restores captions if they were on (so they follow
     * you from video to video), otherwise quietly downloads the likely track so the CC button
     * has no waiting time.
     */
    private fun prepareCaptions(videoId: String) {
        if (current?.id != videoId) return
        val tracks = captionTracksFor(videoId)
        if (tracks.isEmpty()) {
            // The extraction listed no captions. Ask YouTube's player response directly, but only
            // now: it used to run before the video could start, one more round trip for every
            // video without captions.
            if (captionFallbackFor != videoId && StreamCache.get(videoId) != null) {
                captionFallbackFor = videoId
                captionFallbackRunning = true
                scope.launch {
                    val found = withContext(Dispatchers.IO) {
                        runCatching { CaptionTracks.load(videoId) }.getOrDefault(emptyList())
                    }
                    if (current?.id == videoId) {
                        captionFallbackRunning = false
                        if (found.isNotEmpty()) prepareCaptions(videoId)
                    }
                }
            }
            return
        }
        val wanted = selectedSubtitleLanguage ?: CaptionPreference.languageTag
        val track = CaptionTracks.pick(tracks, wanted)
        if (selectedSubtitleLanguage != null || CaptionPreference.enabled) {
            if (track != null) setSubtitleLanguage(track.languageTag, track.url, userInitiated = false)
        } else if (track != null && WordCaptionLoader.cached(track.url) == null) {
            scope.launch(Dispatchers.IO) { WordCaptionLoader.load(track.url) }
        }
    }

    /**
     * Dual subtitles: YouTube's own translation of the caption track into the second language
     * (Settings in the long-press CC menu), shown smaller under the first.
     */
    fun loadSecondCaptions() {
        secondJob?.cancel()
        timeline2 = null
        captionText2 = null
        val second = CaptionPreference.secondLanguage ?: return
        val main = mainCaptionUrl ?: return
        val first = selectedSubtitleLanguage ?: return
        if (first.substringBefore('-').equals(second.substringBefore('-'), ignoreCase = true)) return
        val base = main.replace(Regex("[&?]tlang=[^&]*"), "")
        val url = base + (if ('?' in base) "&" else "?") + "tlang=" + second
        val videoId = current?.id ?: return
        WordCaptionLoader.cached(url)?.let { timeline2 = it; updateCaption(); return }
        secondJob = scope.launch(Dispatchers.IO) {
            val loaded = runCatching { WordCaptionLoader.load(url) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (loaded != null && !loaded.isEmpty && current?.id == videoId && CaptionPreference.secondLanguage == second) {
                    timeline2 = loaded
                    updateCaption()
                }
            }
        }
    }

    /** Recomputes the word-by-word caption for the current playback position. */
    internal fun updateCaption() {
        if (selectedSubtitleLanguage == null) {
            if (captionText != null) captionText = null
            if (captionText2 != null) captionText2 = null
            return
        }
        val pos = controller?.currentPosition ?: positionMs
        val second = timeline2?.textAt(pos)
        if (captionText2 != second) captionText2 = second
        val tl = timeline ?: return
        captionText = tl.textAt(pos)
    }


    fun retry() = current?.let { item -> StreamCache.invalidate(item.id); current = null; open(item) }

    fun togglePlay() {
        cancelUpNext()
        val player = controller ?: return
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }


    fun seekBy(deltaMs: Long): Long {
        val player = controller ?: return 0L
        val from = player.currentPosition
        seekTo(from + deltaMs)
        return positionMs - from
    }

    fun seekTo(ms: Long) {
        val player = controller ?: return
        val max = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = ms.coerceIn(0L, max)
        positionMs = target
        player.seekTo(target)
    }

    fun beginScrubDragging() {
        if (scrubDragging) return
        val player = controller
        resumePlayAfterScrub = player?.isPlaying == true
        if (resumePlayAfterScrub) player?.pause()
        scrubDragging = true
    }


    fun endScrubDragging(seekMs: Long? = null) {
        if (!scrubDragging) return
        val player = controller
        val resume = resumePlayAfterScrub
        resumePlayAfterScrub = false
        scrubDragging = false
        scrubPreviewMs = null
        if (seekMs != null) seekTo(seekMs)
        if (resume) player?.play()
    }


    fun beginHoldSpeed(): Boolean {
        val player = controller ?: return false
        if (scrubDragging || speedBeforeHold != null) return false
        if (!player.isPlaying) return false
        speedBeforeHold = player.playbackParameters.speed
        holdSpeedActive = true
        previewSpeed(PlaybackPrefs.holdSpeed)
        return true
    }

    fun endHoldSpeed() {
        val restore = speedBeforeHold ?: return
        speedBeforeHold = null
        holdSpeedActive = false
        previewSpeed(restore)
    }


    fun previewSpeed(value: Float) {
        val player = controller ?: return
        val s = PlaybackSpeed.snap(value)
        if (player.playbackParameters.speed != s) player.setPlaybackSpeed(s)
        speed = s
    }

    




    fun commitSpeed(value: Float) {
        previewSpeed(value)
        val s = PlaybackSpeed.snap(value)
        // Remembered for this channel too, so its next video starts at the same speed.
        ChannelSpeeds.save(current?.channel?.ifBlank { null } ?: details?.item?.channel, s)
        if (!PlaybackSpeed.remember) return
        if (PlaybackSpeed.isNormal(s) && !PlaybackSpeed.isNormal(PlaybackSpeed.saved)) PlaybackSpeed.setRemember(false, 1f)
        else PlaybackSpeed.save(s)
    }

    fun toggleRememberSpeed() = PlaybackSpeed.setRemember(!PlaybackSpeed.remember, speed)


    fun setAudioOnly(on: Boolean) {
        AudioOnly.set(on)
        applyVideoTrack()
    }

    




    fun setBackground(background: Boolean) {
        if (wantBackground == background) return
        wantBackground = background
        if (isMusicMode) return
        applyVideoTrack()
    }

    





    private var wantBackground = false


    private fun applyVideoTrack() {
        runCatching {
            // Music keeps its video track alive in the background. Toggling it off and on
            // made the decoder re-init, which is the audible dip heard when reopening the
            // app during background playback.
            if (isMusicMode) return
            val off = AudioOnly.enabled || wantBackground
            val player = controller ?: return
            if (player.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO) == off) return
            Log.i(TAG, if (off) "video off (${if (AudioOnly.enabled) "audio only" else "background"})" else "video on")
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, off)
                .build()
        }
    }

    fun close() {
        cancelUpNext()
        VideoAccent.raw = null
        loadJob?.cancel()
        relatedJob?.cancel()
        relatedLoading = false
        relatedFailed = false
        controller?.run { stop(); clearMediaItems() }
        queue = emptyList()
        expanded = false
        fullscreen = false
        portraitFullscreen = false
        sheet = null
        current = null
        details = null
        error = null
        loading = false
    }

    




    internal fun onConnected() {
        controller?.let { p ->
            if (p.mediaItemCount == 0) {
                p.repeatMode = if (PlaybackPrefs.loopByDefault) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
        }
        syncFromPlayer()
        applyVideoTrack()
        controller?.videoSize?.let(::takeVideoSize)
        pendingStart?.let { pendingStart = null; startPlayback(it); return }
        val player = controller ?: return
        rebuildQueue()
        val playing = player.currentMediaItem ?: return
        if (current != null || playing.mediaId.isBlank()) return
        val meta = playing.mediaMetadata
        val item = VideoItem(
            id = playing.mediaId,
            title = meta.title?.toString().orEmpty(),
            channel = meta.artist?.toString().orEmpty(),
            channelUrl = null,
            thumbnail = meta.artworkUri?.toString(),
            avatar = null,
            durationSec = player.duration.takeIf { it > 0 }?.div(1000) ?: 0L,
            views = -1,
            uploaded = null,
        )
        current = item
        loadJob = scope.launch {
            runCatching { YouTube.details(item.id) }.onSuccess { details = it; current = it.item; refreshTracks() }
        }
    }

    internal fun syncFromPlayer() {
        val player = controller ?: return
        isPlaying = player.isPlaying

        isBuffering = player.playbackState == Player.STATE_BUFFERING || StreamRecovery.recovering == current?.id
        positionMs = player.currentPosition.coerceAtLeast(0L)
        bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
        durationMs = player.duration.takeIf { it > 0 } ?: ((current?.durationSec ?: 0L) * 1000L)
        speed = player.playbackParameters.speed
        loopEnabled = player.repeatMode == Player.REPEAT_MODE_ONE

        updateCaption()
    }

    internal val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer()
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) describeAudio()
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) rebuildQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Playing again after a quiet recovery: an old error message no longer applies.
            if (playbackState == Player.STATE_READY && controller?.playerError == null && error != null) error = null
            if (playbackState == Player.STATE_ENDED) {
                val p = controller ?: return
                if (p.repeatMode == Player.REPEAT_MODE_ONE) return
                if (p.currentMediaItemIndex + 1 < p.mediaItemCount) {
                    p.seekTo(p.currentMediaItemIndex + 1, 0L)
                    p.play()
                } else if (isMusicMode) {
                    // Music carries straight on through Up next; Autoplay only decides whether a
                    // radio follows once the songs you chose run out.
                    autoPlayNext()
                } else if (autoPlayEnabled) {
                    // Videos: a short countdown with Cancel first.
                    startUpNext()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) onPlayerMovedTo(mediaItem)
            val lang = selectedSubtitleLanguage
            if (lang != null) {
                setSubtitleLanguage(lang, userInitiated = false)
            } else {
                captionText = null
                timeline = null
            }
        }

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            // Our own word-by-word timeline owns the caption plate. Media3's text track (full
            // cues at once) is only a fallback while that timeline is not available.
            if (timeline != null) return
            val text = cueGroup.cues
                .mapNotNull { cue -> cue.text?.toString()?.takeIf { it.isNotBlank() } }
                .joinToString("\n")
            if (text.isNotBlank()) {
                captionText = text
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) = takeVideoSize(videoSize)

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val vs = controller?.videoSize ?: return
            if (vs.width <= 0 || vs.height <= 0) return
            val w = (vs.width * vs.pixelWidthHeightRatio).roundToInt()
            describeFormat(w, vs.height)
        }

        override fun onRenderedFirstFrame() {
            Log.i("YTXTubeTime", "first frame +${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
            firstFrameRendered = true
        }

        override fun onPlayerError(e: PlaybackException) {
            val status = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            Log.w(TAG, "playback error ${e.errorCodeName}${status?.let { " (HTTP $it)" } ?: ""} for ${current?.id}", e)

            // The service quietly recovers refused streams; only one it gave up on is shown.
            if (e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
                (StreamRecovery.recovering == current?.id || StreamRecovery.recentlyHandled(current?.id))
            ) {
                isBuffering = true
                return
            }
            val client = StreamHealth.clientOf(
                (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.dataSpec?.uri?.toString(),
            )
            error = when (e.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Lost the connection. Check your network and try again."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                    "YouTube refused the stream${status?.let { " (HTTP $it" + (client?.let { c -> " · $c" } ?: "") + ")" } ?: ""}. Try again."
                else -> "This video couldn't be played."
            }
        }
    }

    private fun takeVideoSize(videoSize: VideoSize) {
        if (videoSize.width <= 0 || videoSize.height <= 0) return
        videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        aspectFromFrames = true

        val w = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt()
        describeFormat(w, videoSize.height)
    }


    /**
     * What is really playing and how the connection keeps up, for the stats under the quality
     * list: the picture and sound being decoded, how far ahead is loaded, the measured speed,
     * dropped frames and stalls.
     */
    fun playbackStats(): List<Pair<String, String>> {
        val c = controller ?: return emptyList()
        fun selected(type: Int) = c.currentTracks.groups
            .filter { it.type == type && it.isSelected }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSelected(it) }.map { g.getTrackFormat(it) } }
            .firstOrNull()
        fun codec(raw: String?): String? {
            val s = raw?.lowercase(java.util.Locale.US) ?: return null
            return when {
                s.startsWith("av01") || "av1" in s -> "AV1"
                s.startsWith("vp09") || "vp9" in s -> "VP9"
                s.startsWith("avc") || "avc" in s -> "H.264"
                s.startsWith("hev") || s.startsWith("hvc") -> "HEVC"
                "opus" in s -> "Opus"
                s.startsWith("mp4a") || "aac" in s -> "AAC"
                else -> raw
            }
        }
        fun mbps(bps: Int) = String.format(java.util.Locale.US, "%.1f Mbps", bps / 1_000_000f)
        val out = ArrayList<Pair<String, String>>()
        selected(C.TRACK_TYPE_VIDEO)?.let { f ->
            out += "Video" to listOfNotNull(
                if (f.width > 0 && f.height > 0) "${f.width}×${f.height}" else null,
                f.frameRate.takeIf { it > 0f }?.let { "${it.roundToInt()} fps" },
                codec(f.codecs ?: f.sampleMimeType),
                f.bitrate.takeIf { it > 0 }?.let(::mbps),
            ).joinToString(" · ")
        }
        selected(C.TRACK_TYPE_AUDIO)?.let { f ->
            out += "Audio" to listOfNotNull(
                codec(f.codecs ?: f.sampleMimeType),
                f.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
            ).joinToString(" · ")
        }
        val ahead = (c.bufferedPosition - c.currentPosition).coerceAtLeast(0L) / 1000f
        out += "Loaded ahead" to String.format(java.util.Locale.US, "%.0f s", ahead)
        val kbps = PlaybackBandwidth.measuredKbps()
        out += "Connection" to if (kbps > 0) mbps(kbps * 1000) + " measured" else "measuring"
        out += "Dropped frames" to PlaybackStats.droppedFrames.toString()
        out += "Stalls" to PlaybackStats.stalls.toString()
        return out
    }

    /** Reads the codec and bitrate of the audio actually selected, for the music player's badge. */
    private fun describeAudio() {
        val format = controller?.currentTracks?.groups.orEmpty()
            .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSelected(it) }.map { g.getTrackFormat(it) } }
            .firstOrNull()
        if (format == null) {
            playingAudio = null
            return
        }
        val codecs = (format.codecs ?: format.sampleMimeType ?: "").lowercase(java.util.Locale.US)
        val codec = when {
            "opus" in codecs -> "OPUS"
            "mp4a" in codecs || "aac" in codecs -> "AAC"
            "vorbis" in codecs -> "VORBIS"
            "ac-3" in codecs || "ec-3" in codecs -> "DOLBY"
            else -> codecs.substringAfterLast('/').uppercase(java.util.Locale.US).ifBlank { "AUDIO" }
        }
        val bits = listOf(format.averageBitrate, format.bitrate, format.peakBitrate).firstOrNull { it > 0 }
        playingAudio = if (bits != null) codec + " · " + ((bits + 500) / 1000) + " KBPS" else codec
    }

    private fun describeFormat(width: Int, height: Int) {
        val tier = qualityTier(width, height)
        if (decodedHeight != tier) {
            Log.i("YTXTubeTime", "now ${tier}p (${width}×${height}) at +${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
        }
        decodedHeight = tier
        val formats = controller?.currentTracks?.groups.orEmpty()
            .filter { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it) } }
        val format = formats.firstOrNull { it.height == height }
        if (format == null) {
            playingFormat = "${tier}p"
            return
        }
        run {
            val codec = when {
                format.codecs?.startsWith("avc") == true -> "H.264"
                format.codecs?.startsWith("vp9") == true || format.codecs?.startsWith("vp09") == true -> "VP9"
                format.codecs?.startsWith("av01") == true -> "AV1"
                else -> format.codecs ?: format.sampleMimeType ?: "?"
            }
            val auto = if (formats.size > 1) "Auto" else null
            val labelTier = qualityTier(format.width, format.height)
            val codecText = format.codecs?.lowercase(java.util.Locale.US).orEmpty()
            val hdr = format.colorInfo?.let {
                it.colorTransfer == C.COLOR_TRANSFER_ST2084 || it.colorTransfer == C.COLOR_TRANSFER_HLG ||
                    it.hdrStaticInfo != null
            } == true || codecText.contains("vp09.02") || codecText.contains("vp9.2") ||
                (codecText.startsWith("av01") &&
                    (codecText.split('.').getOrNull(3)?.take(2)?.toIntOrNull() ?: 0) >= 10)
            playingFormat = listOfNotNull(
                auto,
                "${labelTier}p",
                if (hdr) "HDR" else null,
                codec,
                format.id?.substringAfterLast(":")?.let { "itag $it" },
            ).joinToString(" · ")
        }
    }
}

@Composable
fun rememberPlayerState(): PlayerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { PlayerState(scope) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { controller ->
                state.controller = controller
                controller.addListener(state.listener)
                state.onConnected()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            state.controller?.removeListener(state.listener)
            MediaController.releaseFuture(future)
            state.controller = null
        }
    }

    LaunchedEffect(state.isPlaying) {
        while (isActive && state.isPlaying) {
            state.syncFromPlayer()
            delay(250)
        }
    }
    // Word-by-word captions need a much finer clock than the 250 ms UI sync above. Only the
    // caption text is recomputed here, and it only triggers a redraw when the words change.
    LaunchedEffect(state.isPlaying, state.selectedSubtitleLanguage) {
        while (isActive && state.isPlaying && state.selectedSubtitleLanguage != null) {
            state.updateCaption()
            delay(CAPTION_TICK_MS)
        }
    }
    return state
}

enum class PlayerSheet { Quality, Comments, Queue, Settings, Speed, Audio, Subtitles, Chapters }

private const val TAG = "YTXTube"
private const val CAPTION_TICK_MS = 50L
