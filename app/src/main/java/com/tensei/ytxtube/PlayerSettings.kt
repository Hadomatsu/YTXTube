package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PlayerControlSettings {
    private const val KEY_ENABLED = "show_quick_speed_quality"
    private var prefs: SharedPreferences? = null

    var showQuickControls by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        showQuickControls = prefs?.getBoolean(KEY_ENABLED, true) ?: true
    }

    fun set(on: Boolean) {
        showQuickControls = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }
}

/**
 * Home feed layout: a grid of videos side by side (default) or one big card per row. The
 * number of columns is not stored; it follows the screen width (see [HomeGrid]).
 */
object HomeLayoutSettings {
    private const val KEY_GRID = "home_grid"
    private var prefs: SharedPreferences? = null

    var grid by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        grid = prefs?.getBoolean(KEY_GRID, true) ?: true
        loadColumns()
    }

    fun set(on: Boolean) {
        grid = on
        prefs?.edit()?.putBoolean(KEY_GRID, on)?.apply()
    }

    /** Columns on Home, folded (narrow) and unfolded (wide); 0 is automatic. */
    var columnsNarrow by mutableStateOf(0)
        private set
    var columnsWide by mutableStateOf(0)
        private set

    fun loadColumns() {
        columnsNarrow = prefs?.getInt("home_columns_narrow", 0) ?: 0
        columnsWide = prefs?.getInt("home_columns_wide", 0) ?: 0
    }

    fun setColumns(wide: Boolean, value: Int) {
        if (wide) columnsWide = value else columnsNarrow = value
        prefs?.edit()?.putInt(if (wide) "home_columns_wide" else "home_columns_narrow", value)?.apply()
    }

    /** Pinching the feed: [delta] +1 for one more column, -1 for one fewer (1 to 3). */
    fun pinch(widthDp: Float, delta: Int): Boolean {
        val wide = widthDp >= HomeGrid.TWO_COLUMNS_FROM_DP
        val now = if (grid) HomeGrid.columns(widthDp) else 1
        val next = (now + delta).coerceIn(1, 3)
        if (next == now && grid) return false
        if (!grid) set(true)
        setColumns(wide, next)
        return true
    }
}

object PipSettings {
    private const val KEY_ENABLED = "app_pip_enabled"
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, true) ?: true
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }
}

object MiniPlayerSettings {
    private const val KEY_USE_LIQUID = "use_liquid_mini_player"
    private var prefs: SharedPreferences? = null

    var useLiquidMiniPlayer by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        useLiquidMiniPlayer = prefs?.getBoolean(KEY_USE_LIQUID, false) ?: false
    }

    fun set(on: Boolean) {
        useLiquidMiniPlayer = on
        prefs?.edit()?.putBoolean(KEY_USE_LIQUID, on)?.apply()
    }
}
