package com.tensei.ytxtube

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

/**
 * The TikTok client's own launcher entry. It lives in the same app as the YouTube client and shares
 * its theme, liquid-glass bar and display settings, but it is a separate screen in a separate task.
 * Sign-in, the feed and every interaction stay on tiktok.com itself.
 */
class TokActivity : ComponentActivity() {
    private val state = TokState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        state.pendingUrl = linkFrom(intent)
        setContent {
            YtxTubeTheme {
                Box(Modifier.fillMaxSize()) {
                    RequestHighFrameRate()
                    TokScreen(state, onExit = { finish() })
                    FpsOverlay()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkFrom(intent)?.let { state.load(it) }
    }

    override fun onResume() {
        super.onResume()
        requestHighestRefreshRate()
        state.webView?.let { web ->
            web.onResume()
            web.evaluateJavascript(TokWeb.RESUME_SCRIPT, null)
        }
    }

    override fun onPause() {
        super.onPause()
        state.webView?.let { web ->
            web.evaluateJavascript(TokWeb.PAUSE_SCRIPT, null)
            web.onPause()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        requestHighestRefreshRate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) requestHighestRefreshRate()
    }

    /** A TikTok link opened from another app (a shared video, a profile) loads here instead of the home feed. */
    private fun linkFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme != "https" || !TokWeb.isOwnHost(uri.host)) return null
        return uri.toString()
    }

    /** The same request the YouTube screen makes: the panel's top mode at the current resolution. */
    private fun requestHighestRefreshRate() {
        if (!state.modeHint) return
        runCatching {
            @Suppress("DEPRECATION")
            val disp = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay)
                ?: return@runCatching
            val current = disp.mode
            val best = disp.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: disp.supportedModes.maxByOrNull { it.refreshRate }
            if (best != null) {
                val lp = window.attributes
                if (lp.preferredDisplayModeId != best.modeId || lp.preferredRefreshRate != best.refreshRate) {
                    lp.preferredDisplayModeId = best.modeId
                    lp.preferredRefreshRate = best.refreshRate
                    window.attributes = lp
                }
            }
        }
    }
}
