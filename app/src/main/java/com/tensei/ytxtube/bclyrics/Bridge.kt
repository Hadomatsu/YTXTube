package com.tensei.ytxtube.bclyrics

import com.tensei.ytxtube.LyricsDoc

/**
 * The app's way into BitChord's lyrics chain: every enabled service is asked at once and the answers
 * are taken in priority order, Apple Music word timing first (lrc.red, BiniLyrics, BetterLyrics,
 * PaxSenix, LyricsPlus), then Unison, KuGou, LRCLIB, Musixmatch, and Genius as plain text.
 */
object BitChordLyrics {

    /** Services that answer without an account or key. The YouTube ones are handled by the app itself. */
    private val sources: Set<LyricsSource> = LyricsSource.offered.toSet() - setOf(
        LyricsSource.PAXSENIX_SPOTIFY,
        LyricsSource.PAXSENIX_MUSIXMATCH,
        LyricsSource.YOUTUBE_TRANSCRIPT,
        LyricsSource.YOUTUBE_MUSIC,
    )

    suspend fun lookup(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): LyricsDoc? {
        val result = runCatching {
            LyricsRepository.lyrics(
                videoId = videoId,
                title = title,
                artist = artist,
                durationMs = durationMs,
                album = album,
                sources = sources,
                order = LyricsSource.offered,
                prioritizeSyllableSync = true,
            )
        }.getOrNull() ?: return null
        val lines = result.lines
        if (lines.none { it.text.isNotBlank() }) return null
        // A source with no timing at all (Genius, plain text) stamps every line 0; show it unsynced.
        val timed = lines.any { it.timeMs > 0 || it.words.isNotEmpty() }
        val mapped = lines.map { line ->
            com.tensei.ytxtube.LyricLine(
                timeMs = if (timed) line.timeMs else -1L,
                text = line.text,
                words = line.words.map { com.tensei.ytxtube.LyricWord(it.startMs, it.endMs, it.text) },
                endMs = line.sungUntilMs ?: line.words.lastOrNull()?.endMs ?: -1L,
                background = line.background?.text?.takeIf { it.isNotBlank() },
            )
        }.let { list -> if (timed) list else list.filter { it.text.isNotBlank() } }
        return LyricsDoc(mapped, source = result.source.label)
    }
}
