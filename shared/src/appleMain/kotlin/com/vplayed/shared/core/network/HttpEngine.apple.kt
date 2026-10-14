package com.vplayed.shared.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLSessionConfiguration

actual fun createPlatformHttpEngine(): HttpClientEngine = Darwin.create()

/**
 * An NSURLSession engine whose session configuration the app adjusts, e.g. VPlayed's URL cache and
 * per-host connection limit (`APIClient.makeSession`), or a `URLProtocol` stub in tests.
 * Timeouts are set by the shared client (`SharedConfig`), not here.
 */
fun createDarwinEngine(configure: (NSURLSessionConfiguration) -> Unit): HttpClientEngine = Darwin.create {
    configureSession { configure(this) }
}
