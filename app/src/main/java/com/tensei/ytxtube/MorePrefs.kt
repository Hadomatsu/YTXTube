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
    WORD_SYNCED("Word by word first", "Apple Music style timing from BetterLyrics when it has the song, then line-synced lyrics"),
    LINE_SYNCED("Line synced only", "LRCLIB line timing, then YouTube Music's own lyrics"),
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
}
