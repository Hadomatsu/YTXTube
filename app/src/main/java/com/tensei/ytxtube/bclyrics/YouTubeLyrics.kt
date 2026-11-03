package com.tensei.ytxtube.bclyrics

/*
 * BitChord asks YouTube itself for lyrics through its own InnerTube client. This app already does
 * that after the lyrics services (see InnerTube.musicLyrics), so these two sources answer nothing here.
 */

object YouTubeMusicLyrics {
    @Suppress("UNUSED_PARAMETER")
    suspend fun lyrics(videoId: String): List<LyricLine>? = null
}

object YouTubeTranscriptLyrics {
    @Suppress("UNUSED_PARAMETER")
    suspend fun lyrics(videoId: String): List<LyricLine>? = null
}
