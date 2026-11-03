package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.View
import android.view.Choreographer
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/** Whether the live frame-rate readout is shown. Off by default, switched on in Settings. */
object FpsSettings {
    private const val KEY = "show_fps_counter"
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY, false) ?: false
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY, on)?.apply()
    }
}

/**
 * Votes for the high frame-rate category on the view that actually draws the app.
 *
 * On Android 15 and newer a view that does not vote is treated as "normal" (about 60 Hz) except
 * while a finger is on the screen. The vote used to be placed on the window's outer decor view,
 * and a vote on a parent does not reach its children, so the Compose view was still at "normal".
 */
@Composable
fun RequestHighFrameRate() {
    val view = LocalView.current
    SideEffect {
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching { view.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH }
        }
    }
}

/**
 * A small readout in the top corner: how many frames per second the screen is really delivering to
 * the app, the slowest frame of the last half second, and the refresh rate the panel reports.
 *
 * It counts screen refreshes, so it shows what the phone gives the app (60, 90 or 120). A slowest
 * frame far above 8 ms on a 120 Hz screen means frames are being dropped by the app itself.
 */
@Composable
fun FpsOverlay() {
    if (!FpsSettings.enabled) return
    val view = LocalView.current
    var fps by remember { mutableIntStateOf(0) }
    var worstMs by remember { mutableIntStateOf(0) }
    var panelHz by remember { mutableIntStateOf(0) }
    var offered by remember { mutableStateOf("") }

    DisposableEffect(view) {
        val choreographer = Choreographer.getInstance()
        var windowStart = 0L
        var last = 0L
        var frames = 0
        var worst = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (last != 0L) worst = max(worst, frameTimeNanos - last)
                last = frameTimeNanos
                if (windowStart == 0L) windowStart = frameTimeNanos
                frames++
                val elapsed = frameTimeNanos - windowStart
                if (elapsed >= 500_000_000L) {
                    fps = (frames * 1_000_000_000.0 / elapsed).roundToInt()
                    worstMs = (worst / 1_000_000L).toInt()
                    panelHz = runCatching { view.display?.refreshRate?.roundToInt() }.getOrNull() ?: 0
                    offered = describeDisplay(view)
                    frames = 0
                    windowStart = frameTimeNanos
                    worst = 0L
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }

    // No pointer handling here, so touches go straight through to the app underneath.
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopEnd) {
        Text(
            text = "$fps fps  |  slowest $worstMs ms  |  panel $panelHz Hz\n$offered",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(top = 4.dp, end = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * What the phone offers this app: the refresh rates of the current screen mode, whether it has
 * adaptive refresh rate (Android 15+), and what the window has asked for.
 */
private fun describeDisplay(view: View): String {
    val display = view.display ?: return "no display"
    val current = display.mode
    val rates = display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .map { it.refreshRate.roundToInt() }
        .distinct()
        .sorted()
        .joinToString("/")
    val arr = runCatching {
        Display::class.java.getMethod("hasArrSupport").invoke(display) as? Boolean
    }.getOrNull()
    val attrs = (view.context as? android.app.Activity)?.window?.attributes
    val asked = attrs?.preferredRefreshRate?.roundToInt() ?: 0
    return "modes $rates  |  asked $asked  |  ARR ${when (arr) { true -> "yes"; false -> "no"; null -> "?" }}  |  android ${Build.VERSION.SDK_INT}"
}
