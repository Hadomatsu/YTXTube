package com.tensei.ytxtube

import android.app.Application

/**
 * YTXTok's application. It starts only what the shared glass shell needs (theme, glass quality,
 * display settings), not the YouTube side (stream extraction, downloads, account, player settings).
 */
class TokApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TvMode.init(this)
        LanguageSettings.init(this)
        GlassQuality.init(this)
        GlassSettings.init(this)
        ThemeSettings.init(this)
        FpsSettings.init(this)
    }
}
