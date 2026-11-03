package com.tensei.ytxtube

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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

    /** The same Chrome identity an Android phone sends, so TikTok serves its phone layout. */
    fun userAgent(context: Context): String =
        WebSettings.getDefaultUserAgent(context).replace("; wv", "").replace(" Version/4.0", "")

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
  var CSS = 'html,body{background:#000 !important;overscroll-behavior:none}' +
    '*{-webkit-tap-highlight-color:transparent}' +
    '::-webkit-scrollbar{display:none}' +
    '[data-ytx-nav]{opacity:0 !important;pointer-events:none !important}' +
    '[data-ytx-hide]{display:none !important}' +
    '[data-e2e="middle-cta-container"]{display:none !important}';
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
  function sweep() {
    addStyle();
    var h = document.querySelector('[data-e2e="home-icon"]');
    var p = document.querySelector('[data-e2e="profile-icon"]');
    if (h && p && !document.querySelector('[data-ytx-nav]')) {
      var e = h;
      while (e && !e.contains(p)) e = e.parentElement;
      if (e && e !== document.body && e !== document.documentElement) e.setAttribute('data-ytx-nav', '1');
    }
    var no = document.querySelector('[data-e2e="alt-middle-cta-cancel-btn"]');
    if (no) no.click();
    var now = Date.now();
    if (now - lastScan > 1500 && !document.querySelector('[data-ytx-hide]')) {
      lastScan = now;
      var all = document.querySelectorAll('a,button,span,div');
      for (var i = 0; i < all.length; i++) {
        var c = all[i];
        if (c.childElementCount > 4) continue;
        var t = (c.textContent || '').trim().toLowerCase();
        if (t !== 'open app') continue;
        var r = c;
        while (r.parentElement && r.parentElement !== document.body &&
               (r.parentElement.textContent || '').trim().toLowerCase() === 'open app' &&
               r.parentElement.getBoundingClientRect().width <= 220) {
          r = r.parentElement;
        }
        r.setAttribute('data-ytx-hide', '1');
        break;
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
        if (request.isForMainFrame) state.error = true
    }
}

/** A WebView set up for TikTok: phone identity, cookies kept for the account, videos that start by themselves. */
@SuppressLint("SetJavaScriptEnabled")
internal fun createTokWebView(context: Context, state: TokState): WebView {
    val web = WebView(context)
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
    s.useWideViewPort = true
    s.loadWithOverviewMode = false
    s.textZoom = 100
    s.setSupportZoom(false)
    s.builtInZoomControls = false
    s.displayZoomControls = false
    s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    s.allowFileAccess = false
    s.allowContentAccess = false
    s.userAgentString = TokWeb.userAgent(context)

    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(web, true)

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
    return web
}
