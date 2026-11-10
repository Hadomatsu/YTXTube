package com.tensei.ytxtube

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gets Shorts ready before they are opened, and keeps your place when you leave the Shorts tab.
 *
 * A few seconds after the app starts (only for people who watch Shorts), the first page of the
 * Shorts feed is fetched and the first Shorts are resolved, so opening the tab starts playing
 * straight away instead of waiting for the feed and then for the video. Leaving the tab keeps the
 * feed and the Short you were on, so coming back is instant too.
 */
internal object ShortsWarmup {
    private const val WARM_MS = 15 * 60_000L
    private const val KEEP_MS = 30 * 60_000L
    private const val USED_WINDOW_MS = 21L * 24 * 60 * 60_000L

    private class Warm(val page: FeedPage, val account: String?, val at: Long)

    class Kept(
        val videos: List<VideoItem>,
        val next: String?,
        val seen: Set<String>,
        val page: Int,
        val account: String?,
        val at: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var warm: Warm? = null
    @Volatile private var kept: Kept? = null
    @Volatile private var fetching = false
    private var job: Job? = null

    private fun account(): String? = Account.activeAccountId ?: Account.profile?.name

    private fun now() = System.currentTimeMillis()

    /** Remembers that Shorts are used, so the app only warms them up for people who watch them. */
    fun noteUsed(context: Context) {
        runCatching {
            context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
                .edit().putLong("shorts_last_used", now()).apply()
        }
    }

    private fun usedRecently(context: Context): Boolean = runCatching {
        val at = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getLong("shorts_last_used", 0L)
        at > 0L && now() - at < USED_WINDOW_MS
    }.getOrDefault(false)

    /** Fetches the first page of the feed and the first Shorts in the background. */
    @Synchronized
    fun warm(context: Context, delayMs: Long = 0L) {
        if (!usedRecently(context)) return
        val w = warm
        if (w != null && w.account == account() && now() - w.at < WARM_MS) return
        if (keptFresh() != null) return
        if (job?.isActive == true) return
        job = scope.launch {
            if (delayMs > 0) delay(delayMs)
            fetching = true
            try {
                val who = account()
                val page = runCatching { InnerTube.shorts(null, emptySet()) }.getOrNull()
                if (page != null && page.videos.isNotEmpty()) {
                    warm = Warm(page, who, now())
                    page.videos.take(2).forEach { StreamCache.prefetch(it.id) }
                }
            } finally {
                fetching = false
            }
        }
    }

    /**
     * The warmed first page, used once. If the warm-up is still fetching, waits for it rather
     * than sending the same request twice; if it has not started yet, it is dropped.
     */
    suspend fun takeFirstPage(): FeedPage? {
        val running = job
        if (running != null && running.isActive) {
            if (fetching) running.join() else running.cancel()
        }
        val w = warm ?: return null
        warm = null
        return w.page.takeIf { w.account == account() && now() - w.at < WARM_MS }
    }

    /** Keeps the feed and the Short on screen for the next time the tab is opened. */
    fun keep(videos: List<VideoItem>, next: String?, seen: Set<String>, page: Int) {
        if (videos.isEmpty()) return
        kept = Kept(videos, next, seen.toSet(), page.coerceIn(0, videos.lastIndex), account(), now())
    }

    private fun keptFresh(): Kept? = kept?.takeIf { it.account == account() && now() - it.at < KEEP_MS }

    /** The kept feed, used once. */
    fun restore(): Kept? {
        val k = keptFresh()
        kept = null
        return k
    }

    fun clear() {
        warm = null
        kept = null
    }
}
