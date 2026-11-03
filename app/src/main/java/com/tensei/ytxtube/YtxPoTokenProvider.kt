package com.tensei.ytxtube

import android.util.Log
import com.tensei.ytxtube.potoken.PoTokenGenerator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

/**
 * Hands the extractor a proof-of-origin token for the WEB client.
 *
 * Two things matter for streams that keep playing:
 *  - The token is only valid for the web client. The extractor also asks for tokens for the
 *    embed, Android and iOS clients; a web token glued onto those streams makes YouTube cut them
 *    off after a short while ("YouTube refused the stream"), so those get none (like NewPipe).
 *  - The token is tied to a visitor id, and the id handed back to the extractor has to be a real
 *    one from YouTube. A signed-in account has one; a guest gets one fetched once and kept for the
 *    whole run (it used to be a made-up "guest_<time>" string that also changed on every video,
 *    which threw the token generator away each time).
 */
class YtxPoTokenProvider(private val generator: PoTokenGenerator) : PoTokenProvider {
    init {
        current = this
    }

    /**
     * Forget the cached token generator, and the guest visitor id when [newVisitor] is set. The
     * next video then gets a completely fresh token. Blocks; call it off the main thread.
     */
    fun reset(newVisitor: Boolean = true) {
        if (newVisitor) guestVisitor = null
        generator.reset()
    }

    private fun resolvePoToken(videoId: String?): PoTokenResult? {
        videoId ?: return null
        return try {
            val sessionId = Account.visitorData() ?: guestVisitorData()
            if (sessionId == null) {
                Log.w(TAG, "No visitor id for $videoId; continuing without a PoToken")
                return null
            }
            val res = generator.getWebClientPoToken(videoId, sessionId) ?: return null
            PoTokenResult(
                sessionId,
                res.playerRequestPoToken,
                res.streamingDataPoToken
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate PoToken for $videoId: ${e.message}")
            null
        }
    }

    override fun getWebClientPoToken(videoId: String?): PoTokenResult? = resolvePoToken(videoId)

    // A token minted for the web client is not valid for these clients (see the class comment).
    override fun getWebEmbedClientPoToken(videoId: String?): PoTokenResult? = null

    override fun getAndroidClientPoToken(videoId: String?): PoTokenResult? = null

    override fun getIosClientPoToken(videoId: String?): PoTokenResult? = null

    companion object {
        private const val TAG = "YtxPoTokenProvider"

        @Volatile
        var current: YtxPoTokenProvider? = null
            private set

        @Volatile
        private var guestVisitor: String? = null

        private fun guestVisitorData(): String? {
            guestVisitor?.let { return it }
            synchronized(this) {
                guestVisitor?.let { return it }
                val fetched = fetchVisitorData()
                if (fetched != null) guestVisitor = fetched
                return fetched
            }
        }

        /** Asks YouTube for a fresh visitor id, the same way its own web client does. */
        private fun fetchVisitorData(): String? = runCatching {
            val body = JSONObject().put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB")
                        .put("clientVersion", Account.clientVersion())
                        .put("hl", "en")
                        .put("gl", "US"),
                ),
            ).toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .header("User-Agent", Net.USER_AGENT)
                .header("Origin", "https://www.youtube.com")
                .post(body)
                .build()
            Net.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("responseContext")
                    ?.optString("visitorData")
                    ?.takeIf { it.isNotBlank() }
            }
        }.onFailure { Log.w(TAG, "Could not fetch a visitor id: ${it.message}") }.getOrNull()
    }
}
