package com.tensei.ytxtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.TextureView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.Stream
import java.io.ByteArrayInputStream
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.PreloadException
import androidx.media3.exoplayer.source.preload.PreloadManagerListener
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.math.absoluteValue


@Stable
private class ShortsFeedState(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val seed: VideoItem? = null,
    restored: ShortsWarmup.Kept? = null,
) {
    var videos by mutableStateOf(restored?.videos ?: seed?.let { listOf(it) } ?: emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var next: String? = restored?.next
    private var loaded = restored != null

    private val seen = HashSet<String>().apply { restored?.seen?.let(::addAll) }

    val nextToken: String? get() = next
    val seenIds: Set<String> get() = seen

    fun load(ctx: Context? = null) {
        if (loading || loaded) return
        loading = true
        error = null
        scope.launch {
            // The first page is usually fetched already, a few seconds after the app started.
            val warmed = ShortsWarmup.takeFirstPage()?.let { page ->
                // Anything watched since it was fetched is left out.
                page.copy(videos = page.videos.filter { !SeenShorts.isSeen(it.id) && it.id !in seen })
            }?.takeIf { it.videos.size >= 4 }
            runCatching { warmed ?: InnerTube.shorts(null, seen) }
                .onSuccess { page ->
                    videos = merge(page.videos)
                    seen += videos.map { it.id }
                    next = page.next
                    loaded = true
                    if (videos.isEmpty()) error = "No Shorts found for this account."
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun refresh(ctx: Context? = null) {
        if (refreshing || loading) return
        refreshing = true
        error = null
        scope.launch {
            val token = next
            val page = runCatching { InnerTube.newShorts(seen, token) }.getOrNull()
            if (page != null && page.videos.isNotEmpty()) {
                val fresh = page.videos.filter { it.id !in seen }
                val list = if (fresh.isNotEmpty()) fresh else page.videos
                videos = list
                seen += list.map { it.id }
                next = page.next ?: token
                loaded = true
                error = null
            } else {
                error = "Couldn't load new Shorts. Check your connection."
            }
            refreshing = false
        }
    }

    private fun merge(page: List<VideoItem>): List<VideoItem> {
        val merged = LinkedHashMap<String, VideoItem>()
        seed?.let { merged[it.id] = it }
        page.forEach { merged.putIfAbsent(it.id, it) }
        return merged.values.toList()
    }

    fun loadMore(ctx: Context? = null) {
        if (loading) return
        loading = true
        scope.launch {
            val token = next
            runCatching { InnerTube.shorts(token, seen) }
                .onSuccess { page ->
                    val known = videos.mapTo(HashSet()) { it.id }
                    val newItems = page.videos.filter { known.add(it.id) }
                    if (newItems.isNotEmpty()) {
                        videos = videos + newItems
                        seen += newItems.map { it.id }
                    }
                    next = page.next ?: "anon_shorts:${(videos.size / 10) % 9}:start"
                }
            loading = false
        }
    }
}

@Composable
fun ShortsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSearch: () -> Unit,
    bottomInset: Dp = 0.dp,
    startItem: VideoItem? = null,
    active: Boolean = true,
    onOpenChannel: (String) -> Unit = {},
    onCommentsOpenChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val view = androidx.compose.ui.platform.LocalView.current
    val dark = isDarkTheme()
    DisposableEffect(dark) {
        val activity = view.context as? androidx.activity.ComponentActivity
        val window = activity?.window ?: (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !dark
            controller?.isAppearanceLightNavigationBars = !dark
            if (activity != null) {
                val barStyle = if (dark) {
                    androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                runCatching {
                    activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                    controller?.isAppearanceLightStatusBars = !dark
                    controller?.isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    // Coming back to the Shorts tab picks up where you left it (same feed, same Short).
    val restored = remember(startItem?.id) { if (startItem == null) ShortsWarmup.restore() else null }
    val feed = remember(startItem?.id) { ShortsFeedState(scope, startItem, restored) }
    LaunchedEffect(feed) {
        ShortsWarmup.noteUsed(context)
        feed.load(context)
    }

    val pager = rememberPagerState(initialPage = restored?.page ?: 0, pageCount = { feed.videos.size })
    // Stills of the Shorts around this one: the first frame of the next ones (made by a hidden
    // second player) and the last frame of the ones you left, shown instead of thumbnails so a
    // swipe goes straight from video to video.
    val frames = remember { ShortsFrames() }
    val grabber = remember { ShortsFrameGrabber(context) }
    val mainView = remember { ShortsViewRef() }
    // The Short that plays. It moves as soon as a swipe is let go (not when it has settled), so
    // the next Short is already starting while it slides in.
    var activePage by remember { mutableIntStateOf(restored?.page ?: 0) }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    DisposableEffect(feed) {
        onDispose {
            if (startItem == null) ShortsWarmup.keep(feed.videos, feed.nextToken, feed.seenIds, pager.settledPage)
        }
    }
    // As soon as a swipe is let go, start resolving the Short it is heading to, before it settles.
    LaunchedEffect(pager, feed) {
        snapshotFlow { pager.targetPage }.collect { target ->
            feed.videos.getOrNull(target)?.let { StreamCache.prefetch(it.id) }
            feed.videos.getOrNull(target + 1)?.let { StreamCache.prefetch(it.id) }
        }
    }
    val page = if (feed.videos.isEmpty()) 0 else activePage.coerceIn(0, feed.videos.lastIndex)
    val current = feed.videos.getOrNull(page)

    LaunchedEffect(page, feed.videos.size) {
        if (feed.videos.isNotEmpty() && page >= feed.videos.lastIndex - 3) {
            feed.loadMore(context)
        }
    }

    // One shared preload manager and player: the next Short is prepared inside the
    // manager, and the wrapped source is handed to the player so a swipe starts on
    // samples that are already buffered instead of rebuilding the source.
    val preload = remember { ShortsPreloader(context) }
    val exo = preload.player
    // Auto-advance (Settings): when a Short has played to the end, slide to the next one instead
    // of looping it.
    DisposableEffect(exo, pager) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION || !ShortsPrefs.autoAdvance) return
                if (pager.isScrollInProgress) return
                val next = pager.currentPage + 1
                if (next < pager.pageCount) scope.launch { pager.animateScrollToPage(next) }
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }
    DisposableEffect(Unit) {
        onDispose {
            exo.release()
            preload.release()
            grabber.release()
        }
    }

    val pip = LocalPictureInPicture.current
    val inPip = pip?.active == true

    DisposableEffect(exo) {
        (context as? MainActivity)?.shortsPlayerHolder = exo
        onDispose {
            if ((context as? MainActivity)?.shortsPlayerHolder == exo) {
                (context as? MainActivity)?.shortsPlayerHolder = null
            }
            pip?.updateAutoPip(false)
        }
    }

    LaunchedEffect(Unit) {
        pip?.updateAutoPip(false)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exo) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                exo.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var resumeAfterOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) {
            resumeAfterOverlay = exo.playWhenReady
            exo.pause()
        } else if (resumeAfterOverlay) {
            resumeAfterOverlay = false
            exo.playWhenReady = true
        }
    }

    var info by remember { mutableStateOf<StreamInfo?>(null) }
    // Which Short the player has actually drawn a frame of; until then its still covers the video.
    var renderedId by remember { mutableStateOf<String?>(null) }
    // The shape of the playing Short, so the progress bar can span the video and not the screen.
    var videoAspect by remember { mutableFloatStateOf(9f / 16f) }
    var playError by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var liked by remember { mutableStateOf(false) }
    var disliked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableLongStateOf(-1L) }
    var dislikes by remember { mutableLongStateOf(-1L) }
    var likeBump by remember { mutableIntStateOf(0) }
    var dislikeBump by remember { mutableIntStateOf(0) }
    var subscribed by remember { mutableStateOf(false) }
    val comments = remember { CommentsState(scope) }
    var commentsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(commentsOpen) { onCommentsOpenChange(commentsOpen) }
    var downloadOpen by remember { mutableStateOf(false) }
    var overlayWasPlaying by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    fun togglePlayback() {
        // While the player is buffering, a tap should keep playback wanted
        // instead of turning playWhenReady off and creating the old dead pause.
        if (exo.playWhenReady && exo.playbackState == Player.STATE_READY) {
            exo.pause()
        } else {
            exo.playWhenReady = true
        }
    }

    fun pauseForOverlay() {
        overlayWasPlaying = exo.playWhenReady
        if (overlayWasPlaying) exo.pause()
    }

    fun resumeAfterOverlayPanel() {
        if (overlayWasPlaying && active) exo.playWhenReady = true
        overlayWasPlaying = false
    }


    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            // The pause/play overlay follows playWhenReady, not isPlaying. While
            // ExoPlayer is buffering isPlaying is false even though playback is
            // wanted, which used to show a dead pause button that could not resume.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playing = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    loading = false
                    playError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loading = false
                playError = error.message ?: "Playback failed."
            }

            override fun onTracksChanged(tracks: Tracks) {
                // The player now owns the period, so the preload manager can drop
                // its reference without clearing the samples the player is using.
                if (tracks.groups.isNotEmpty()) {
                    preload.releaseAdoptedIfCurrent(exo.currentMediaItem?.mediaId)
                }
            }

            override fun onRenderedFirstFrame() {
                if (!mainView.acceptFrames) return
                val id = exo.currentMediaItem?.mediaId
                // Two frames later the video view is showing it, so the still can go without a
                // flash of black in between.
                scope.launch {
                    withFrameNanos { }
                    withFrameNanos { }
                    renderedId = id
                }
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // The Short being left keeps its last frame on screen while it slides away (and if you come
    // back to it, until it plays again from where you were).
    fun captureLeaving(id: String?) {
        val view = mainView.view ?: return
        if (id == null || renderedId != id || exo.currentMediaItem?.mediaId != id) return
        if (!view.isAvailable) return
        val size = exo.videoSize
        val (w, h) = stillSize(size.width, size.height)
        val bmp = runCatching { view.getBitmap(w, h) }.getOrNull() ?: return
        frames.putLast(id, bmp.asImageBitmap(), exo.currentPosition)
    }

    // Switches the player to another Short right away, before its page is drawn with the video
    // view. The old Short is taken out first, so not a single frame of it can land in the new
    // Short's view (that was the wrong-video flash when swiping up and down).
    fun startShort(id: String?) {
        renderedId = null
        mainView.closeGate()
        exo.pause()
        exo.clearVideoSurface()
        exo.clearMediaItems()
        if (id == null) return
        val loaded = StreamCache.get(id) ?: return
        val source = preload.take(id) ?: shortsSource(context, loaded, id) ?: return
        exo.setMediaSource(source, frames.lastPosition(id))
        exo.prepare()
        exo.playWhenReady = active
    }

    LaunchedEffect(pager, feed) {
        snapshotFlow { Triple(pager.isScrollInProgress, dragged, pager.currentPage) to pager.settledPage }
            .collect { (live, settled) ->
                val (scrolling, holding, nearest) = live
                val next = when {
                    !scrolling -> settled
                    // Let go: the page it is sliding to plays now.
                    !holding -> nearest
                    // Still under the finger: nothing changes yet.
                    else -> activePage
                }
                if (next != activePage) {
                    captureLeaving(feed.videos.getOrNull(activePage)?.id)
                    startShort(feed.videos.getOrNull(next)?.id)
                    activePage = next
                }
            }
    }
    LaunchedEffect(exo) {
        while (true) {
            position = exo.currentPosition.coerceAtLeast(0L)
            duration = exo.duration.takeIf { it > 0 } ?: 0L
            delay(200)
        }
    }

    val currentId = current?.id
    LaunchedEffect(currentId) {
        if (currentId == null) return@LaunchedEffect
        SeenShorts.markSeen(context, currentId)
        // Usually the swipe has already started this Short (see startShort).
        val started = exo.currentMediaItem?.mediaId == currentId
        loading = !started || exo.playbackState != Player.STATE_READY
        if (!started) {
            renderedId = null
            exo.pause()
        }
        info = null
        playError = null
        position = 0L
        duration = 0L
        liked = false
        disliked = false
        likeCount = -1L
        dislikes = -1L
        likeBump = 0
        dislikeBump = 0
        subscribed = false
        commentsOpen = false
        downloadOpen = false
        scrubbing = false

        launch {
            val count = withContext(Dispatchers.IO) { fetchDislikeCount(currentId) }
            if (current?.id == currentId) dislikes = count
        }
        val loaded = StreamCache.get(currentId) ?: withContext(Dispatchers.IO) {
            runCatching { StreamCache.getOrLoad(currentId) }.getOrNull()
        }
        info = loaded
        if (loaded == null) {
            loading = false
            playError = "Couldn't load this Short."
            return@LaunchedEffect
        }
        likeCount = loaded.likeCount
        if (exo.currentMediaItem?.mediaId != currentId) {
            val source = preload.take(currentId) ?: shortsSource(context, loaded, currentId)
            if (source == null) {
                loading = false
                playError = "This Short has no playable stream."
                return@LaunchedEffect
            }
            renderedId = null
            // Back to a Short you swiped away from: carry on where you were.
            val resumeAt = frames.lastPosition(currentId)
            exo.setMediaSource(source, resumeAt)
            exo.prepare()
            exo.play()
        }
        if (Account.signedIn) {
            runCatching { InnerTube.videoState(currentId) }.getOrNull()?.let { st ->
                liked = st.like == LikeStatus.Like
                disliked = st.like == LikeStatus.Dislike
                subscribed = st.subscribed == true
            }
        }
    }

    // Resolve several Shorts ahead while this one plays and hand them to Media3's
    // preload manager. It buffers the first seconds of each source in rank order;
    // a swipe then adopts that wrapped source instead of rebuilding it.
    LaunchedEffect(feed.videos, page) {
        val upcoming = feed.videos.drop(page + 1).take(6)
        val keep = upcoming.mapTo(HashSet()) { it.id }
        feed.videos.getOrNull(page)?.let { keep.add(it.id) }
        preload.setCurrentPlayingIndex(page)
        preload.keepOnly(keep)
        if (upcoming.isEmpty()) return@LaunchedEffect
        // Thumbnails of the next Shorts in the image cache, for any Short whose first frame is
        // not ready yet, so its page is never empty while it slides in.
        runCatching {
            val images = coil.Coil.imageLoader(context)
            upcoming.forEach { next ->
                images.enqueue(
                    coil.request.ImageRequest.Builder(context)
                        .data(next.thumbnail ?: feedThumbnailUrl(next.id))
                        .build(),
                )
            }
        }
        // The Short on screen first: the ones after it start once it is playing (or after a
        // moment), so they never slow down the one you are waiting for.
        withTimeoutOrNull(2_500) { snapshotFlow { loading }.first { !it } }

        withContext(Dispatchers.IO) { upcoming.forEach { StreamCache.prefetch(it.id) } }
        upcoming.forEachIndexed { index, item ->
            if (preload.has(item.id)) return@forEachIndexed
            val loaded = withContext(Dispatchers.IO) {
                runCatching { StreamCache.getOrLoad(item.id) }.getOrNull()
            } ?: return@forEachIndexed
            val source = shortsSource(context, loaded, item.id) ?: return@forEachIndexed
            preload.prepare(item.id, source, page + 1 + index)
        }
    }

    // First frames of the next Shorts (and the one before, if it was never played), made one at
    // a time by a hidden, muted player once this Short is playing.
    LaunchedEffect(feed.videos, page) {
        withTimeoutOrNull(2_500) { snapshotFlow { loading }.first { !it } }
        delay(200)
        val targets = feed.videos.drop(page + 1).take(4) + listOfNotNull(feed.videos.getOrNull(page - 1))
        for (item in targets) {
            if (frames.has(item.id)) continue
            val loaded = StreamCache.get(item.id) ?: withContext(Dispatchers.IO) {
                runCatching { StreamCache.getOrLoad(item.id) }.getOrNull()
            } ?: continue
            val source = shortsPreviewSource(context, loaded, item.id) ?: continue
            val still = grabber.grab(source) ?: continue
            frames.putFirst(item.id, still)
        }
    }

    LaunchedEffect(muted) { exo.volume = if (muted) 0f else 1f }

    fun requireSignIn(): Boolean {
        if (Account.signedIn) return true
        onSignIn()
        return false
    }

    fun share(item: VideoItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/shorts/${item.id}")
        }
        context.startActivity(Intent.createChooser(intent, "Share Short"))
    }

    val glassTint = glassOverlayFill(0.5f)
    // Unfolded (or any wide screen) the comments open as a panel beside the buttons instead of
    // over the bottom of the screen.
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val sideComments = screenWidth >= 600
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sidePanelWidth = (screenWidth.dp * 0.46f).coerceIn(320.dp, 520.dp)
    val sideOpen = commentsOpen && sideComments
    // With the comments open the bottom bar is put away, so its space goes to the Short.
    val bottomSpace by animateDpAsState(
        targetValue = if (commentsOpen) minOf(bottomInset, navBottom + 12.dp) else bottomInset,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
        label = "shortsBottom",
    )
    // The whole feed, recorded so the comments slide up over it as frosted glass.
    val feedBackdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The hidden player's view, where first frames are drawn to be copied. It sits under
        // everything, behind a black cover, so it is never seen.
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { grabber.attach(it) } },
            modifier = Modifier.size(180.dp, 320.dp),
            onRelease = { view -> grabber.detach(view) },
        )
        Box(Modifier.size(180.dp, 320.dp).background(Color.Black))
        if (feed.videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (feed.loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            feed.error ?: "No Shorts right now.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        PillButton("Retry", { feed.load(context) })
                    }
                }
            }
        } else {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().layerBackdrop(feedBackdrop),
                beyondViewportPageCount = 1,
                pageSpacing = 0.dp,
            ) { index ->
                val item = feed.videos.getOrNull(index) ?: return@VerticalPager
                val pageFocus = remember { FocusRequester() }
                var selfFocused by remember { mutableStateOf(false) }
                LaunchedEffect(index, page) {
                    if (TvMode.isTv && index == page && feed.videos.isNotEmpty()) {
                        runCatching { pageFocus.requestFocus() }
                    }
                }
                val backdrop = rememberLayerBackdrop()
                val glass = glassSpec(
                    shape = CircleShape,
                    tint = glassTint,
                    blur = 16.dp,
                    refractionHeight = 9.dp,
                    refractionAmount = 14.dp,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Read while drawing, so scrolling does not recompose every page.
                            val pageOffset = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
                                .absoluteValue.coerceIn(0f, 1f)
                            val s = 1f - 0.05f * pageOffset
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.3f * pageOffset
                        }
                        .then(
                            if (TvMode.isTv) Modifier
                                .focusRequester(pageFocus)
                                .onFocusChanged { selfFocused = it.isFocused }
                                .onPreviewKeyEvent { e ->
                                    if (!selfFocused || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.DirectionUp -> {
                                            if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (pager.currentPage < feed.videos.lastIndex) {
                                                scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                                            }
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            togglePlayback()
                                            true
                                        }
                                        Key.MediaPlayPause -> {
                                            togglePlayback()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            focusManager.moveFocus(FocusDirection.Right)
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .focusable()
                            else Modifier,
                        )
                        .background(Color.Black),
                ) {
                    // Everything the glass samples lives in this layer. The buttons are drawn
                    // outside it so a backdrop can never contain the surface that samples it.
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    // The still of a Short (where you left it, or its first frame) in exactly the
                    // place and size of its video, so the video simply carries on from it.
                    val still = frames.last(item.id) ?: frames.first(item.id)
                    if (index == page && playError == null) {
                        // Under the video too: the view is see-through until it has a frame, so
                        // there is never black in between.
                        ShortsStill(item, still, Modifier.fillMaxSize())
                        ShortsVideo(exo, Modifier.fillMaxSize(), onView = { mainView.view = it })
                        if (renderedId != item.id) {
                            ShortsStill(item, still, Modifier.fillMaxSize())
                        }
                    } else {
                        ShortsStill(item, still, Modifier.fillMaxSize())
                    }


                    if (!inPip) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                                    ),
                                ),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                                    ),
                                ),
                        )
                    }
                    }

                    var is2xSpeed by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(index == page) {
                                if (index != page) return@pointerInput
                                // Hold anywhere on the Short for 2x speed. Letting go of a hold is
                                // not a tap, so it no longer pauses the Short.
                                var heldFast = false
                                detectTapGestures(
                                    // With the comments open, a tap anywhere on the Short puts
                                    // them away (and does not pause).
                                    onTap = {
                                        when {
                                            heldFast -> Unit
                                            commentsOpen -> commentsOpen = false
                                            else -> togglePlayback()
                                        }
                                    },
                                    onPress = {
                                        heldFast = false
                                        var activated = false
                                        val holdJob = scope.launch {
                                            kotlinx.coroutines.delay(220)
                                            activated = true
                                            heldFast = true
                                            is2xSpeed = true
                                            exo.setPlaybackSpeed(2.0f)
                                        }
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            holdJob.cancel()
                                            if (activated) {
                                                exo.setPlaybackSpeed(1.0f)
                                                is2xSpeed = false
                                            }
                                        }
                                    }
                                )
                            },
                    )

                    if (is2xSpeed && index == page) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 70.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.FastForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "2X Speed",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                )
                            }
                        }
                    }

                    if (index == page && playError == null && (loading || (buffering && exo.playWhenReady))) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(36.dp).align(Alignment.Center),
                        )
                    }

                    if (index == page && !playing && !loading && playError == null) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .align(Alignment.Center)
                                .pressable(pressedScale = 0.9f) { exo.playWhenReady = true }
                                .clip(CircleShape)
                                .liquidGlass(backdrop, glass),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }

                    if (playError != null && index == page) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(playError.orEmpty(), color = Color.White, fontSize = 14.sp)
                                Spacer(Modifier.height(12.dp))
                                PillButton("Retry", {
                                    info = null
                                    loading = true
                                    playError = null
                                    scope.launch {
                                        val retryId = item.id
                                        val loaded = withContext(Dispatchers.IO) {
                                            runCatching { StreamCache.getOrLoad(retryId) }.getOrNull()
                                        }
                                        info = loaded
                                        val source = loaded?.let { shortsSource(context, it, retryId) }
                                        if (source != null) {
                                            playError = null
                                            exo.setMediaSource(source)
                                            exo.prepare()
                                            exo.play()
                                        } else {
                                            loading = false
                                        }
                                    }
                                })
                            }
                        }
                    }

                    if (!inPip) {
                        ShortsTopBar(
                            onBack = onBack,
                            muted = muted,
                            onToggleMute = { muted = !muted },
                            onSearch = onSearch,
                            refreshing = feed.refreshing,
                            onRefresh = {
                                feed.refresh(context)
                                scope.launch { if (feed.videos.isNotEmpty()) pager.scrollToPage(0) }
                            },
                            backdrop = backdrop,
                            glass = glass,
                        )
                    }

                    if (index == page && !inPip) {
                        val currentLikeCount = if (likeCount >= 0) (likeCount + likeBump).coerceAtLeast(0L) else if (liked) 1L else -1L
                        val currentDislikes = if (dislikes >= 0) (dislikes + dislikeBump).coerceAtLeast(0L) else if (disliked) 1L else -1L
                        // With the comments open beside the video (unfolded), the buttons go to the
                        // top, just left of the comments, labels tucked away, so the comments can
                        // use the whole height.
                        val railUp = sideOpen
                        val railBias by animateFloatAsState(
                            targetValue = if (railUp) -1f else 1f,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                            label = "railBias",
                        )
                        val railShift by animateDpAsState(
                            targetValue = if (railUp) sidePanelWidth + 8.dp else 0.dp,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                            label = "railShift",
                        )
                        ShortsRail(
                            modifier = Modifier
                                .align(androidx.compose.ui.BiasAlignment(1f, railBias))
                                .padding(
                                    end = 8.dp,
                                    top = statusTop + RailTopGap,
                                    bottom = bottomSpace + 22.dp,
                                )
                                .graphicsLayer { translationX = -railShift.toPx() },
                            compact = railUp,
                            item = item,
                            info = info,
                            liked = liked,
                            disliked = disliked,
                            likeCount = currentLikeCount,
                            dislikes = currentDislikes,
                            onLike = {
                                val beforeLiked = liked
                                val beforeDisliked = disliked
                                val next = !beforeLiked
                                liked = next
                                if (next) disliked = false
                                likeBump += (if (next) 1 else 0) - (if (beforeLiked) 1 else 0)
                                if (beforeDisliked) dislikeBump -= 1
                                if (Account.signedIn) {
                                    val target = if (next) LikeStatus.Like else LikeStatus.None
                                    scope.launch {
                                        runCatching { InnerTube.setLike(item.id, target) }
                                    }
                                }
                            },
                            onDislike = {
                                val beforeLiked = liked
                                val beforeDisliked = disliked
                                val next = !beforeDisliked
                                disliked = next
                                if (next) liked = false
                                dislikeBump += (if (next) 1 else 0) - (if (beforeDisliked) 1 else 0)
                                if (beforeLiked) likeBump -= 1
                                if (Account.signedIn) {
                                    val target = if (next) LikeStatus.Dislike else LikeStatus.None
                                    scope.launch {
                                        runCatching { InnerTube.setLike(item.id, target) }
                                    }
                                }
                            },
                            onShare = { share(item) },
                            onComments = {
                                // The Short keeps playing while the comments are open.
                                if (commentsOpen) {
                                    commentsOpen = false
                                } else {
                                    comments.load(item.id)
                                    commentsOpen = true
                                }
                            },
                            onDownload = {
                                pauseForOverlay()
                                downloadOpen = true
                            },
                            backdrop = backdrop,
                            glass = glass,
                        )

                        ShortsDetails(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(
                                    start = 16.dp,
                                    end = if (sideOpen) sidePanelWidth + 24.dp else 82.dp,
                                    bottom = bottomSpace + 20.dp,
                                ),
                            item = item,
                            info = info,
                            subscribed = subscribed,
                            onSubscribe = {
                                if (!requireSignIn()) return@ShortsDetails
                                val channelId = info?.uploaderUrl?.substringAfter("/channel/")?.substringBefore('?')
                                    ?.takeIf { it.startsWith("UC") }
                                if (channelId == null) {
                                    Toast.makeText(context, "Channel unavailable", Toast.LENGTH_SHORT).show()
                                    return@ShortsDetails
                                }
                                val target = !subscribed
                                subscribed = target
                                scope.launch {
                                    runCatching { InnerTube.setSubscribed(channelId, target) }.onFailure {
                                        subscribed = !target
                                        Toast.makeText(context, "Couldn't update subscription.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onOpenChannel = {
                                val url = info?.uploaderUrl
                                    ?: item.channelUrl
                                    ?: item.channelId()?.let { "https://www.youtube.com/channel/$it" }
                                if (url != null) {
                                    exo.pause()
                                    onOpenChannel(url)
                                } else {
                                    Toast.makeText(context, "Channel unavailable", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )


                        if (duration > 0) BoxWithConstraints(Modifier.fillMaxSize()) {
                            val shownPosition = if (scrubbing) scrubPosition else position
                            val fraction = (shownPosition.toFloat() / duration).coerceIn(0f, 1f)
                            // Under the video itself: as wide as the video (not across the black
                            // sides on a wide screen), and stopping short of the comments.
                            val videoW = minOf(maxWidth, maxHeight * videoAspect)
                            val left = (maxWidth - videoW) / 2
                            val right = if (sideOpen) minOf(left + videoW, maxWidth - sidePanelWidth - 20.dp) else left + videoW
                            val barW = (right - left).coerceAtLeast(96.dp)
                            Canvas(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = left)
                                    .width(barW)
                                    .padding(bottom = bottomSpace + 2.dp)
                                    .height(20.dp)
                                    .pointerInput(duration) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var target = (down.position.x / size.width * duration)
                                                .toLong().coerceIn(0L, duration)
                                            var moved = false
                                            scrubbing = true
                                            scrubPosition = target
                                            try {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (!change.pressed) break
                                                    if (kotlin.math.abs(change.position.x - down.position.x) > 4f) moved = true
                                                    if (moved) {
                                                        target = (change.position.x / size.width * duration)
                                                            .toLong().coerceIn(0L, duration)
                                                        scrubPosition = target
                                                        exo.seekTo(target)
                                                        change.consume()
                                                    }
                                                }
                                                if (!moved) exo.seekTo(target)
                                                position = target
                                            } finally {
                                                scrubbing = false
                                            }
                                        }
                                    },
                            ) {
                                val y = size.height / 2f
                                val stroke = if (scrubbing) 4.dp.toPx() else 2.5.dp.toPx()
                                drawLine(
                                    Color.White.copy(alpha = 0.24f),
                                    Offset(0f, y),
                                    Offset(size.width, y),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    Color.White.copy(alpha = 0.92f),
                                    Offset(0f, y),
                                    Offset(size.width * fraction, y),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round,
                                )
                                // The dot only while you drag; otherwise just a clean line.
                                if (scrubbing) {
                                    drawCircle(
                                        Color.White,
                                        radius = 6.dp.toPx(),
                                        center = Offset(size.width * fraction, y),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = feed.refreshing,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    I18n.t("finding_shorts", "Finding new Shorts…"),
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (sideOpen) {
            // Unfolded: a panel down the whole right side, from under the top buttons to the
            // bottom (the bottom bar is put away), with the Short playing in place beside it.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val top = statusTop + RailTopGap
                val bottomPad = navBottom + 12.dp
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = bottomPad)
                        .width(sidePanelWidth)
                        .height((maxHeight - top - bottomPad).coerceAtLeast(260.dp)),
                ) {
                    CommentsPanel(
                        state = comments,
                        partialTop = 0.dp,
                        onClose = { commentsOpen = false },
                        creatorAvatar = info?.uploaderAvatars?.maxByOrNull { it.width }?.url,
                        creatorName = info?.uploaderName.orEmpty(),
                        backdrop = feedBackdrop,
                        floating = true,
                        accentVideoId = current?.id,
                        accentThumbnail = current?.thumbnail,
                    )
                }
            }
        } else if (commentsOpen) {
            CommentsPanel(
                state = comments,
                partialTop = 0.dp,
                onClose = { commentsOpen = false },
                creatorAvatar = info?.uploaderAvatars?.maxByOrNull { it.width }?.url,
                creatorName = info?.uploaderName.orEmpty(),
                backdrop = feedBackdrop,
                accentVideoId = current?.id,
                accentThumbnail = current?.thumbnail,
            )
        }

        if (downloadOpen) {
            current?.let { short ->
                DownloadQualitySheet(
                    video = short,
                    onDownloadStarted = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = {
                        downloadOpen = false
                        resumeAfterOverlayPanel()
                    },
                )
            }
        }
    }
}

@Composable
private fun ShortsTopBar(
    onBack: () -> Unit,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onSearch: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    backdrop: Backdrop,
    glass: GlassSpec,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortsCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, backdrop, glass)
        Spacer(Modifier.width(8.dp))
        Text(
            "Shorts",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        ShortsCircleButton(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Sound", onToggleMute, backdrop, glass)
        Spacer(Modifier.width(6.dp))
        ShortsCircleButton(
            Icons.Rounded.Refresh,
            if (refreshing) "Loading new Shorts" else "Load new Shorts",
            onRefresh,
            backdrop,
            glass,
            loading = refreshing,
            enabled = !refreshing,
        )
        Spacer(Modifier.width(6.dp))
        ShortsCircleButton(Icons.Rounded.Search, "Search", onSearch, backdrop, glass)
    }
}

@Composable
private fun ShortsCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    glass: GlassSpec,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .size(32.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .liquidGlass(backdrop, glass),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ShortsRail(
    modifier: Modifier,
    item: VideoItem,
    info: StreamInfo?,
    liked: Boolean,
    disliked: Boolean,
    likeCount: Long,
    dislikes: Long,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onShare: () -> Unit,
    onComments: () -> Unit,
    onDownload: () -> Unit,
    backdrop: Backdrop,
    glass: GlassSpec,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      androidx.compose.runtime.CompositionLocalProvider(LocalRailCompact provides compact) {
        ShortsAction(
            icon = Icons.Rounded.ThumbUp,
            label = countLabel(likeCount, I18n.t("like", "Like")),
            tint = if (liked) Color(0xFFFF2D55) else Color.White,
            active = liked,
            backdrop = backdrop,
            glass = glass,
            onClick = onLike,
        )
        ShortsAction(
            icon = Icons.Rounded.ThumbDown,
            label = countLabel(dislikes, I18n.t("dislike", "Dislike")),
            tint = if (disliked) Color(0xFFFF2D55) else Color.White,
            active = disliked,
            backdrop = backdrop,
            glass = glass,
            onClick = onDislike,
        )
        ShortsAction(Icons.Rounded.Comment, I18n.t("comments", "Comments"), backdrop = backdrop, glass = glass, onClick = onComments)
        ShortsAction(Icons.Rounded.Download, I18n.t("download", "Download"), backdrop = backdrop, glass = glass, onClick = onDownload)
        ShortsAction(Icons.Rounded.Share, I18n.t("share", "Share"), backdrop = backdrop, glass = glass, onClick = onShare)
      }
    }
}

/** True while the buttons are tucked up beside the comments: their labels are hidden. */
private val LocalRailCompact = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Room under the top bar for the buttons when they move up. */
private val RailTopGap = 64.dp


@Composable
private fun ShortsAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    active: Boolean = false,
    backdrop: Backdrop,
    glass: GlassSpec,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 450f),
        label = "actionScale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(38.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pressable(onClick = onClick)
                .clip(CircleShape)
                .liquidGlass(backdrop, glass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(visible = !LocalRailCompact.current) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ShortsDetails(
    modifier: Modifier,
    item: VideoItem,
    info: StreamInfo?,
    subscribed: Boolean,
    onSubscribe: () -> Unit,
    onOpenChannel: () -> Unit,
) {
    val channel = info?.uploaderName?.ifBlank { null } ?: item.channel.ifBlank { "YouTube" }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.pressable(onClick = onOpenChannel)) {
                ChannelAvatar(
                    info?.uploaderAvatars?.maxByOrNull { it.width }?.url ?: item.avatar,
                    channel,
                    size = 34,
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                channel,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .pressable(onClick = onOpenChannel),
            )
            if (!subscribed) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .pressable(onClick = onSubscribe)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(I18n.t("subscribe", "Subscribe"), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .pressable(onClick = onSubscribe)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(I18n.t("subscribed", "Subscribed"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            info?.name?.ifBlank { null } ?: item.title,
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                "Shorts",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun countLabel(value: Long, fallback: String = "Like"): String = when {
    value < 0 -> fallback
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f).replace(".0M", "M")
    value >= 1_000 -> String.format("%.1fK", value / 1_000f).replace(".0K", "K")
    value <= 0 -> fallback
    else -> value.toString()
}





/** Where the Short's video view is now, so its last frame can be copied when you swipe away. */
private class ShortsViewRef {
    var view: TextureView? = null

    /**
     * False from a switch until the main thread has caught up with everything the player sent
     * before it, so a "first frame" message from the Short being left is never taken as the new
     * Short's.
     */
    var acceptFrames = true
    private var gate = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun closeGate() {
        val mine = ++gate
        acceptFrames = false
        main.post { if (gate == mine) acceptFrames = true }
    }
}

/** Stills of Shorts: first frames from the hidden player, last frames of the ones you left. */
@Stable
private class ShortsFrames {
    private class Last(val image: ImageBitmap, val positionMs: Long)

    private val firsts = mutableStateMapOf<String, ImageBitmap>()
    private val lasts = mutableStateMapOf<String, Last>()
    private val order = ArrayDeque<String>()

    private fun touch(id: String) {
        order.remove(id)
        order.addLast(id)
        // About 2 MB each; keep the dozen nearest.
        while (order.size > 12) {
            val old = order.removeFirst()
            firsts.remove(old)
            lasts.remove(old)
        }
    }

    fun has(id: String) = firsts.containsKey(id) || lasts.containsKey(id)
    fun first(id: String): ImageBitmap? = firsts[id]
    fun last(id: String): ImageBitmap? = lasts[id]?.image
    fun lastPosition(id: String): Long = lasts[id]?.positionMs ?: 0L

    fun putFirst(id: String, image: ImageBitmap) {
        firsts[id] = image
        touch(id)
    }

    fun putLast(id: String, image: ImageBitmap, positionMs: Long) {
        lasts[id] = Last(image, positionMs)
        touch(id)
    }
}

/** The size a still is copied at: the video's shape, at most 540 pixels wide. */
private fun stillSize(videoW: Int, videoH: Int): Pair<Int, Int> {
    val w = if (videoW > 0) videoW else 9
    val h = if (videoH > 0) videoH else 16
    val outW = minOf(540, maxOf(w, 270))
    val outH = (outW * h.toFloat() / w).roundToInt().coerceAtLeast(1)
    return outW to outH
}

/**
 * A second, muted player that only loads a Short far enough to draw its first frame into a
 * hidden view, which is then copied as a still. Uses the same cached stream as the real player.
 */
@OptIn(UnstableApi::class)
private class ShortsFrameGrabber(context: Context) {
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(300, 800, 100, 100)
                .build(),
        )
        .setBandwidthMeter(PlaybackBandwidth.get())
        .build()
        .apply {
            volume = 0f
            playWhenReady = false
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        }

    private var view: TextureView? = null
    private var rendered: CompletableDeferred<Unit>? = null
    private var released = false

    init {
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                rendered?.complete(Unit)
            }
        })
    }

    fun attach(textureView: TextureView) {
        view = textureView
        runCatching { player.setVideoTextureView(textureView) }
    }

    fun detach(textureView: TextureView) {
        if (view === textureView) view = null
        runCatching { player.clearVideoTextureView(textureView) }
    }

    suspend fun grab(source: MediaSource): ImageBitmap? {
        if (released) return null
        val target = view ?: return null
        val done = CompletableDeferred<Unit>()
        rendered = done
        try {
            player.setMediaSource(source)
            player.prepare()
            withTimeoutOrNull(5_000) { done.await() } ?: return null
            // Let the view pick the frame up before copying it.
            delay(60)
            if (released || !target.isAvailable) return null
            val size = player.videoSize
            val (w, h) = stillSize(size.width, size.height)
            return runCatching { target.getBitmap(w, h) }.getOrNull()?.asImageBitmap()
        } finally {
            rendered = null
            if (!released) {
                runCatching {
                    player.stop()
                    player.clearMediaItems()
                }
            }
        }
    }

    fun release() {
        released = true
        runCatching { player.release() }
    }
}

/** A Short's still (or, before there is one, its thumbnail) fitted exactly like its video. */
@Composable
private fun ShortsStill(item: VideoItem, still: ImageBitmap?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val ratio = if (still != null && still.height > 0) still.width.toFloat() / still.height else 9f / 16f
        val containerRatio = if (maxHeight.value > 0f) maxWidth / maxHeight else ratio
        val w = if (ratio >= containerRatio) maxWidth else maxHeight * ratio
        val h = if (ratio >= containerRatio) maxWidth / ratio else maxHeight
        if (still != null) {
            Image(
                bitmap = still,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(w, h),
            )
        } else {
            AsyncImage(
                model = item.thumbnail ?: feedThumbnailUrl(item.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(w, h),
            )
        }
    }
}

@Composable
private fun ShortsVideo(player: ExoPlayer, modifier: Modifier = Modifier, onView: (TextureView?) -> Unit = {}) {
    var video by remember { mutableStateOf(VideoSize(0, 0)) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) video = videoSize
            }
        }
        player.addListener(listener)
        val current = player.videoSize
        if (current.width > 0 && current.height > 0) video = current
        onDispose { player.removeListener(listener) }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val ratio = if (video.width > 0 && video.height > 0) {
            video.width.toFloat() / video.height
        } else {
            9f / 16f
        }
        val containerRatio = if (maxHeight.value > 0f) maxWidth / maxHeight else ratio
        val videoWidth = if (ratio >= containerRatio) maxWidth else maxHeight * ratio
        val videoHeight = if (ratio >= containerRatio) maxWidth / ratio else maxHeight
        AndroidView(
            factory = { context ->



                TextureView(context).apply {
                    // See-through until the first frame arrives (the still underneath shows).
                    isOpaque = false
                    runCatching { player.setVideoTextureView(this) }
                    onView(this)
                }
            },
            modifier = Modifier.size(videoWidth, videoHeight),
            update = { },
            onRelease = { view ->
                runCatching { player.clearVideoTextureView(view) }
            },
        )
    }
}

/**
 * Owns the Shorts player and Media3's preload manager. Both come from one builder so
 * they share the playback looper, renderer capabilities, bandwidth meter and load
 * control; that is what lets [PreloadMediaSource] hand a prepared period straight to
 * the player on a swipe.
 */
@OptIn(UnstableApi::class)
private class ShortsPreloader(context: Context) {
    private val loadControl = DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(true, 64 * 1024))
        .setBufferDurationsMs(
            500,
            6_000,
            150,
            300,
        )
        .setBackBuffer(2_000, true)
        .setPrioritizeTimeOverSizeThresholds(true)
        .setTargetBufferBytes(12 * 1024 * 1024)
        .build()

    @Volatile
    private var currentIndex = 0

    private val builder = DefaultPreloadManager.Builder(
        context,
        TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> { rank ->
            // Rank is the absolute feed position; the manager orders by distance
            // from the current page. The immediate next Short gets the deepest
            // head start, the ones after it progressively less.
            val distance = (rank - currentIndex).coerceAtLeast(1)
            val targetMs = when (distance) {
                1 -> 6_000L
                2 -> 4_000L
                3 -> 3_000L
                else -> 2_000L
            }
            DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(targetMs)
        },
    )
        .setLoadControl(loadControl)
        .setBandwidthMeter(PlaybackBandwidth.get())
        .setDataSourceFactory(MediaCache.dataSourceFactory(context))

    private val manager = builder.build()

    val player: ExoPlayer = builder.buildExoPlayer(
        ExoPlayer.Builder(context).setBandwidthMeter(PlaybackBandwidth.get()),
    ).apply {
        videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        repeatMode = Player.REPEAT_MODE_ONE
        playWhenReady = true
    }

    private val owned = HashMap<String, MediaSource>()
    private val wrapped = HashMap<String, MediaSource>()
    private var adoptedId: String? = null
    private var adoptedWrapped: MediaSource? = null
    private var released = false

    init {
        manager.addListener(object : PreloadManagerListener {
            override fun onError(error: PreloadException) {
                val id = error.mediaItem.mediaId
                if (id.isBlank() || id == adoptedId) return
                removeInternal(id)
            }
        })
    }

    /**
     * The preload manager keys its holders by the wrapped [PreloadMediaSource], not by
     * the source handed to [DefaultPreloadManager.add], so always remove the wrapper.
     */
    private fun removeInternal(id: String) {
        val prepared = wrapped.remove(id)
        val raw = owned.remove(id)
        when {
            prepared != null -> runCatching { manager.remove(prepared) }
            raw != null -> runCatching { manager.remove(raw.mediaItem) }
        }
    }

    fun has(id: String): Boolean = owned.containsKey(id) || wrapped.containsKey(id)

    fun prepare(id: String, source: MediaSource, rank: Int) {
        if (released || has(id)) return
        runCatching {
            manager.add(source, rank)
            owned[id] = source
            wrapped[id] = manager.getMediaSource(source.mediaItem)
                ?: throw IllegalStateException("No preload source")
            manager.invalidate()
        }.onFailure {
            removeInternal(id)
        }
    }

    /** Returns the prepared wrapper for [id], or null when it was never offered. */
    fun take(id: String): MediaSource? {
        if (released) return null
        releaseAdopted()
        val source = wrapped.remove(id) ?: run {
            owned.remove(id)
            return null
        }
        owned.remove(id)
        adoptedId = id
        adoptedWrapped = source
        return source
    }

    /** Called once the foreground player owns the period and its tracks are selected. */
    fun releaseAdoptedIfCurrent(mediaId: String?) {
        if (mediaId != null && mediaId == adoptedId) releaseAdopted()
    }

    fun releaseAdopted() {
        if (adoptedId == null) return
        adoptedId = null
        val source = adoptedWrapped
        adoptedWrapped = null
        if (source != null) runCatching { manager.remove(source) }
    }

    /** Drops preloads for pages that are no longer close to the current one. */
    fun keepOnly(ids: Set<String>) {
        if (released) return
        (owned.keys + wrapped.keys).toSet()
            .filter { it !in ids && it != adoptedId }
            .forEach(::removeInternal)
    }

    fun setCurrentPlayingIndex(index: Int) {
        if (released) return
        currentIndex = index
        manager.setCurrentPlayingIndex(index)
    }

    fun release() {
        if (released) return
        released = true
        adoptedId = null
        adoptedWrapped = null
        owned.clear()
        wrapped.clear()
        runCatching { manager.release() }
    }
}

@OptIn(UnstableApi::class)
private fun createStreamSource(context: Context, stream: Stream, mediaId: String): MediaSource {
    val url = stream.content
    val dsFactory = MediaCache.dataSourceFactory(context)
    val mediaItem = MediaItem.Builder()
        .setUri(url)
        .setMediaId(mediaId)
        .build()
    return ProgressiveMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
}

/** Only the video of a Short (the same stream the player uses, so it comes from the cache). */
@OptIn(UnstableApi::class)
private fun shortsPreviewSource(context: Context, info: StreamInfo, mediaId: String): MediaSource? {
    val picked = StreamSelection.pickShorts(info)
    val stream = picked.video?.takeIf { !it.content.isNullOrBlank() }
        ?: picked.muxed?.takeIf { !it.content.isNullOrBlank() }
        ?: info.videoOnlyStreams.firstOrNull { it.isUrl }
        ?: info.videoStreams.firstOrNull { it.isUrl }
        ?: return null
    return createStreamSource(context, stream, mediaId)
}

@OptIn(UnstableApi::class)
private fun shortsSource(context: Context, info: StreamInfo, mediaId: String): MediaSource? {
    val picked = StreamSelection.pickShorts(info)

    val muxed = picked.muxed
    if (muxed != null && !muxed.content.isNullOrBlank()) {
        return createStreamSource(context, muxed, mediaId)
    }

    val video = picked.video
    val audio = picked.audio
    if (video != null && audio != null && !video.content.isNullOrBlank() && !audio.content.isNullOrBlank()) {
        val videoSource = createStreamSource(context, video, mediaId)
        val audioSource = createStreamSource(context, audio, mediaId)
        return MergingMediaSource(videoSource, audioSource)
    }

    val fallback = info.videoStreams.firstOrNull { it.isUrl }
        ?: info.videoOnlyStreams.firstOrNull { it.isUrl }
    return fallback?.let { createStreamSource(context, it, mediaId) }
}

