package com.tensei.ytxtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit


object Net {

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    val client: OkHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 24
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(32, 5, TimeUnit.MINUTES))
            .build()
    }


    @Volatile private var mediaHost: String? = null

    fun noteMediaHost(url: String?) {
        url?.toHttpUrlOrNull()?.host?.let { mediaHost = it }
    }

    




    fun prewarmMediaConnection() {
        ping(mediaHost ?: return)
    }

    private fun ping(host: String) {
        val request = okhttp3.Request.Builder().url("https://$host/generate_204").head().header("User-Agent", USER_AGENT).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) = response.close()
        })
    }

    private val mediaHostIn = Regex("""https://(rr\d+---[a-z0-9-]+\.googlevideo\.com)/""")
    private val warmedHosts = LinkedHashSet<String>()

    /**
     * Connects to the video server a player answer points at while the extractor is still asking
     * YouTube about the video, so the first video and audio requests skip the connection
     * handshakes (100 to 200 ms). Two connections: video and audio load side by side.
     */
    fun warmMediaHostIn(text: String?) {
        val host = text?.let { mediaHostIn.find(it)?.groupValues?.get(1) } ?: return
        synchronized(warmedHosts) {
            if (!warmedHosts.add(host)) return
            if (warmedHosts.size > 16) warmedHosts.remove(warmedHosts.first())
        }
        repeat(2) { ping(host) }
    }
}

/**
 * Answers fetched before the extractor asks for them. Opening a video, the extractor asks YouTube
 * one question after another, and its last one ("next": title, description, related videos) is
 * also the slowest, 0.6 to 1.3 s. [start] sends the identical request when the extraction begins,
 * and [NewPipeDownloader] hands the extractor that answer instead of asking again.
 */
object Ahead {
    private const val MAX_AGE_MS = 30_000L
    // Not a fixed pool: a request sent after the extractor's own one would only be wasted.
    private val pool = java.util.concurrent.Executors.newCachedThreadPool { r ->
        Thread(r, "ahead").apply { isDaemon = true }
    }
    private val sending = ThreadLocal<Boolean>()
    private val answers = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, java.util.concurrent.FutureTask<Response>>>()

    fun start(videoId: String) {
        pool.execute {
            sending.set(true)
            try {
                val service = org.schabi.newpipe.extractor.ServiceList.YouTube
                // Built exactly as YoutubeStreamExtractor builds its "next" request.
                val body = com.grack.nanojson.JsonWriter.string(
                    org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
                        .prepareDesktopJsonBuilder(service.localization, service.contentCountry)
                        .value("videoId", videoId)
                        .value("contentCheckOk", true)
                        .value("racyCheckOk", true)
                        .done(),
                ).toByteArray(Charsets.UTF_8)
                org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
                    .getJsonPostResponse("next", body, service.localization)
            } catch (_: Throwable) {
            } finally {
                sending.set(false)
            }
        }
        // Signed in, YouTube refuses the extractor's web player request for every video and
        // PlayerFallback asks again signed in; that second request now runs alongside.
        if (Account.signedIn) pool.execute { PlayerFallback.warm(videoId) }
    }

    /** The answer for [key]: sent ahead (stored for the extractor), taken, or null. */
    internal fun answer(key: String, send: () -> Response): Response? {
        if (sending.get() == true) {
            val task = java.util.concurrent.FutureTask(send)
            val now = System.currentTimeMillis()
            answers.entries.removeIf { now - it.value.first > MAX_AGE_MS }
            answers[key] = now to task
            task.run()
            return task.get()
        }
        val (at, task) = answers.remove(key) ?: return null
        if (System.currentTimeMillis() - at > MAX_AGE_MS) return null
        return runCatching { task.get() }.getOrNull()
    }
}


class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val langCode = runCatching { LanguageSettings.effectiveLanguageCode() }.getOrNull() ?: "en"
        val countryCode = runCatching { LanguageSettings.effectiveCountryCode() }.getOrNull() ?: "US"
        val isYouTube = request.url().contains("youtube.com") || request.url().contains("youtubei")

        var sendData = request.dataToSend()
        if (sendData != null && isYouTube && langCode.isNotEmpty()) {
            val raw = String(sendData, Charsets.UTF_8)
            if (raw.contains("\"context\"") || raw.contains("\"client\"")) {
                var modified = raw
                if (modified.contains("\"hl\":")) {
                    modified = modified.replace(Regex("\"hl\"\\s*:\\s*\"[^\"]*\""), "\"hl\":\"$langCode\"")
                } else if (modified.contains("\"client\":{")) {
                    modified = modified.replace("\"client\":{", "\"client\":{\"hl\":\"$langCode\",\"gl\":\"$countryCode\",")
                }
                if (modified.contains("\"gl\":")) {
                    modified = modified.replace(Regex("\"gl\"\\s*:\\s*\"[^\"]*\""), "\"gl\":\"$countryCode\"")
                }
                sendData = modified.toByteArray(Charsets.UTF_8)
            }
        }
        val body = sendData?.toRequestBody()

        var targetUrl = request.url()
        if (isYouTube && langCode.isNotEmpty()) {
            val httpUrl = targetUrl.toHttpUrlOrNull()
            if (httpUrl != null) {
                targetUrl = httpUrl.newBuilder()
                    .removeAllQueryParameters("hl")
                    .addQueryParameter("hl", langCode)
                    .removeAllQueryParameters("gl")
                    .addQueryParameter("gl", countryCode)
                    .build().toString()
            }
        }

        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(targetUrl)
            .addHeader("User-Agent", Net.USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        builder.removeHeader("Accept-Language")
        builder.addHeader("Accept-Language", "$langCode-$countryCode,$langCode;q=0.9,en-US;q=0.8,en;q=0.7")

        val existingCookie = request.headers()["Cookie"]?.firstOrNull() ?: ""
        val prefCookie = "PREF=hl=$langCode&gl=$countryCode"
        val mergedCookie = buildString {
            if (Account.signedIn) {
                val acctCookies = Account.cookieHeader()
                if (acctCookies.isNotEmpty()) append(acctCookies).append("; ")
            }
            if (existingCookie.isNotEmpty()) append(existingCookie).append("; ")
            if (!contains("PREF=")) append(prefCookie)
        }.trim().removeSuffix(";")

        if (mergedCookie.isNotEmpty()) {
            builder.removeHeader("Cookie")
            builder.addHeader("Cookie", mergedCookie)
        }

        if (isYouTube && Account.signedIn) {
            val auth = Account.authorization()
            if (auth != null && !request.headers().containsKey("Authorization")) {
                builder.addHeader("Authorization", auth)
            }
        }
        if (sendData != null && "/youtubei/v1/next" in targetUrl) {
            val key = targetUrl + "\n" + String(sendData, Charsets.UTF_8)
            Ahead.answer(key) { send(builder, request) }?.let { return it }
        }
        return send(builder, request)
    }

    private fun send(builder: okhttp3.Request.Builder, request: Request): Response {
        client.newCall(builder.build()).execute().use { response ->

            if (response.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            val text = response.body?.string()
            if (PlayerFallback.handles(request.url())) Net.warmMediaHostIn(text)
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                if (PlayerFallback.handles(request.url())) PlayerFallback.substitute(request.url(), request.dataToSend(), text) else text,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        val instance: NewPipeDownloader by lazy { NewPipeDownloader(Net.client) }
    }
}
