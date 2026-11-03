package com.tensei.ytxtube

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

private val DockHeight = 64.dp
private val DockGap = 12.dp
private val DockWidth = 344.dp

/** TikTok's own phone bottom bar is 49 dp tall; it stays in the page's layout, just invisible, under this app's dock. */
private val TikTokNavHeight = 49.dp

/** On a wide screen (the unfolded phone) the feed keeps a phone-sized column in the middle. */
private val FeedMaxWidth = 560.dp

private val tabIcons: List<Pair<ImageVector, ImageVector>> = listOf(
    Icons.Outlined.Home to Icons.Rounded.Home,
    Icons.Outlined.Explore to Icons.Rounded.Explore,
    Icons.Outlined.Inbox to Icons.Rounded.Inbox,
    Icons.Outlined.Person to Icons.Rounded.Person,
)

/**
 * The TikTok client: TikTok's own page in the middle (account, For You feed, comments, everything),
 * and the same floating liquid-glass bar as the YouTube client underneath it, on pure black.
 */
@Composable
internal fun TokScreen(state: TokState, onExit: () -> Unit) {
    val density = LocalDensity.current
    val view = LocalView.current
    val backdrop = rememberLayerBackdrop()
    val imeUp = WindowInsets.ime.getBottom(density) > 0
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The page ends where the dock begins, so the dock never covers the caption or the buttons.
    val reserve = if (imeUp) 0.dp else (navInset + DockGap + DockHeight - TikTokNavHeight).coerceAtLeast(0.dp)

    // This screen is always black, so the status and gesture bars always get light icons,
    // whatever theme the YouTube side is set to.
    SideEffect {
        val activity = view.context as? ComponentActivity
        val window = activity?.window ?: (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (activity != null) {
            val style = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            runCatching {
                activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    BackHandler {
        val web = state.webView
        if (web != null && web.canGoBack()) web.goBack() else onExit()
    }

    Box(Modifier.fillMaxSize()) {
        // The black page behind everything. The dock's glass reads from this layer, not from the web page.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.Black))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(bottom = reserve),
        ) {
            TokDiagStrip(state)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                AndroidView(
                    factory = { ctx ->
                        createTokWebView(ctx, state).also { web ->
                            state.webView = web
                            val first = state.pendingUrl ?: TokWeb.HOME
                            state.pendingUrl = null
                            web.loadUrl(first)
                        }
                    },
                    modifier = (if (state.desktop || !state.capWidth) Modifier else Modifier.widthIn(max = FeedMaxWidth)).fillMaxSize(),
                    onRelease = { web ->
                        if (state.webView === web) state.webView = null
                        web.stopLoading()
                        web.destroy()
                    },
                )
            }
        }

        if (state.plain) {
            // A vanilla WebView with default settings, to see whether the zero viewport units come from this app's setup.
            Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 150.dp, bottom = 90.dp)) {
                AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).also { w ->
                            w.settings.javaScriptEnabled = true
                            w.loadDataWithBaseURL("https://probe.local/", TokWeb.PROBE_HTML, "text/html", "utf-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // A thin line under the status bar while a page loads; the page itself is never covered.
        if (state.progress in 1..99) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .fillMaxWidth(state.progress / 100f)
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.55f)),
            )
        }

        if (state.error) {
            Column(
                Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Can't reach TikTok", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Check your connection and try again.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (state.errorDetail.isNotEmpty()) {
                    Text(
                        state.errorDetail,
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box(Modifier.size(20.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable { state.retry() }
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                ) {
                    Text("Try again", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (!imeUp && state.dockOn) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = DockGap)
                    .widthIn(max = DockWidth)
                    .fillMaxWidth(),
            ) {
                val selected = remember(state) { { state.tab } }
                LiquidBottomBar(
                    selectedTabIndex = selected,
                    onTabSelected = { state.settle(it) },
                    backdrop = backdrop,
                    tabsCount = TokWeb.tabs.size,
                    isDarkSurface = true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TokWeb.tabs.forEachIndexed { i, spec ->
                        TokTab(spec, tabIcons[i], selected = state.tab == i) { state.tap(i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TokTab(
    spec: TokTabSpec,
    icons: Pair<ImageVector, ImageVector>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    LiquidBottomTab(onClick = onClick) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
            Icon(
                if (selected) icons.second else icons.first,
                contentDescription = spec.label,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        NavLabel(spec.label, selected, Color.White)
    }
}

/**
 * A strip above the page while this client is being tuned: the switch to the desktop site (some sign-in
 * screens only exist there), a reload, a plain video test, and a few lines about the page, its video and
 * whatever the page failed to load.
 */
@Composable
private fun TokDiagStrip(state: TokState) {
    LaunchedEffect(state) {
        while (true) {
            delay(1200)
            state.nativeText = state.nativeInfo()
            state.webView?.evaluateJavascript(TokWeb.DIAG_SCRIPT) { r -> state.diag = (r ?: "").trim('"').replace("\\n", "\n") }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TokChip(if (state.desktop) "Mobile site" else "Desktop site") { state.switchSite(!state.desktop) }
            TokChip("Reload") {
                fetchRemoteScript(state)
                state.retry()
            }
            TokChip("Test video") {
                state.webView?.loadUrl("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4")
            }
        }
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TokChip("Layer " + listOf("none", "hw", "sw")[state.layerMode]) {
                state.layerMode = (state.layerMode + 1) % 3
                state.applyLayer()
            }
            TokChip("FR " + (if (state.frameHint) "on" else "off")) {
                state.frameHint = !state.frameHint
                state.applyFrameHint()
            }
            TokChip("Dock " + (if (state.dockOn) "on" else "off")) { state.dockOn = !state.dockOn }
            TokChip("Cap " + (if (state.capWidth) "on" else "off")) { state.capWidth = !state.capWidth }
            TokChip("Mode " + (if (state.modeHint) "on" else "off")) { state.dropModeRequest() }
            TokChip("Probe") {
                state.webView?.loadDataWithBaseURL("https://probe.local/", TokWeb.PROBE_HTML, "text/html", "utf-8", null)
            }
            TokChip("Plain " + (if (state.plain) "on" else "off")) { state.plain = !state.plain }
            TokChip("Wide " + (if (state.wideOn) "on" else "off")) {
                state.wideOn = !state.wideOn
                TokWeb.wide = state.wideOn
                state.webView?.let { web ->
                    applyTokMode(web, state.desktop)
                    web.reload()
                }
            }
        }
        Text(
            state.nativeText + " " + state.remoteStatus + "\n" + (if (state.desktop) "desktop " else "mobile ") + state.diag +
                (if (state.net.isNotEmpty()) "\nnet: " + state.net else "") +
                (if (state.console.isNotEmpty()) "\njs: " + state.console else ""),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            minLines = 7,
            maxLines = 7,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun TokChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color.White, fontSize = 11.sp)
    }
}
