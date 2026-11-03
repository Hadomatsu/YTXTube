package com.tensei.ytxtube.bclyrics

import okhttp3.OkHttpClient

/** The app's shared HTTP client, under the name the lyrics providers (ported from BitChord) expect. */
object Http {
    val client: OkHttpClient get() = com.tensei.ytxtube.Net.client
}
