package com.tensei.ytxtube

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/** How a foldable is being held. */
enum class FoldMode { Flat, Tabletop, Book }

@Immutable
data class FoldState(
    val mode: FoldMode = FoldMode.Flat,
    /** The hinge in window pixels (top/bottom when it runs across, left/right when it runs down); null = the middle. */
    val hingeStart: Int? = null,
    val hingeEnd: Int? = null,
    /** Where the posture came from, shown in Settings: "WindowManager", "hinge sensor" or "none". */
    val source: String = "none",
    /** The hinge angle when the phone has a hinge sensor (180 = flat open). */
    val angle: Float? = null,
)

/**
 * Flex mode: on a foldable stood half open, the video plays on the upper half and the controls,
 * up next and comments sit on the lower half (held like a book, the video is on the left page).
 *
 * The fold is read two ways. Android's WindowManager reports the fold and its exact position on
 * phones that support it; the hinge angle sensor (which the Honor Magic V series has) is the
 * fallback when WindowManager says nothing or keeps saying "flat", so it also works where the
 * phone only reports the angle.
 */
object FlexMode {
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set
    var bookEnabled by mutableStateOf(true)
        private set

    /** What the phone reports right now. */
    var state by mutableStateOf(FoldState())
        internal set

    fun init(context: Context) {
        val p = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean("flex_mode", true)
        bookEnabled = p.getBoolean("flex_book", true)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean("flex_mode", value)?.apply()
    }

    fun updateBook(value: Boolean) {
        bookEnabled = value
        prefs?.edit()?.putBoolean("flex_book", value)?.apply()
    }

    /** The posture the player should use now, after the settings. */
    val active: FoldMode
        get() = when {
            !enabled -> FoldMode.Flat
            state.mode == FoldMode.Book && !bookEnabled -> FoldMode.Flat
            else -> state.mode
        }

    fun describe(): String {
        val s = state
        val pose = when (s.mode) {
            FoldMode.Flat -> "flat or closed"
            FoldMode.Tabletop -> "half open, standing (tabletop)"
            FoldMode.Book -> "half open, like a book"
        }
        val angle = s.angle?.let { " · hinge ${it.toInt()}°" }.orEmpty()
        val from = when (s.source) {
            "none" -> "no fold information from this phone"
            else -> "from ${s.source}"
        }
        return "Now: $pose$angle ($from)"
    }
}

/** Watches the fold while the app is open and keeps [FlexMode.state] up to date. */
@Composable
fun TrackFoldPosture(activity: Activity) {
    val feature by produceState<FoldingFeature?>(null, activity) {
        runCatching {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
                value = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            }
        }
    }
    val hinge = rememberHingeAngle(activity)
    val angle = hinge.value
    val config = LocalConfiguration.current
    val landscape = config.screenWidthDp > config.screenHeightDp
    // The big inner screen; the cover screen (folded) never gets flex mode.
    val innerScreen = config.smallestScreenWidthDp >= 480

    // A little give either way, so holding it near the edge of "half open" does not flicker.
    val halfOpen = remember { BooleanArray(1) }
    if (angle != null) {
        halfOpen[0] = if (halfOpen[0]) angle in 15f..165f else angle in 30f..150f
    } else {
        halfOpen[0] = false
    }

    val f = feature
    val state = when {
        f != null && f.state == FoldingFeature.State.HALF_OPENED -> {
            val across = f.orientation == FoldingFeature.Orientation.HORIZONTAL
            FoldState(
                mode = if (across) FoldMode.Tabletop else FoldMode.Book,
                hingeStart = if (across) f.bounds.top else f.bounds.left,
                hingeEnd = if (across) f.bounds.bottom else f.bounds.right,
                source = "WindowManager",
                angle = angle,
            )
        }
        halfOpen[0] && innerScreen -> {
            // WindowManager is silent (or says flat): the hinge sensor decides. On a book-style
            // foldable the hinge runs across the screen when it is turned sideways.
            val across = f?.let { it.orientation == FoldingFeature.Orientation.HORIZONTAL } ?: landscape
            FoldState(
                mode = if (across) FoldMode.Tabletop else FoldMode.Book,
                hingeStart = f?.let { if (across) it.bounds.top else it.bounds.left },
                hingeEnd = f?.let { if (across) it.bounds.bottom else it.bounds.right },
                source = "hinge sensor",
                angle = angle,
            )
        }
        else -> FoldState(
            mode = FoldMode.Flat,
            source = when {
                f != null -> "WindowManager"
                angle != null -> "hinge sensor"
                else -> "none"
            },
            angle = angle,
        )
    }
    SideEffect { FlexMode.state = state }
}

/** The hinge angle, listened to only while the app is in the foreground. */
@Composable
private fun rememberHingeAngle(context: Context): State<Float?> {
    val angle = remember { mutableStateOf<Float?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = if (Build.VERSION.SDK_INT >= 30) sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) else null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                angle.value = event.values.firstOrNull()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun start() {
            if (sensor != null && sm != null && !registered) {
                registered = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        fun stop() {
            if (registered) {
                sm?.unregisterListener(listener)
                registered = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
    return angle
}
