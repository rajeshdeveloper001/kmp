package com.vplayed.shared.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient

actual fun createPlatformHttpEngine(): HttpClientEngine = OkHttp.create()

/**
 * An engine on top of the app's own [OkHttpClient], so shared requests reuse its connection pool
 * and dispatcher (Coil shares them too). Pass a client without the app's VPlayed interceptors:
 * the shared header plugin adds the headers.
 */
fun createOkHttpEngine(client: OkHttpClient): HttpClientEngine = OkHttp.create { preconfigured = client }
