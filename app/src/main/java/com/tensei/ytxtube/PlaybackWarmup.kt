package com.tensei.ytxtube

import android.util.Log
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager

/**
 * Gets the slow one-time parts of playing a video done while the app opens, instead of when the
 * first video is tapped: YouTube's anti-bot token generator (a hidden web page) for this session,
 * and YouTube's player code with its signature and speed-limit functions.
 */
object PlaybackWarmup {
    private const val TAG = "PlaybackWarmup"
    private const val WARM_ID = "jNQXAC9IVRw"

    fun start(poTokens: YtxPoTokenProvider) {
        Thread({
            val t = android.os.SystemClock.elapsedRealtime()
            poTokens.warmUp()
            Log.i(TAG, "token generator ready in ${android.os.SystemClock.elapsedRealtime() - t} ms")
        }, "warmup-potoken").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }.start()

        Thread({
            val t = android.os.SystemClock.elapsedRealtime()
            runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(WARM_ID) }
            runCatching {
                YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                    WARM_ID,
                    "https://rr1---sn-warmup.googlevideo.com/videoplayback?expire=1&c=WEB&n=Qx7bLm2Pa9TzKcVw",
                )
            }
            runCatching { YoutubeJavaScriptPlayerManager.deobfuscateSignature(WARM_ID, "AOq0QJ8wRgIhAKz3s1Qw9mWarmupSignature0123456789abcdefABCDEF") }
            Log.i(TAG, "player code ready in ${android.os.SystemClock.elapsedRealtime() - t} ms")
        }, "warmup-player").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }.start()
    }
}
