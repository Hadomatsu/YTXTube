package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which audio rendition is streamed, for videos and music alike. */
enum class AudioQuality(val label: String, val detail: String) {
    BEST("Best available", "Highest bitrate YouTube offers for this track, usually Opus around 130 to 160 kbps"),
    PREFER_AAC("Prefer AAC", "AAC (m4a) when there is one, around 128 kbps, or 256 kbps where YouTube serves it"),
    PREFER_OPUS("Prefer Opus", "Opus (webm) when there is one, the most efficient codec YouTube uses"),
    DATA_SAVER("Data saver", "Lowest bitrate, around 50 to 70 kbps, to save mobile data"),
}

/** Where the lyrics come from, tried in this order. */
enum class LyricsMode(val label: String, val detail: String) {
    WORD_SYNCED("All sources", "Like BitChord: Apple Music word timing (lrc.red, BiniLyrics, BetterLyrics, PaxSenix, LyricsPlus), then Unison, KuGou, LRCLIB, Musixmatch and Genius, then YouTube Music"),
    LINE_SYNCED("Quick", "Only LRCLIB and YouTube Music's own lyrics, line by line"),
}

/**
 * Settings that change how videos and music play. Every value here is read by the code that does
 * the work, so each switch in Settings changes something real.
 */
object PlaybackPrefs {
    private var prefs: SharedPreferences? = null

    val seekSteps = listOf(5, 10, 15, 20, 30, 60)
    val speedChoices = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /** How far double tap and the skip buttons jump. */
    var seekStepSec by mutableIntStateOf(10)
        private set
    var autoplay by mutableStateOf(true)
        private set
    var loopByDefault by mutableStateOf(false)
        private set
    var resume by mutableStateOf(true)
        private set
    var skipSilence by mutableStateOf(false)
        private set
    var defaultSpeed by mutableFloatStateOf(1f)
        private set
    var audioQuality by mutableStateOf(AudioQuality.BEST)
        private set

    var lyricsMode by mutableStateOf(LyricsMode.WORD_SYNCED)
        private set
    /** Show a Latin-letter reading under each line when a song is in another script. */
    var romanizeLyrics by mutableStateOf(true)
        private set
    /** Lines that are not being sung are softly blurred, as on Apple Music. */
    var lyricsBlur by mutableStateOf(true)
        private set
    /** Moves the lyrics earlier (negative) or later (positive) against the music. */
    var lyricsOffsetMs by mutableIntStateOf(0)
        private set
    var lyricsTextLarge by mutableStateOf(true)
        private set
    var keepScreenOnLyrics by mutableStateOf(true)
        private set

    val holdSpeeds = listOf(1.5f, 2f, 2.5f, 3f)
    /** Speed while a finger is held on the video. */
    var holdSpeed by mutableFloatStateOf(2f)
        private set
    var showDislikes by mutableStateOf(true)
        private set
    /** Keep playing when the app is swiped away from recents (off: stop, as before). */
    var keepPlayingOnSwipe by mutableStateOf(false)
        private set
    /** Pause when another app starts playing sound (calls, other players). */
    var pauseForOtherAudio by mutableStateOf(true)
        private set
    /** Index of the phone's equalizer preset, or -1 for off. */
    var eqPreset by mutableIntStateOf(-1)
        private set
    /** Bass boost strength, 0 (off) to 1000. */
    var bassBoost by mutableIntStateOf(0)
        private set

    val seekStepMs: Long get() = seekStepSec * 1000L

    fun init(context: Context) {
        val p = context.getSharedPreferences("more_prefs", Context.MODE_PRIVATE).also { prefs = it }
        seekStepSec = p.getInt("seek_step", 10).takeIf { it in seekSteps } ?: 10
        autoplay = p.getBoolean("autoplay", true)
        loopByDefault = p.getBoolean("loop_default", false)
        resume = p.getBoolean("resume", true)
        skipSilence = p.getBoolean("skip_silence", false)
        defaultSpeed = p.getFloat("default_speed", 1f).takeIf { it in speedChoices } ?: 1f
        audioQuality = runCatching { AudioQuality.valueOf(p.getString("audio_quality", null) ?: "BEST") }
            .getOrDefault(AudioQuality.BEST)
        lyricsMode = runCatching { LyricsMode.valueOf(p.getString("lyrics_mode", null) ?: "WORD_SYNCED") }
            .getOrDefault(LyricsMode.WORD_SYNCED)
        romanizeLyrics = p.getBoolean("romanize_lyrics", true)
        lyricsBlur = p.getBoolean("lyrics_blur", true)
        lyricsOffsetMs = p.getInt("lyrics_offset", 0).coerceIn(-3000, 3000)
        lyricsTextLarge = p.getBoolean("lyrics_large", true)
        keepScreenOnLyrics = p.getBoolean("lyrics_screen_on", true)
        holdSpeed = p.getFloat("hold_speed", 2f).takeIf { it in holdSpeeds } ?: 2f
        showDislikes = p.getBoolean("show_dislikes", true)
        keepPlayingOnSwipe = p.getBoolean("keep_on_swipe", false)
        pauseForOtherAudio = p.getBoolean("audio_focus", true)
        eqPreset = p.getInt("eq_preset", -1)
        bassBoost = p.getInt("bass_boost", 0).coerceIn(0, 1000)
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply { block() }?.apply()
    }

    fun updateSeekStep(sec: Int) { seekStepSec = sec; edit { putInt("seek_step", sec) } }
    fun updateAutoplay(on: Boolean) { autoplay = on; edit { putBoolean("autoplay", on) } }
    fun updateLoopByDefault(on: Boolean) { loopByDefault = on; edit { putBoolean("loop_default", on) } }
    fun updateResume(on: Boolean) { resume = on; edit { putBoolean("resume", on) } }
    fun updateSkipSilence(on: Boolean) {
        skipSilence = on
        edit { putBoolean("skip_silence", on) }
        PlaybackService.applyLivePrefs()
    }
    fun updateDefaultSpeed(v: Float) { defaultSpeed = v; edit { putFloat("default_speed", v) } }
    fun updateAudioQuality(q: AudioQuality) {
        // Streams already playing keep their rendition; the next song or video uses the new choice.
        audioQuality = q
        edit { putString("audio_quality", q.name) }
    }
    fun updateLyricsMode(m: LyricsMode) { lyricsMode = m; edit { putString("lyrics_mode", m.name) } }
    fun updateRomanize(on: Boolean) { romanizeLyrics = on; edit { putBoolean("romanize_lyrics", on) } }
    fun updateLyricsBlur(on: Boolean) { lyricsBlur = on; edit { putBoolean("lyrics_blur", on) } }
    fun updateLyricsOffset(ms: Int) { lyricsOffsetMs = ms.coerceIn(-3000, 3000); edit { putInt("lyrics_offset", lyricsOffsetMs) } }
    fun updateLyricsLarge(on: Boolean) { lyricsTextLarge = on; edit { putBoolean("lyrics_large", on) } }
    fun updateKeepScreenOnLyrics(on: Boolean) { keepScreenOnLyrics = on; edit { putBoolean("lyrics_screen_on", on) } }
    fun updateHoldSpeed(v: Float) { holdSpeed = v; edit { putFloat("hold_speed", v) } }
    fun updateShowDislikes(on: Boolean) { showDislikes = on; edit { putBoolean("show_dislikes", on) } }
    fun updateKeepPlayingOnSwipe(on: Boolean) { keepPlayingOnSwipe = on; edit { putBoolean("keep_on_swipe", on) } }
    fun updatePauseForOtherAudio(on: Boolean) {
        pauseForOtherAudio = on
        edit { putBoolean("audio_focus", on) }
        PlaybackService.applyLivePrefs()
    }
    fun updateEqPreset(i: Int) { eqPreset = i; edit { putInt("eq_preset", i) }; PlaybackService.applyLivePrefs() }
    fun updateBassBoost(v: Int) { bassBoost = v.coerceIn(0, 1000); edit { putInt("bass_boost", bassBoost) }; PlaybackService.applyLivePrefs() }
}
