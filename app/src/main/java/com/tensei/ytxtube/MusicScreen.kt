package com.tensei.ytxtube

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Still used by the player to decide what counts as music among related videos. */
fun isMusicTrack(video: VideoItem): Boolean {
    val t = video.title.lowercase()
    val ch = video.channel.lowercase()
    return t.contains("music") || t.contains("song") || t.contains("audio") ||
        t.contains("official") || t.contains("lyrics") || t.contains("feat") ||
        t.contains("ft.") || t.contains("remix") || t.contains("album") ||
        t.contains("track") || ch.contains("vevo") || ch.contains("records") ||
        ch.contains("music") || ch.contains("topic") || video.durationSec in 60..720
}

enum class MusicTab(val label: String) { Home("Home"), Explore("Explore"), Library("Library") }

enum class MusicLibraryTab(val label: String) {
    Playlists("Playlists"), Songs("Songs"), Albums("Albums"), Artists("Artists"), History("History"), Downloads("Downloads"),
}

sealed interface MusicRoute { val key: String }
data class MusicCollectionRoute(val browseId: String) : MusicRoute { override val key: String get() = "c:$browseId" }
data class MusicArtistRoute(val browseId: String) : MusicRoute { override val key: String get() = "a:$browseId" }
data class MusicBrowseRoute(val browseId: String, val params: String?, val title: String) : MusicRoute {
    override val key: String get() = "b:$browseId:${params.orEmpty()}"
}

/** Shelves loaded page by page (home, explore, library, a mood, search results). */
@Stable
class MusicSectionsState(
    private val scope: CoroutineScope,
    private val first: suspend () -> MusicPage,
    private val more: suspend (String) -> MusicMore,
) {
    var title by mutableStateOf("")
        private set
    var chips by mutableStateOf<List<MusicChip>>(emptyList())
        private set
    var sections by mutableStateOf<List<MusicSection>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    private var token: String? = null
    private var generation = 0
    private var job: Job? = null

    val hasMore: Boolean get() = token != null

    fun ensureLoaded() {
        if (!loaded && !loading) refresh()
    }

    fun refresh() {
        val gen = ++generation
        job?.cancel()
        loading = true
        error = null
        job = scope.launch {
            try {
                val page = first()
                if (gen != generation) return@launch
                title = page.title
                chips = page.chips
                sections = page.sections
                token = page.continuation
                loaded = true
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (gen == generation) error = YouTube.friendlyError(t)
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    fun loadMore() {
        val t = token ?: return
        if (loadingMore || loading) return
        val gen = generation
        loadingMore = true
        scope.launch {
            try {
                val m = more(t)
                if (gen != generation) return@launch
                token = m.continuation
                if (m.sections.isNotEmpty()) sections = sections + m.sections
                if (m.items.isNotEmpty()) {
                    val last = sections.lastOrNull()
                    sections = if (last != null) {
                        sections.dropLast(1) + last.copy(items = (last.items + m.items).distinctBy { it.key })
                    } else {
                        listOf(MusicSection("", m.items, MusicLayout.List))
                    }
                }
                if (m.sections.isEmpty() && m.items.isEmpty()) token = null
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                if (gen == generation) token = null
            } finally {
                loadingMore = false
            }
        }
    }
}

/** A playlist or album, every page of it. */
@Stable
class MusicCollectionState(private val scope: CoroutineScope, val browseId: String) {
    var data by mutableStateOf<MusicCollection?>(null)
        private set
    var tracks by mutableStateOf<List<MusicItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf(false)
    private var token: String? = null

    val complete: Boolean get() = data != null && token == null

    fun ensureLoaded() {
        if (data == null && !loading) load()
    }

    fun load() {
        loading = true
        error = null
        scope.launch {
            runCatching { YTMusic.collection(browseId) }
                .onSuccess { d ->
                    data = d
                    tracks = d.tracks
                    token = d.continuation
                }
                .onFailure { if (it !is CancellationException) error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun loadMore() {
        val t = token ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            runCatching { YTMusic.collectionMore(data?.browseId ?: browseId, t) }
                .onSuccess { m ->
                    token = m.continuation
                    val d = data
                    val add = m.items.filter { it.playable }.map { item ->
                        if (d != null && d.isAlbum) item.copy(thumbnail = item.thumbnail ?: d.thumbnail, album = item.album ?: MusicRef(d.title, d.browseId))
                        else item
                    }
                    tracks = (tracks + add).distinctBy { it.videoId }
                    if (add.isEmpty() && m.items.isEmpty()) token = null
                }
                .onFailure { token = null }
            loadingMore = false
        }
    }

    /** The pages not loaded yet, for the player to fetch as the queue runs low. */
    fun feeder(): MusicFeeder? = token?.let { MusicFeeds.collection(data?.browseId ?: browseId, it) }
}

@Stable
class MusicArtistState(private val scope: CoroutineScope, val browseId: String) {
    var data by mutableStateOf<MusicArtist?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var subscribed by mutableStateOf(false)

    fun ensureLoaded() {
        if (data == null && !loading) load()
    }

    fun load() {
        loading = true
        error = null
        scope.launch {
            runCatching { YTMusic.artist(browseId) }
                .onSuccess { data = it; subscribed = it.subscribed }
                .onFailure { if (it !is CancellationException) error = YouTube.friendlyError(it) }
            loading = false
        }
    }
}

/** Past music searches, kept apart from video searches. */
object MusicSearchHistory {
    private var prefs: android.content.SharedPreferences? = null
    var queries by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("music_search_history", Context.MODE_PRIVATE)
        prefs = p
        queries = p.getString("q", null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
    }

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty() || Incognito.enabled) return
        queries = (listOf(q) + queries.filterNot { it.equals(q, ignoreCase = true) }).take(25)
        prefs?.edit()?.putString("q", queries.joinToString("\n"))?.apply()
    }

    fun remove(query: String) {
        queries = queries.filterNot { it == query }
        prefs?.edit()?.putString("q", queries.joinToString("\n"))?.apply()
    }
}

/** Everything the Music tab keeps while you move around the app. */
@Stable
class MusicData(private val scope: CoroutineScope) {
    var tab by mutableStateOf(MusicTab.Home)
    var libraryTab by mutableStateOf(MusicLibraryTab.Playlists)

    var homeChip by mutableStateOf<MusicChip?>(null)
        private set
    var homeChips by mutableStateOf<List<MusicChip>>(emptyList())
        private set
    var home by mutableStateOf(homeState(null))
        private set

    private fun homeState(chip: MusicChip?) = MusicSectionsState(
        scope,
        first = {
            YTMusic.home(chip?.params).also { if (it.chips.isNotEmpty() && (chip == null || homeChips.isEmpty())) homeChips = it.chips }
        },
        more = { t -> YTMusic.browseMore("FEmusic_home", chip?.params, t) },
    )

    fun selectChip(chip: MusicChip?) {
        val next = if (chip != null && chip.params == homeChip?.params) null else chip
        homeChip = next
        home = homeState(next)
        home.ensureLoaded()
    }

    val explore = MusicSectionsState(scope, { YTMusic.browse("FEmusic_explore") }, { t -> YTMusic.browseMore("FEmusic_explore", null, t) })

    private val libraryPages = HashMap<MusicLibraryTab, MusicSectionsState>()

    fun library(tab: MusicLibraryTab): MusicSectionsState = libraryPages.getOrPut(tab) {
        val id = when (tab) {
            MusicLibraryTab.Albums -> "FEmusic_liked_albums"
            MusicLibraryTab.Artists -> "FEmusic_library_corpus_artists"
            MusicLibraryTab.History -> "FEmusic_history"
            else -> "FEmusic_liked_playlists"
        }
        val first: suspend () -> MusicPage =
            if (tab == MusicLibraryTab.Artists) suspend { YTMusic.libraryArtists() } else suspend { YTMusic.browse(id) }
        MusicSectionsState(scope, first) { t -> YTMusic.browseMore(id, null, t) }
    }

    /** Forget the account's pages (signed in or out, or switched account). */
    fun resetAccount() {
        libraryPages.clear()
        collections.remove("VLLM")
        home = homeState(homeChip)
    }

    var stack by mutableStateOf<List<MusicRoute>>(emptyList())
        private set

    fun push(route: MusicRoute) {
        if (stack.lastOrNull()?.key == route.key) return
        stack = stack + route
    }

    fun pop() {
        stack = stack.dropLast(1)
    }

    fun clearStack() {
        stack = emptyList()
    }

    private val collections = HashMap<String, MusicCollectionState>()
    private val artists = HashMap<String, MusicArtistState>()
    private val browses = HashMap<String, MusicSectionsState>()

    fun collection(browseId: String): MusicCollectionState = collections.getOrPut(browseId) { MusicCollectionState(scope, browseId) }
    fun artist(browseId: String): MusicArtistState = artists.getOrPut(browseId) { MusicArtistState(scope, browseId) }
    fun browse(route: MusicBrowseRoute): MusicSectionsState = browses.getOrPut(route.key) {
        MusicSectionsState(scope, { YTMusic.browse(route.browseId, route.params) }) { t -> YTMusic.browseMore(route.browseId, route.params, t) }
    }

    // Search
    var searchOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var submitted by mutableStateOf<String?>(null)
        private set
    var searchFilter by mutableStateOf<String?>(null)
        private set
    var searchChips by mutableStateOf<List<MusicChip>>(emptyList())
        private set
    var results by mutableStateOf<MusicSectionsState?>(null)
        private set
    var suggestions by mutableStateOf(MusicSuggestions(emptyList(), emptyList()))
        private set
    private var suggestJob: Job? = null

    fun onQuery(q: String) {
        query = q
        suggestJob?.cancel()
        if (q.isBlank()) {
            suggestions = MusicSuggestions(emptyList(), emptyList())
            return
        }
        suggestJob = scope.launch {
            delay(160)
            runCatching { YTMusic.suggestions(q) }.onSuccess { suggestions = it }
        }
    }

    fun submit(q: String, filter: String? = null) {
        val text = q.trim()
        if (text.isEmpty()) return
        if (text != submitted) searchChips = emptyList()
        query = text
        submitted = text
        searchFilter = filter
        MusicSearchHistory.add(text)
        results = MusicSectionsState(
            scope,
            first = {
                YTMusic.search(text, filter).also { page ->
                    val chips = page.chips.filter { it.params != null }
                    if (chips.isNotEmpty() && (filter == null || searchChips.isEmpty())) searchChips = chips
                }
            },
            more = { t -> YTMusic.searchMore(text, filter, t) },
        ).also { it.ensureLoaded() }
    }

    fun closeSearch() {
        searchOpen = false
        query = ""
        submitted = null
        results = null
        searchFilter = null
        suggestions = MusicSuggestions(emptyList(), emptyList())
    }
}

/** Plays and opens things the way YouTube Music does. */
private class MusicActions(
    val data: MusicData,
    val player: PlayerState,
    val scope: CoroutineScope,
    val context: Context,
) {
    fun open(item: MusicItem, list: List<MusicItem>? = null, more: MusicFeeder? = null) {
        when {
            item.playable && list != null -> playList(list, list.indexOfFirst { it.key == item.key }.coerceAtLeast(0), more)
            item.playable -> playSong(item)
            item.kind == MusicKind.Album || item.kind == MusicKind.Playlist && item.browseId != null ->
                data.push(MusicCollectionRoute(item.browseId ?: return))
            item.kind == MusicKind.Artist -> data.push(MusicArtistRoute(item.browseId ?: return))
            item.kind == MusicKind.Playlist && item.playlistId != null -> playMix(item.playlistId, item.watchParams, item.title)
            item.browseId != null -> data.push(MusicBrowseRoute(item.browseId, item.browseParams, item.title))
        }
    }

    /** A song on its own starts its radio, like tapping it in YouTube Music. */
    fun playSong(item: MusicItem) {
        val video = item.toVideoItem() ?: return
        val radioList = item.playlistId?.takeIf { it.startsWith("RD") }
        player.startMusic(listOf(video), 0, MusicFeeds.radio(video.id, radioList, if (radioList != null) item.watchParams else null))
    }

    fun playList(items: List<MusicItem>, index: Int, more: MusicFeeder?, shuffle: Boolean = false) {
        val videos = items.mapNotNull { it.toVideoItem() }
        if (videos.isEmpty()) return
        val target = items.getOrNull(index)?.videoId
        val start = videos.indexOfFirst { it.id == target }.coerceAtLeast(0)
        player.startMusic(videos, start, more, shuffle = shuffle)
    }

    fun playMix(playlistId: String, params: String?, title: String) {
        scope.launch {
            val feeder = MusicFeeds.radio(null, playlistId, params)
            val first = runCatching { feeder() }.getOrDefault(emptyList())
            if (first.isEmpty()) {
                toast("Couldn't start $title")
            } else {
                player.startMusic(first, 0, feeder)
            }
        }
    }

    fun playCollection(state: MusicCollectionState, index: Int = 0, shuffle: Boolean = false) {
        val tracks = state.tracks
        if (tracks.isEmpty()) return
        playList(tracks, index, state.feeder(), shuffle)
    }

    /** Play or shuffle an album or playlist from a card, loading it first. */
    fun playCollectionItem(item: MusicItem, shuffle: Boolean) {
        val id = item.browseId
        if (id == null) {
            item.playlistId?.let { playMix(it, item.watchParams, item.title) }
            return
        }
        val state = data.collection(id)
        scope.launch {
            if (state.data == null) {
                state.ensureLoaded()
                var waited = 0
                while (state.loading && waited < 100) { delay(100); waited++ }
            }
            if (state.tracks.isEmpty()) toast("Nothing to play here") else playCollection(state, 0, shuffle)
        }
    }

    fun radioFor(item: MusicItem) {
        when {
            item.videoId != null -> item.toVideoItem()?.let { player.startRadio(it) }
            item.playlistId != null -> playMix("RDAMPL" + item.playlistId.removePrefix("VL"), null, item.title)
        }
    }

    fun like(item: MusicItem) {
        val id = item.videoId ?: return
        if (!Account.signedIn) { toast("Sign in to like songs"); return }
        scope.launch {
            runCatching { InnerTube.setLike(id, LikeStatus.Like) }
                .onSuccess { toast("Added to Liked music") }
                .onFailure { toast("Couldn't like this song") }
        }
    }

    fun save(playlistId: String?, save: Boolean, onDone: (Boolean) -> Unit = {}) {
        val id = playlistId?.removePrefix("VL") ?: return
        if (!Account.signedIn) { toast("Sign in to save to your library"); return }
        scope.launch {
            runCatching { YTMusic.savePlaylist(id, save) }
                .onSuccess { toast(if (save) "Saved to library" else "Removed from library"); onDone(save) }
                .onFailure { toast("Couldn't change your library") }
        }
    }

    fun download(item: MusicItem) {
        val v = item.toVideoItem() ?: return
        toast(if (Downloads.enqueueMusic(v)) "Downloading audio: ${v.title}" else "Already queued or downloaded")
    }

    fun share(item: MusicItem) {
        val url = when {
            item.videoId != null -> "https://music.youtube.com/watch?v=${item.videoId}"
            item.kind == MusicKind.Artist && item.browseId != null -> "https://music.youtube.com/channel/${item.browseId}"
            item.playlistId != null -> "https://music.youtube.com/playlist?list=${item.playlistId}"
            item.browseId != null -> "https://music.youtube.com/browse/${item.browseId}"
            else -> return
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        context.startActivity(Intent.createChooser(send, item.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

@Composable
fun MusicScreen(
    lib: LibraryData,
    player: PlayerState,
    contentPadding: PaddingValues,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val data = lib.music
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actions = remember(data, player) { MusicActions(data, player, scope, context.applicationContext) }
    LaunchedEffect(Unit) {
        MusicSearchHistory.init(context)
        RecentMusic.load()
    }

    // Signed in, out, or another account: the library and home belong to whoever it is now.
    val account = Account.profile?.name
    var lastAccount by remember { mutableStateOf(account) }
    LaunchedEffect(account) {
        if (account != lastAccount) {
            lastAccount = account
            data.resetAccount()
        }
    }

    var menuFor by remember { mutableStateOf<MusicItem?>(null) }

    val homeList = rememberLazyListState()
    val exploreList = rememberLazyListState()
    val libraryList = rememberLazyListState()
    var lastScroll by remember { mutableIntStateOf(scrollToTop) }
    LaunchedEffect(scrollToTop) {
        if (scrollToTop == lastScroll) return@LaunchedEffect
        lastScroll = scrollToTop
        if (data.stack.isNotEmpty() || data.searchOpen) {
            data.clearStack()
            data.closeSearch()
        } else {
            when (data.tab) {
                MusicTab.Home -> homeList.animateScrollToItem(0)
                MusicTab.Explore -> exploreList.animateScrollToItem(0)
                MusicTab.Library -> libraryList.animateScrollToItem(0)
            }
        }
    }

    BackHandler(enabled = data.stack.isNotEmpty()) { data.pop() }
    BackHandler(enabled = data.stack.isEmpty() && data.searchOpen) {
        if (data.submitted != null && data.query != data.submitted) data.query = data.submitted.orEmpty()
        else data.closeSearch()
    }
    BackHandler(enabled = data.stack.isEmpty() && !data.searchOpen && data.tab != MusicTab.Home) { data.tab = MusicTab.Home }

    val c = MaterialTheme.colorScheme
    val onMenu: (MusicItem) -> Unit = { menuFor = it }

    CompositionLocalProvider(LocalMusicPlayer provides player) {
    Box(Modifier.fillMaxSize().background(c.background)) {
        // The same collapsing glass header as the other tabs.
        CollapsingHeaderLayout(
            resetKey = data.tab,
            header = {
                MusicTopBar(
                    tab = data.tab,
                    onTab = { data.tab = it },
                    onSearch = { data.searchOpen = true },
                    onAccount = onAccount,
                )
            },
        ) { top ->
            val padded = contentPadding.withTop(top)
            when (data.tab) {
                MusicTab.Home -> MusicHome(data, actions, homeList, padded, onMenu)
                MusicTab.Explore -> MusicSectionsPage(data.explore, actions, exploreList, padded, onMenu)
                MusicTab.Library -> MusicLibrary(data, actions, libraryList, padded, onMenu, onAccount)
            }
        }
        if (data.searchOpen) {
            MusicSearch(data, actions, contentPadding, onMenu)
        }
        data.stack.lastOrNull()?.let { route ->
            key(route.key) {
                Box(Modifier.fillMaxSize().background(c.background).blockTouchesBelow()) {
                    when (route) {
                        is MusicCollectionRoute -> MusicCollectionPage(data.collection(route.browseId), actions, contentPadding, onMenu)
                        is MusicArtistRoute -> MusicArtistPage(data.artist(route.browseId), actions, contentPadding, onMenu)
                        is MusicBrowseRoute -> MusicBrowsePage(route, data.browse(route), actions, contentPadding, onMenu)
                    }
                }
            }
        }
    }

    menuFor?.let { item ->
        MusicItemSheet(item, actions, onDismiss = { menuFor = null })
    }
    }
}

/** A page laid over another must not let taps fall through to the one underneath. */
private fun Modifier.blockTouchesBelow(): Modifier =
    pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }

// ---------------------------------------------------------------------------------------- top

@Composable
private fun MusicTopBar(tab: MusicTab, onTab: (MusicTab) -> Unit, onSearch: () -> Unit, onAccount: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark()
            Spacer(Modifier.width(6.dp))
            Text(
                "Music",
                modifier = Modifier.weight(1f),
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
            HeaderActions(onSearch = onSearch, onAccount = onAccount)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MusicTab.entries.forEach { t ->
                MusicPill(t.label, selected = t == tab, backdrop = LocalGlassBackdrop.current) { onTab(t) }
            }
        }
    }
}

@Composable
private fun MusicPill(
    text: String,
    selected: Boolean,
    icon: ImageVector? = null,
    /** Over a backdrop the unselected pill is liquid glass. */
    backdrop: Backdrop? = null,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val bg = when {
        selected -> if (dark) Color.White else Color.Black
        ThemeSettings.mode == "amoled" -> Color(0xFF161616)
        dark -> Color(0xFF272727)
        else -> Color(0xFFEDEDED)
    }
    val fg = if (selected) (if (dark) Color.Black else Color.White) else c.onSurface
    Row(
        Modifier
            .then(
                if (!selected && backdrop != null) Modifier.glassControl(backdrop, RoundedCornerShape(8.dp))
                else Modifier.clip(RoundedCornerShape(8.dp)).background(bg),
            )
            .pressable(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// --------------------------------------------------------------------------------------- pages

@Composable
private fun LoadMoreWhenNearEnd(list: LazyListState, key: Any?, onMore: () -> Unit) {
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, key) { if (nearEnd) onMore() }
}

@Composable
private fun CenterSpinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun MusicHome(
    data: MusicData,
    actions: MusicActions,
    list: LazyListState,
    contentPadding: PaddingValues,
    onMenu: (MusicItem) -> Unit,
) {
    val state = data.home
    LaunchedEffect(state) { state.ensureLoaded() }
    LoadMoreWhenNearEnd(list, state.sections.size) { state.loadMore() }
    PullToRefresh(enabled = true, refreshing = state.loading && state.loaded, onRefresh = { state.refresh() }, topPadding = contentPadding.calculateTopPadding()) {
        LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            if (data.homeChips.isNotEmpty()) {
                item(key = "chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        items(data.homeChips, key = { "chip:" + it.title }) { chip ->
                            MusicPill(chip.title, selected = chip.params != null && chip.params == data.homeChip?.params) {
                                data.selectChip(chip)
                            }
                        }
                    }
                }
            }
            // Signed out, YouTube Music has no "Listen again"; the songs played here stand in.
            if (!Account.signedIn && data.homeChip == null && RecentMusic.items.isNotEmpty()) {
                item(key = "recent") {
                    val recent = RecentMusic.items.take(20).map {
                        MusicItem(MusicKind.Song, it.title, it.channel, it.thumbnail, videoId = it.id, artists = listOf(MusicRef(it.channel, null)))
                    }
                    MusicSectionView(MusicSection("Listen again", recent, MusicLayout.Carousel), actions, onMenu)
                }
            }
            when {
                state.loading && state.sections.isEmpty() -> item(key = "spin") { CenterSpinner() }
                state.error != null && state.sections.isEmpty() -> item(key = "err") {
                    EmptyState(
                        icon = Icons.Rounded.MusicNote,
                        title = "Couldn't load YouTube Music",
                        body = state.error.orEmpty(),
                        action = { PillButton("Retry", onClick = { state.refresh() }) },
                    )
                }
            }
            sectionItems(state.sections, actions, onMenu)
            if (state.loadingMore) item(key = "more") { CenterSpinner(Modifier.padding(vertical = 0.dp)) }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MusicSectionsPage(
    state: MusicSectionsState,
    actions: MusicActions,
    list: LazyListState,
    contentPadding: PaddingValues,
    onMenu: (MusicItem) -> Unit,
    header: (LazyListScope.() -> Unit)? = null,
) {
    LaunchedEffect(state) { state.ensureLoaded() }
    LoadMoreWhenNearEnd(list, state.sections.size) { state.loadMore() }
    PullToRefresh(enabled = true, refreshing = state.loading && state.loaded, onRefresh = { state.refresh() }, topPadding = contentPadding.calculateTopPadding()) {
        LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            header?.invoke(this)
            when {
                state.loading && state.sections.isEmpty() -> item(key = "spin") { CenterSpinner() }
                state.error != null && state.sections.isEmpty() -> item(key = "err") {
                    EmptyState(
                        icon = Icons.Rounded.MusicNote,
                        title = "Couldn't load this",
                        body = state.error.orEmpty(),
                        action = { PillButton("Retry", onClick = { state.refresh() }) },
                    )
                }
                state.loaded && state.sections.isEmpty() -> item(key = "empty") {
                    EmptyState(icon = Icons.Rounded.MusicNote, title = "Nothing here yet", body = "There is nothing to show on this page right now.")
                }
            }
            sectionItems(state.sections, actions, onMenu)
            if (state.loadingMore) item(key = "more") { CenterSpinner(Modifier.padding(vertical = 0.dp)) }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MusicBrowsePage(
    route: MusicBrowseRoute,
    state: MusicSectionsState,
    actions: MusicActions,
    contentPadding: PaddingValues,
    onMenu: (MusicItem) -> Unit,
) {
    val list = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = { actions.data.pop() }) {
            Text(
                state.title.ifBlank { route.title },
                modifier = Modifier.weight(1f),
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MusicSectionsPage(state, actions, list, contentPadding, onMenu)
    }
}

/** The shelves of a page, each drawn the way YouTube Music draws that kind of shelf. */
private fun LazyListScope.sectionItems(
    sections: List<MusicSection>,
    actions: MusicActions,
    onMenu: (MusicItem) -> Unit,
    onShowAll: ((MusicSection) -> Unit)? = null,
) {
    sections.forEachIndexed { index, section ->
        when (section.layout) {
            // Long lists (search results, history, the library) are rows of the page itself.
            MusicLayout.List -> {
                if (section.title.isNotBlank()) {
                    item(key = "h$index:${section.title}") {
                        MusicSectionHeader(section, onMore = sectionMore(section, actions, onShowAll))
                    }
                }
                // A song tapped in search, history or a chart starts its radio, as in YouTube Music.
                itemsIndexed(section.items, key = { i, it -> "r$index:$i:${it.key}" }) { _, item ->
                    MusicRow(item, onClick = { actions.open(item) }, onMenu = { onMenu(item) })
                }
                item(key = "g$index") { Spacer(Modifier.height(18.dp)) }
            }
            else -> item(key = "s$index:${section.title}") {
                MusicSectionView(section, actions, onMenu, onShowAll)
            }
        }
    }
}

private fun sectionMore(section: MusicSection, actions: MusicActions, onShowAll: ((MusicSection) -> Unit)?): (() -> Unit)? = when {
    section.searchParams != null && onShowAll != null -> { { onShowAll(section) } }
    section.more != null -> { { actions.open(section.more) } }
    else -> null
}

@Composable
private fun MusicSectionHeader(section: MusicSection, onMore: (() -> Unit)?) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (section.thumbnail != null) {
            AsyncImage(
                model = musicArt(section.thumbnail, 120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(38.dp).clip(CircleShape).background(c.surfaceContainerHighest),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            section.strapline?.let {
                Text(it.uppercase(), fontSize = 11.5.sp, color = c.onSurfaceVariant, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                section.title,
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp,
                letterSpacing = (-0.4).sp,
                color = c.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMore != null) {
            Text(
                "More",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(c.surfaceContainerHigh)
                    .pressable(onClick = onMore)
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MusicSectionView(
    section: MusicSection,
    actions: MusicActions,
    onMenu: (MusicItem) -> Unit,
    onShowAll: ((MusicSection) -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (section.title.isNotBlank() && section.layout != MusicLayout.TopResult) {
            MusicSectionHeader(section, onMore = sectionMore(section, actions, onShowAll))
        }
        when (section.layout) {
            MusicLayout.Carousel -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(section.items, key = { it.key }) { item ->
                        MusicCard(item, onClick = { actions.open(item) }, onMenu = { onMenu(item) })
                    }
                }
            }
            MusicLayout.QuickPicks -> {
                // Quick picks: columns of four songs, swiped through one column at a time.
                val rowState = rememberLazyListState()
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val colWidth = (maxWidth - 40.dp).coerceAtMost(420.dp)
                    LazyRow(
                        state = rowState,
                        flingBehavior = rememberSnapFlingBehavior(rowState),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        items(section.items.chunked(4), key = { col -> "q:" + col.first().key }) { column ->
                            Column(Modifier.width(colWidth)) {
                                column.forEach { item ->
                                    MusicRow(item, onClick = { actions.open(item) }, onMenu = { onMenu(item) })
                                }
                            }
                        }
                    }
                }
            }
            MusicLayout.Grid -> {
                val navs = section.items.all { it.kind == MusicKind.Nav }
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    val columns = if (navs) (maxWidth / 170.dp).toInt().coerceIn(2, 4) else (maxWidth / 165.dp).toInt().coerceIn(2, 5)
                    Column(verticalArrangement = Arrangement.spacedBy(if (navs) 10.dp else 16.dp)) {
                        section.items.chunked(columns).forEach { rowItems ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowItems.forEach { item ->
                                    Box(Modifier.weight(1f)) {
                                        if (navs) MoodButton(item) { actions.open(item) }
                                        else MusicCard(item, onClick = { actions.open(item) }, onMenu = { onMenu(item) }, fill = true)
                                    }
                                }
                                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            MusicLayout.TopResult -> {
                val top = section.items.first()
                Text(
                    section.title,
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 10.dp),
                )
                TopResultCard(top, actions, onMenu)
                section.items.drop(1).forEach { item ->
                    MusicRow(item, onClick = { actions.open(item) }, onMenu = { onMenu(item) })
                }
            }
            MusicLayout.Description -> {
                var open by remember { mutableStateOf(false) }
                Text(
                    section.description.orEmpty(),
                    fontSize = 14.sp,
                    color = c.onSurfaceVariant,
                    maxLines = if (open) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp).clickable { open = !open },
                )
            }
            MusicLayout.List -> section.items.forEach { item ->
                MusicRow(item, onClick = { actions.open(item) }, onMenu = { onMenu(item) })
            }
        }
    }
}

// ------------------------------------------------------------------------------------- pieces

@Composable
private fun MusicCard(item: MusicItem, onClick: () -> Unit, onMenu: () -> Unit, fill: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val width: Dp = if (item.wide) 248.dp else 150.dp
    val round = item.kind == MusicKind.Artist
    Column(
        Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier.width(width))
            .pressable(pressedScale = 0.96f, onLongClick = onMenu, onClick = onClick)
            .then(item.videoId?.let { Modifier.prefetchOnPress(it) } ?: Modifier),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (item.wide) 16f / 9f else 1f)
                .clip(if (round) CircleShape else RoundedCornerShape(8.dp))
                .background(c.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = musicArt(item.thumbnail, 400),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        if (item.subtitle.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.explicit) ExplicitMark()
                Text(
                    item.subtitle,
                    fontSize = 12.5.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (round) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ExplicitMark() {
    Icon(
        Icons.Rounded.Explicit,
        contentDescription = "Explicit",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 3.dp).size(15.dp),
    )
}

@Composable
private fun MusicRow(
    item: MusicItem,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    index: Int? = null,
    showArt: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    val player = LocalMusicPlayer.current
    val playing = item.videoId != null && player?.current?.id == item.videoId
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onLongClick = onMenu, onClick = onClick)
            // The song starts loading the moment the finger touches it.
            .then(item.videoId?.let { Modifier.prefetchOnPress(it) } ?: Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null && !showArt) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                if (playing) Icon(Icons.Rounded.GraphicEq, null, tint = c.primary, modifier = Modifier.size(18.dp))
                else Text("$index", fontSize = 14.sp, color = c.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
        } else if (showArt) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(if (item.kind == MusicKind.Artist) CircleShape else RoundedCornerShape(6.dp))
                    .background(c.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = musicArt(item.thumbnail, 160),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (playing) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (playing) c.primary else c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = buildString {
                append(item.subtitle)
                if (item.durationSec > 0 && item.kind != MusicKind.Artist) {
                    if (isNotEmpty()) append(" • ")
                    append(formatDuration(item.durationSec * 1000))
                }
            }
            if (line.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.explicit) ExplicitMark()
                    Text(line, fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MoreVert, "More", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MoodButton(item: MusicItem, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val stripe = item.color?.let { Color(it.toInt()) } ?: c.primary
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceContainerHigh)
            .pressable(pressedScale = 0.96f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(stripe))
        Text(
            item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun TopResultCard(item: MusicItem, actions: MusicActions, onMenu: (MusicItem) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceContainerHigh)
            .pressable(pressedScale = 0.98f, onLongClick = { onMenu(item) }) { actions.open(item) }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = musicArt(item.thumbnail, 300),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(if (item.kind == MusicKind.Artist) CircleShape else RoundedCornerShape(8.dp))
                    .background(c.surfaceContainerHighest),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                item.playable -> {
                    MusicPill("Play", selected = true, icon = Icons.Rounded.PlayArrow) { actions.playSong(item) }
                    MusicPill("Radio", selected = false, icon = Icons.Rounded.Radio) { actions.radioFor(item) }
                }
                item.kind == MusicKind.Album || item.kind == MusicKind.Playlist -> {
                    MusicPill("Play", selected = true, icon = Icons.Rounded.PlayArrow) { actions.playCollectionItem(item, shuffle = false) }
                    MusicPill("Shuffle", selected = false, icon = Icons.Rounded.Shuffle) { actions.playCollectionItem(item, shuffle = true) }
                }
                item.kind == MusicKind.Artist -> {
                    MusicPill("Open", selected = true, icon = Icons.Rounded.Person) { actions.open(item) }
                }
            }
        }
    }
}

@Composable
private fun MusicActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean = false,
    backdrop: Backdrop? = null,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 64.dp)) {
        Box(
            Modifier
                .size(if (primary) 60.dp else 46.dp)
                .then(
                    if (primary || backdrop == null) Modifier.clip(CircleShape).background(if (primary) c.onSurface else c.surfaceContainerHigh)
                    else Modifier.glassControl(backdrop, CircleShape, strength = 1.2f),
                )
                .pressable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (primary) c.surface else c.onSurface, modifier = Modifier.size(if (primary) 32.dp else 22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.5.sp, color = c.onSurfaceVariant, maxLines = 1)
    }
}

// ------------------------------------------------------------------------- playlist / album

@Composable
private fun MusicCollectionPage(
    state: MusicCollectionState,
    actions: MusicActions,
    contentPadding: PaddingValues,
    onMenu: (MusicItem) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    LaunchedEffect(state) { state.ensureLoaded() }
    LoadMoreWhenNearEnd(list, state.tracks.size) { state.loadMore() }
    val d = state.data
    val ambient = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
    MusicAmbient(d?.thumbnail, ambient)
    Column(Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = { actions.data.pop() }) {
            val showTitle by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
            Text(
                if (showTitle) d?.title.orEmpty() else "",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            d == null && state.loading -> CenterSpinner()
            d == null && state.error != null -> EmptyState(
                icon = Icons.Rounded.Album,
                title = "Couldn't load this",
                body = state.error.orEmpty(),
                action = { PillButton("Retry", onClick = { state.load() }) },
            )
            d != null -> LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                item(key = "head") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = musicArt(d.thumbnail, 800),
                            contentDescription = d.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(0.66f)
                                .widthIn(max = 300.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surfaceContainerHighest),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            d.title,
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 23.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        d.author?.let { author ->
                            val artistId = author.browseId?.takeIf { it.startsWith("UC") }
                            Text(
                                author.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = c.onSurface,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(
                                        if (artistId != null) {
                                            Modifier.clickable { actions.data.push(MusicArtistRoute(artistId)) }
                                        } else Modifier,
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                        val info = listOf(d.subtitle, d.secondSubtitle).filter { it.isNotBlank() }.joinToString("\n")
                        if (info.isNotBlank()) {
                            Text(info, fontSize = 13.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        }
                        d.description?.let { text ->
                            var open by remember { mutableStateOf(false) }
                            Text(
                                text,
                                fontSize = 13.sp,
                                color = c.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = if (open) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp).clickable { open = !open },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (Account.signedIn && d.browseId != "VLLM") {
                                MusicActionButton(
                                    if (state.saved) "Saved" else "Save",
                                    if (state.saved) Icons.Rounded.LibraryAddCheck else Icons.Rounded.LibraryAdd,
                                    backdrop = ambient,
                                ) { actions.save(d.playlistId, !state.saved) { state.saved = it } }
                            }
                            MusicActionButton("Shuffle", Icons.Rounded.Shuffle, backdrop = ambient) { actions.playCollection(state, 0, shuffle = true) }
                            MusicActionButton("Play", Icons.Rounded.PlayArrow, primary = true) { actions.playCollection(state, 0) }
                            MusicActionButton("Radio", Icons.Rounded.Radio, backdrop = ambient) {
                                d.playlistId?.let { actions.playMix("RDAMPL$it", null, d.title) }
                            }
                            MusicActionButton("Share", Icons.Rounded.Share, backdrop = ambient) {
                                actions.share(MusicItem(MusicKind.Playlist, d.title, playlistId = d.playlistId, browseId = d.browseId))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (state.tracks.isEmpty() && !state.loadingMore) {
                    item(key = "none") {
                        EmptyState(icon = Icons.Rounded.MusicNote, title = "No songs", body = "This has no songs that can be played here.")
                    }
                }
                itemsIndexed(state.tracks, key = { _, t -> "t:" + t.key }) { i, track ->
                    MusicRow(
                        track,
                        onClick = { actions.playCollection(state, i) },
                        onMenu = { onMenu(track) },
                        index = i + 1,
                        showArt = !d.isAlbum,
                    )
                }
                if (state.loadingMore) item(key = "more") { CenterSpinner(Modifier.padding(vertical = 0.dp)) }
                if (state.complete && d.related.isNotEmpty()) {
                    item(key = "relgap") { HorizontalDivider(Modifier.padding(16.dp), color = c.onSurface.copy(alpha = 0.08f)) }
                    d.related.forEachIndexed { i, s ->
                        item(key = "rel$i") { MusicSectionView(s, actions, onMenu) }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    }
}

/**
 * The cover, blurred, glowing behind the top of an album, playlist or artist page and fading into
 * the page. It is also what the page's glass buttons bend and blur.
 */
@Composable
private fun MusicAmbient(art: String?, backdrop: com.kyant.backdrop.backdrops.LayerBackdrop) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(c.background)) {
        if (art != null) {
            AsyncImage(
                model = musicArt(art, 300),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(520.dp).blur(60.dp).graphicsLayer { alpha = 0.9f },
            )
            Box(
                Modifier.fillMaxWidth().height(520.dp).background(
                    Brush.verticalGradient(
                        0f to c.background.copy(alpha = 0.25f),
                        0.55f to c.background.copy(alpha = 0.55f),
                        1f to c.background,
                    ),
                ),
            )
        }
    }
}

// ----------------------------------------------------------------------------------- artist

@Composable
private fun MusicArtistPage(
    state: MusicArtistState,
    actions: MusicActions,
    contentPadding: PaddingValues,
    onMenu: (MusicItem) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.ensureLoaded() }
    val d = state.data
    val ambient = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        MusicAmbient(d?.thumbnail, ambient)
        when {
            d == null && state.loading -> Column { Spacer(Modifier.statusBarsPadding().height(56.dp)); CenterSpinner() }
            d == null && state.error != null -> Column {
                Spacer(Modifier.statusBarsPadding().height(56.dp))
                EmptyState(
                    icon = Icons.Rounded.Person,
                    title = "Couldn't load this artist",
                    body = state.error.orEmpty(),
                    action = { PillButton("Retry", onClick = { state.load() }) },
                )
            }
            d != null -> LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                item(key = "head") {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.1f)) {
                        AsyncImage(
                            model = musicArt(d.thumbnail, 1080),
                            contentDescription = d.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().background(c.surfaceContainerHighest),
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.35f),
                                    0.3f to Color.Transparent,
                                    0.62f to c.background.copy(alpha = 0.35f),
                                    1f to c.background,
                                ),
                            ),
                        )
                        Text(
                            d.name,
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp,
                            letterSpacing = (-0.8).sp,
                            color = c.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    }
                }
                item(key = "actions") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        d.subscribers?.let { Text(it, fontSize = 13.sp, color = c.onSurfaceVariant) }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (Account.signedIn && d.channelId != null) {
                                MusicPill(if (state.subscribed) "Subscribed" else "Subscribe", selected = !state.subscribed, backdrop = ambient) {
                                    val next = !state.subscribed
                                    state.subscribed = next
                                    scope.launch {
                                        runCatching { InnerTube.setSubscribed(d.channelId, next) }
                                            .onFailure { state.subscribed = !next; actions.toast("Couldn't change the subscription") }
                                    }
                                }
                            }
                            d.shuffle?.let { s ->
                                MusicPill("Shuffle", selected = false, icon = Icons.Rounded.Shuffle, backdrop = ambient) { actions.playMix(s.playlistId ?: return@MusicPill, s.watchParams, d.name) }
                            }
                            d.radio?.let { r ->
                                MusicPill("Radio", selected = false, icon = Icons.Rounded.Radio, backdrop = ambient) { actions.playMix(r.playlistId ?: return@MusicPill, r.watchParams, d.name) }
                            }
                        }
                        d.description?.let { text ->
                            var open by remember { mutableStateOf(false) }
                            Text(
                                text,
                                fontSize = 13.5.sp,
                                color = c.onSurfaceVariant,
                                maxLines = if (open) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 12.dp).clickable { open = !open },
                            )
                        }
                    }
                }
                d.sections.forEachIndexed { i, section ->
                    item(key = "sec$i") {
                        if (section.layout == MusicLayout.List) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                MusicSectionHeader(section, onMore = section.more?.let { m -> { actions.open(m) } })
                                section.items.forEach { item ->
                                    MusicRow(item, onClick = { actions.open(item, section.items.filter { it.playable }) }, onMenu = { onMenu(item) })
                                }
                            }
                        } else {
                            MusicSectionView(section, actions, onMenu)
                        }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
        // Back button over the picture.
        Box(
            Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .size(42.dp)
                .glassControl(ambient, CircleShape)
                .pressable { actions.data.pop() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ---------------------------------------------------------------------------------- library

@Composable
private fun MusicLibrary(
    data: MusicData,
    actions: MusicActions,
    list: LazyListState,
    contentPadding: PaddingValues,
    onMenu: (MusicItem) -> Unit,
    onAccount: () -> Unit,
) {
    val tab = data.libraryTab
    val chips: LazyListScope.() -> Unit = {
        item(key = "libchips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(MusicLibraryTab.entries, key = { it.name }) { t ->
                    MusicPill(t.label, selected = t == tab) { data.libraryTab = t }
                }
            }
        }
    }
    if (tab == MusicLibraryTab.Downloads) {
        val downloads = Downloads.musicDownloads.map { d ->
            MusicItem(MusicKind.Song, d.title, d.channel, d.thumbnail, videoId = d.videoId, durationSec = d.durationSec, artists = listOf(MusicRef(d.channel, null)))
        }
        val videos = Downloads.musicDownloads.map { it.toVideoItem() }
        LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            chips()
            if (downloads.isEmpty()) {
                item(key = "none") {
                    EmptyState(icon = Icons.Rounded.Download, title = "No downloads", body = "Songs you download (audio only) show up here and play without a connection.")
                }
            } else {
                item(key = "dlplay") {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MusicPill("Play", selected = true, icon = Icons.Rounded.PlayArrow) { actions.player.startMusic(videos, 0) }
                        MusicPill("Shuffle", selected = false, icon = Icons.Rounded.Shuffle) { actions.player.startMusic(videos, 0, shuffle = true) }
                    }
                }
                itemsIndexed(downloads, key = { _, it -> "dl:" + it.key }) { i, item ->
                    MusicRow(item, onClick = { actions.player.startMusic(videos, i) }, onMenu = { onMenu(item) })
                }
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
        return
    }
    if (!Account.signedIn) {
        LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            chips()
            item(key = "signin") {
                EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = "Your YouTube Music library",
                    body = "Sign in to see your playlists, liked songs, albums, artists and history from YouTube Music.",
                    action = { PillButton("Sign in", onClick = onAccount) },
                )
            }
        }
        return
    }
    if (tab == MusicLibraryTab.Songs) {
        val liked = data.collection("VLLM")
        LaunchedEffect(liked) { liked.ensureLoaded() }
        LoadMoreWhenNearEnd(list, liked.tracks.size) { liked.loadMore() }
        LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            chips()
            item(key = "likedplay") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicPill("Play", selected = true, icon = Icons.Rounded.PlayArrow) { actions.playCollection(liked, 0) }
                    MusicPill("Shuffle", selected = false, icon = Icons.Rounded.Shuffle) { actions.playCollection(liked, 0, shuffle = true) }
                }
            }
            when {
                liked.loading && liked.tracks.isEmpty() -> item(key = "spin") { CenterSpinner() }
                liked.error != null && liked.tracks.isEmpty() -> item(key = "err") {
                    EmptyState(Icons.Rounded.MusicNote, "Couldn't load your liked songs", liked.error.orEmpty(), action = { PillButton("Retry", onClick = { liked.load() }) })
                }
            }
            itemsIndexed(liked.tracks, key = { _, t -> "lk:" + t.key }) { i, track ->
                MusicRow(track, onClick = { actions.playCollection(liked, i) }, onMenu = { onMenu(track) })
            }
            if (liked.loadingMore) item(key = "more") { CenterSpinner(Modifier.padding(vertical = 0.dp)) }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
        return
    }
    val state = data.library(tab)
    MusicSectionsPage(state, actions, list, contentPadding, onMenu, header = {
        chips()
        if (tab == MusicLibraryTab.Playlists) {
            item(key = "likedrow") {
                MusicRow(
                    MusicItem(MusicKind.Playlist, "Liked music", "Auto playlist", browseId = "VLLM", playlistId = "LM"),
                    onClick = { actions.data.push(MusicCollectionRoute("VLLM")) },
                    onMenu = {},
                    showArt = false,
                )
            }
        }
    })
}

// ----------------------------------------------------------------------------------- search

@Composable
private fun MusicSearch(
    data: MusicData,
    actions: MusicActions,
    contentPadding: PaddingValues,
    onMenu: (MusicItem) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val editing = data.submitted == null || data.query != data.submitted
    LaunchedEffect(Unit) { if (data.submitted == null) runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().background(c.background).blockTouchesBelow().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { data.closeSearch() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onSurface) }
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surfaceContainerHigh)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (data.query.isEmpty()) {
                    Text("Search songs, albums, artists", fontSize = 14.5.sp, color = c.onSurfaceVariant)
                }
                BasicTextField(
                    value = data.query,
                    onValueChange = { data.onQuery(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        data.submit(data.query)
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (data.query.isNotEmpty()) {
                IconButton(onClick = { data.onQuery(""); runCatching { focus.requestFocus() } }) {
                    Icon(Icons.Rounded.Close, "Clear", tint = c.onSurfaceVariant)
                }
            }
        }
        if (editing) {
            LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                if (data.query.isBlank()) {
                    items(MusicSearchHistory.queries, key = { "h:$it" }) { q ->
                        SuggestionRow(q, Icons.Rounded.History, onClick = { keyboard?.hide(); data.submit(q) }, onTrailing = { MusicSearchHistory.remove(q) }, trailing = Icons.Rounded.Close)
                    }
                } else {
                    items(data.suggestions.queries, key = { "s:$it" }) { q ->
                        SuggestionRow(q, Icons.Rounded.Search, onClick = { keyboard?.hide(); data.submit(q) }, onTrailing = { data.onQuery("$q ") }, trailing = Icons.Rounded.NorthWest)
                    }
                    if (data.suggestions.items.isNotEmpty()) {
                        item(key = "sdiv") { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = c.onSurface.copy(alpha = 0.08f)) }
                        items(data.suggestions.items, key = { "si:" + it.key }) { item ->
                            MusicRow(item, onClick = { keyboard?.hide(); actions.open(item) }, onMenu = { onMenu(item) })
                        }
                    }
                }
            }
        } else {
            val results = data.results
            val chips = data.searchChips.ifEmpty {
                listOf(
                    MusicChip("Songs", null, YTMusic.FILTER_SONGS),
                    MusicChip("Videos", null, YTMusic.FILTER_VIDEOS),
                    MusicChip("Albums", null, YTMusic.FILTER_ALBUMS),
                    MusicChip("Artists", null, YTMusic.FILTER_ARTISTS),
                    MusicChip("Playlists", null, YTMusic.FILTER_PLAYLISTS),
                )
            }
            val list = rememberLazyListState()
            if (results != null) {
                LaunchedEffect(results) { list.scrollToItem(0) }
                LoadMoreWhenNearEnd(list, results.sections.sumOf { it.items.size }) { results.loadMore() }
                LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                    item(key = "chips") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 6.dp),
                        ) {
                            item(key = "all") {
                                MusicPill("All", selected = data.searchFilter == null) { data.submit(data.submitted.orEmpty(), null) }
                            }
                            items(chips, key = { "c:" + it.title }) { chip ->
                                MusicPill(chip.title, selected = chip.params == data.searchFilter) {
                                    data.submit(data.submitted.orEmpty(), if (chip.params == data.searchFilter) null else chip.params)
                                }
                            }
                        }
                    }
                    when {
                        results.loading && results.sections.isEmpty() -> item(key = "spin") { CenterSpinner() }
                        results.error != null && results.sections.isEmpty() -> item(key = "err") {
                            EmptyState(Icons.Rounded.Search, "Search failed", results.error.orEmpty(), action = { PillButton("Retry", onClick = { results.refresh() }) })
                        }
                        results.loaded && results.sections.isEmpty() -> item(key = "none") {
                            EmptyState(Icons.Rounded.Search, "No results", "Try different words.")
                        }
                    }
                    sectionItems(results.sections, actions, onMenu, onShowAll = { s -> data.submit(data.submitted.orEmpty(), s.searchParams) })
                    if (results.loadingMore) item(key = "more") { CenterSpinner(Modifier.padding(vertical = 0.dp)) }
                    item(key = "end") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(text: String, icon: ImageVector, onClick: () -> Unit, onTrailing: () -> Unit, trailing: ImageVector) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, fontSize = 15.sp, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onTrailing) { Icon(trailing, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
    }
}

// ----------------------------------------------------------------------------------- sheets

/** The player's own copy of the playing song state, for "now playing" marks in lists. */
val LocalMusicPlayer = androidx.compose.runtime.staticCompositionLocalOf<PlayerState?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicItemSheet(item: MusicItem, actions: MusicActions, onDismiss: () -> Unit) {
    if (item.playable) {
        val video = item.toVideoItem() ?: return
        val menu = LocalVideoMenu.current
        MusicTrackOptionsSheet(
            track = video,
            onPlayNext = { actions.player.playNextMusic(video) },
            onDownloadAudio = { actions.download(item) },
            onShare = { actions.share(item) },
            onDismiss = onDismiss,
            onAddToQueue = { actions.player.addToMusicQueue(video) },
            onStartRadio = { actions.radioFor(item) },
            onLike = if (Account.signedIn) ({ actions.like(item) }) else null,
            onAlbum = item.album?.browseId?.let { id -> { actions.data.push(MusicCollectionRoute(id)) } },
            onArtist = item.artists.firstOrNull { it.browseId?.startsWith("UC") == true }?.browseId?.let { id -> { actions.data.push(MusicArtistRoute(id)) } },
            onMore = { menu(video) },
        )
        return
    }
    val c = MaterialTheme.colorScheme
    GlassSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            SheetHeader(item.title, item.subtitle, item.thumbnail, round = item.kind == MusicKind.Artist)
            if (item.kind == MusicKind.Album || item.kind == MusicKind.Playlist) {
                SheetRow(Icons.Rounded.PlayArrow, "Play") { onDismiss(); actions.playCollectionItem(item, shuffle = false) }
                SheetRow(Icons.Rounded.Shuffle, "Shuffle") { onDismiss(); actions.playCollectionItem(item, shuffle = true) }
                if (item.playlistId != null) SheetRow(Icons.Rounded.Radio, "Start radio") { onDismiss(); actions.radioFor(item) }
                if (Account.signedIn && item.playlistId != null) {
                    SheetRow(Icons.Rounded.LibraryAdd, "Save to library") { onDismiss(); actions.save(item.playlistId, true) }
                }
            }
            if (item.opensPage) SheetRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Open") { onDismiss(); actions.open(item) }
            SheetRow(Icons.Rounded.Share, "Share") { onDismiss(); actions.share(item) }
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String, thumbnail: String?, round: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = musicArt(thumbnail, 200),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp)).background(c.surfaceContainerHighest),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, subtitle: String? = null, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
            subtitle?.let { Text(it, fontSize = 12.sp, color = c.onSurfaceVariant) }
        }
    }
}

/** What you can do with a song: also used by the player's own menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTrackOptionsSheet(
    track: VideoItem,
    onPlayNext: () -> Unit,
    onDownloadAudio: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    onArtist: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    GlassSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(bottom = 28.dp)) {
            SheetHeader(track.title, track.channel, track.thumbnail)
            val close: (() -> Unit) -> () -> Unit = { action -> { onDismiss(); action() } }
            onStartRadio?.let { SheetRow(Icons.Rounded.Radio, "Start radio", onClick = close(it)) }
            SheetRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", onClick = close(onPlayNext))
            onAddToQueue?.let { SheetRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", onClick = close(it)) }
            onLike?.let { SheetRow(Icons.Rounded.ThumbUp, "Like", onClick = close(it)) }
            onAlbum?.let { SheetRow(Icons.Rounded.Album, "Go to album", onClick = close(it)) }
            onArtist?.let { SheetRow(Icons.Rounded.Person, "Go to artist", onClick = close(it)) }
            SheetRow(Icons.Rounded.Download, "Download (audio only)", "High quality audio", onClick = close(onDownloadAudio))
            SheetRow(Icons.Rounded.Share, "Share", onClick = close(onShare))
            onMore?.let { SheetRow(Icons.Rounded.MoreHoriz, "More options", "Save to a playlist and more", onClick = close(it)) }
        }
    }
}
