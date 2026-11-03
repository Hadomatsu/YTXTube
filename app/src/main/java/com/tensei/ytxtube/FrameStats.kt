package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.view.Choreographer
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
            text = "$fps fps  |  slowest $worstMs ms  |  panel $panelHz Hz",
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
