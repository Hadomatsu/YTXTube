package com.tensei.ytxtube

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * The TikTok client is a native shell around tiktok.com. TikTok's own page supplies the account,
 * the For You feed and every interaction; this file only wraps it: a phone user agent, a small
 * script that hides TikTok's own bottom bar and app-install nags, and navigation that goes through
 * TikTok's own links so nothing about the account or the feed is touched.
 */
internal object TokWeb {
    const val HOME = "https://www.tiktok.com/foryou"


    /** Tab index to the TikTok link that opens it (the same links TikTok's own bottom bar uses). */
    val tabs = listOf(
        TokTabSpec("Home", "home-icon", "https://www.tiktok.com/foryou"),
        TokTabSpec("Discover", "discover-icon", "https://www.tiktok.com/discover"),
        TokTabSpec("Inbox", "dm-icon", "https://www.tiktok.com/inbox"),
        TokTabSpec("Profile", "profile-icon", "https://www.tiktok.com/profile"),
    )

    /** Hosts that belong to TikTok and may load inside the app (logins and captchas bounce through several). */
    private val ownHosts = listOf(
        "tiktok.com", "tiktokv.com", "tiktokcdn.com", "tiktokcdn-us.com", "tiktokw.us", "tiktokw.eu",
        "byteoversea.com", "bytedance.com", "ibytedtos.com", "ttwstatic.com", "musical.ly", "tiktokshop.com",
    )

    fun isOwnHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return ownHosts.any { h == it || h.endsWith(".$it") }
    }

    /** Which bottom-bar tab a TikTok page belongs to, or -1 when it belongs to none of them. */
    fun tabForUrl(url: String?): Int {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return -1
        if (!isOwnHost(uri.host)) return -1
        val path = (uri.path ?: "").trimEnd('/')
        return when {
            path.isEmpty() || path == "/foryou" || path == "/following" || path == "/friends" -> 0
            path == "/discover" || path == "/explore" || path == "/search" -> 1
            path == "/inbox" || path == "/messages" || path == "/notifications" -> 2
            path == "/profile" -> 3
            else -> -1
        }
    }

    /**
     * The identity Chrome sends, with the Chrome version of this WebView: the phone one (device name
     * reduced to "K", no "wv" or "Build/") so TikTok serves its normal phone page, or the desktop one
     * for pages the phone site does not offer, such as signing in.
     */
    fun userAgent(context: Context, desktop: Boolean = false): String {
        val default = WebSettings.getDefaultUserAgent(context)
        val chrome = Regex("Chrome/([0-9.]+)").find(default)?.groupValues?.get(1) ?: "138.0.0.0"
        return if (desktop) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome + " Safari/537.36"
        } else {
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome + " Mobile Safari/537.36"
        }
    }

    /**
     * Starts TikTok's own theme cookies on dark the first time this install runs, so the feed is black like the
     * rest of the app. After that a theme picked in TikTok's settings is never overwritten.
     */
    fun presetDarkTheme(context: Context) {
        val prefs = context.getSharedPreferences("tok", Context.MODE_PRIVATE)
        val cookies = CookieManager.getInstance()
        val existing = cookies.getCookie("https://www.tiktok.com") ?: ""
        if (prefs.getBoolean("dark_applied", false) && existing.contains("tiktok_webapp_theme=")) return
        for (name in listOf("tiktok_webapp_theme", "tiktok_webapp_theme_source")) {
            cookies.setCookie(
                "https://www.tiktok.com",
                name + "=dark; Domain=.tiktok.com; Path=/; Max-Age=31536000; Secure; SameSite=Lax",
            )
        }
        cookies.flush()
        prefs.edit().putBoolean("dark_applied", true).apply()
    }

    /**
     * Runs on every TikTok page, as early as the browser allows (and again when the page finishes,
     * which is harmless: it guards itself). It only restyles and dismisses things:
     *  - black page, no scrollbars, no tap flash;
     *  - TikTok's own bottom bar is made invisible but keeps its place and its links, because the
     *    glass bar of this app clicks those links;
     *  - the "open the app" pill, the app-install pop-up and its overlay are removed.
     * The cookie notice is left alone: that choice is the viewer's.
     * No dollar signs in here, this sits in a Kotlin raw string.
     */
    private const val SCRIPT_BODY = """
(function () {
  if (window.__ytxInit) return;
  window.__ytxInit = true;
  var CSS = 'html,body{overscroll-behavior:none}' +
    '*{-webkit-tap-highlight-color:transparent}' +
    '::-webkit-scrollbar{display:none}' +
    '[data-ytx-nav]{opacity:0 !important;pointer-events:none !important}' +
    '[data-ytx-hide]{display:none !important}' +
    '[data-ytx-wall]{display:none !important}';
  function addStyle() {
    var host = document.head || document.documentElement;
    if (!host) return;
    if (document.getElementById('ytx-style')) return;
    var s = document.createElement('style');
    s.id = 'ytx-style';
    s.textContent = CSS;
    host.appendChild(s);
  }
  var lastScan = 0;
  var APP_PILLS = ['open app', 'open on tiktok', 'open tiktok', 'open in app', 'discover more', 'watch on tiktok', 'get app', 'get the app', 'use the app'];
  function sweep() {
    addStyle();
    var h = document.querySelector('[data-e2e="home-icon"]');
    var p = document.querySelector('[data-e2e="profile-icon"]');
    if (h && p && !document.querySelector('[data-ytx-nav]')) {
      var e = h;
      while (e && !e.contains(p)) e = e.parentElement;
      if (e && e !== document.body && e !== document.documentElement && e.getBoundingClientRect().height <= 120) {
        e.setAttribute('data-ytx-nav', '1');
      }
    }
    // Only on pages that have TikTok's normal bottom bar. On other pages (sign-in) the same card can be the page itself.
    if (h) {
      var no = document.querySelector('[data-e2e="alt-middle-cta-cancel-btn"]');
      if (no && !no.__ytxClicked) { no.__ytxClicked = true; no.click(); }
      var cta = document.querySelector('[data-e2e="middle-cta-container"]');
      if (cta && !cta.closest('[data-ytx-wall]')) {
        // Hide the card together with the wrapper that holds its dimming layer, but never anything holding the feed.
        var top = cta;
        while (top.parentElement && top.parentElement !== document.body && top.parentElement !== document.documentElement) {
          var par = top.parentElement;
          if (par.querySelector('[data-e2e="video-card"],[data-e2e="home-icon"],[data-e2e="profile-icon"],[data-e2e="header-foryou"]')) break;
          top = par;
        }
        top.setAttribute('data-ytx-wall', '1');
      }
    }
    var vids = document.querySelectorAll('video');
    for (var k = 0; k < vids.length; k++) {
      var v = vids[k];
      if (v.__ytxTried || v.readyState < 2) continue;
      var vr = v.getBoundingClientRect();
      if (!(vr.height > 0 && vr.top < innerHeight * 0.6 && vr.bottom > innerHeight * 0.4)) continue;
      v.__ytxTried = true;
      if (v.paused) {
        var pr1 = v.play();
        if (pr1 && pr1.catch) pr1.catch(function () { v.muted = true; var pr2 = v.play(); if (pr2 && pr2.catch) pr2.catch(function () {}); });
      }
    }
    var now = Date.now();
    if (now - lastScan > 1500) {
      lastScan = now;
      // TikTok's floating "open the app" pills (their wording differs per page). Only small pills are hidden.
      var all = document.querySelectorAll('a,button,span,div');
      for (var i = 0; i < all.length; i++) {
        var c = all[i];
        if (c.childElementCount > 4) continue;
        var t = (c.textContent || '').trim().toLowerCase();
        if (APP_PILLS.indexOf(t) < 0) continue;
        if (c.closest('[data-ytx-hide]')) continue;
        var cr = c.getBoundingClientRect();
        if (cr.width > 260 || cr.height > 64 || cr.width === 0) continue;
        var r = c;
        while (r.parentElement && r.parentElement !== document.body) {
          var pr = r.parentElement.getBoundingClientRect();
          if ((r.parentElement.textContent || '').trim().toLowerCase() !== t || pr.width > 260 || pr.height > 64) break;
          r = r.parentElement;
        }
        r.setAttribute('data-ytx-hide', '1');
      }
    }
  }
  var timer = 0;
  var observing = false;
  function observe() {
    if (observing) return;
    var root = document.documentElement;
    if (!root) return;
    try {
      new MutationObserver(schedule).observe(root, { childList: true, subtree: true });
      observing = true;
    } catch (x) {}
  }
  function schedule() {
    observe();
    if (timer) return;
    timer = setTimeout(function () { timer = 0; try { sweep(); } catch (x) {} }, 250);
  }
  // At document start the page may not have a root element yet, so keep trying as it loads.
  document.addEventListener('readystatechange', schedule);
  document.addEventListener('DOMContentLoaded', schedule);
  try { addStyle(); observe(); } catch (x) {}
  setInterval(schedule, 1500);
  schedule();
})();
"""
    val script: String = SCRIPT_BODY

    /** Clicks one of TikTok's own bottom-bar links; answers "1" when it found it, so the caller can fall back to loading the page. */
    fun clickScript(e2e: String): String =
        "(function(){var a=document.querySelector('[data-e2e=\"$e2e\"]');if(a){a.click();return '1';}return '0';})()"

    /** Stops every video, for when the app leaves the screen. */
    const val PAUSE_SCRIPT =
        "(function(){document.querySelectorAll('video').forEach(function(v){try{v.pause();}catch(x){}});})()"

    /** Plays the video that is on screen, for when the app comes back. */
    const val RESUME_SCRIPT =
        "(function(){var best=null;document.querySelectorAll('video').forEach(function(v){var r=v.getBoundingClientRect();" +
            "if(r.height>0&&r.top<innerHeight*0.6&&r.bottom>innerHeight*0.4){best=v;}});" +
            "if(best){var p=best.play();if(p&&p.catch)p.catch(function(){});}})()"
}

internal class TokTabSpec(val label: String, val e2e: String, val url: String)

/** Everything the Compose side needs to know about the page, and a handle to the WebView. */
internal class TokState {
    var tab by mutableIntStateOf(0)
    /** The tab the web page was last sent to, so the bar's two callbacks cannot navigate twice. */
    var navigatedTab = 0
    var progress by mutableIntStateOf(0)
    var loadedOnce by mutableStateOf(false)
    var error by mutableStateOf(false)
    var errorDetail by mutableStateOf("")
    /** Whether the page is loaded as the desktop site (needed for some sign-in screens). */
    var desktop by mutableStateOf(false)
    var webView: WebView? = null
    var pendingUrl: String? = null

    /** The bar was tapped: send the page to that tab (tapping Home again refreshes the feed, TikTok's own behaviour). */
    fun tap(index: Int) {
        tab = index
        navigatedTab = index
        open(index)
    }

    /** The bar settled on a tab by dragging; only navigate when the page is not already there. */
    fun settle(index: Int) {
        if (index == navigatedTab) return
        tab = index
        navigatedTab = index
        open(index)
    }

    private fun open(index: Int) {
        val web = webView ?: return
        val spec = TokWeb.tabs.getOrNull(index) ?: return
        error = false
        web.evaluateJavascript(TokWeb.clickScript(spec.e2e)) { result ->
            if (result?.contains('1') != true) web.loadUrl(spec.url)
        }
    }

    fun onUrl(url: String?) {
        val t = TokWeb.tabForUrl(url)
        if (t >= 0) {
            tab = t
            navigatedTab = t
        }
    }

    fun retry() {
        error = false
        progress = 0
        val web = webView ?: return
        if (web.url.isNullOrEmpty() || web.url == "about:blank") web.loadUrl(TokWeb.HOME) else web.reload()
    }

    /** Switches between the phone site and the desktop site and reloads; the choice is kept. */
    fun switchSite(value: Boolean) {
        val web = webView ?: return
        desktop = value
        web.context.getSharedPreferences("tok", Context.MODE_PRIVATE).edit().putBoolean("desktop", value).apply()
        applyTokMode(web, value)
        error = false
        progress = 0
        web.reload()
    }

    fun load(url: String) {
        val web = webView
        if (web == null) pendingUrl = url else {
            error = false
            web.loadUrl(url)
        }
    }
}

private class TokClient(private val state: TokState, private val context: Context) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase()
        // App deep links (snssdk://, intent://, market://) would bounce to the TikTok app or a store.
        if (scheme != "http" && scheme != "https") return true
        if (TokWeb.isOwnHost(uri.host)) return false
        // Redirects that nobody tapped (login and captcha hops) stay inside; a tapped outside link opens the browser.
        if (!request.hasGesture() || !request.isForMainFrame) return false
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        state.progress = 0
        state.error = false
        view.evaluateJavascript(TokWeb.script, null)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        view.evaluateJavascript(TokWeb.script, null)
        state.loadedOnce = true
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        state.onUrl(url)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            state.errorDetail = error.errorCode.toString() + " " + error.description
            state.error = true
        }
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            state.errorDetail = "HTTP " + errorResponse.statusCode + " " + (errorResponse.reasonPhrase ?: "")
            state.error = true
        }
    }
}

/** A WebView set up for TikTok: phone identity, cookies kept for the account, videos that start by themselves. */
@SuppressLint("SetJavaScriptEnabled")
internal fun createTokWebView(context: Context, state: TokState): WebView {
    val web = WebView(context)
    // Compose adds the view with wrap-content layout params by default. A WebView whose height is wrap-content
    // makes Chromium force the page's layout height to zero (so the page can size the view), which turns every
    // 100vh into 0 and collapses TikTok's feed. The view always fills the space Compose gives it, so say so.
    web.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    web.setBackgroundColor(AColor.BLACK)
    web.overScrollMode = View.OVER_SCROLL_NEVER
    web.isVerticalScrollBarEnabled = false
    web.isHorizontalScrollBarEnabled = false
    web.isFocusable = true
    web.isFocusableInTouchMode = true

    val s = web.settings
    s.javaScriptEnabled = true
    s.domStorageEnabled = true
    s.mediaPlaybackRequiresUserGesture = false
    s.textZoom = 100
    s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    s.allowFileAccess = false
    s.allowContentAccess = false
    state.desktop = false
    applyTokMode(web, state.desktop)

    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(web, true)
    TokWeb.presetDarkTheme(context)

    web.webViewClient = TokClient(state, context)
    web.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            state.progress = newProgress
        }

    }

    // Runs before the page's own scripts when the WebView supports it, so the app-install pop-up never flashes.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(
                web,
                TokWeb.script,
                setOf("https://*.tiktok.com", "https://tiktok.com"),
            )
        }
    }
    if (Build.VERSION.SDK_INT >= 35) {
        runCatching { web.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH }
    }
    // The page follows the app's own (dark) theme for prefers-color-scheme; it must not be recoloured on top of that.
    if (Build.VERSION.SDK_INT >= 33) {
        runCatching { web.settings.isAlgorithmicDarkeningAllowed = false }
    }
    return web
}

/** The phone site (default) or the desktop site: identity, and whether the page can be pinched. */
internal fun applyTokMode(web: WebView, desktop: Boolean) {
    val s = web.settings
    s.userAgentString = TokWeb.userAgent(web.context, desktop)
    // The layout width is always the view's own width (the phone page asks for exactly that anyway).
    s.useWideViewPort = false
    s.loadWithOverviewMode = false
    s.setSupportZoom(desktop)
    s.builtInZoomControls = desktop
    s.displayZoomControls = false
}
