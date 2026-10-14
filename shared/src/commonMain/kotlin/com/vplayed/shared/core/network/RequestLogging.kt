package com.vplayed.shared.core.network

import com.vplayed.shared.core.platform.NoOpLogger
import com.vplayed.shared.core.platform.SharedLogger
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.util.AttributeKey
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class RequestLoggingConfig {
    var logger: SharedLogger = NoOpLogger
}

private val StartMarkKey = AttributeKey<TimeMark>("VPlayedRequestStart")
private const val TAG = "API"

/**
 * One line per request: method, path, status and duration. Bodies and headers are never logged,
 * so tokens, codes, emails and the geo token cannot leak; [RequestOptions.redact] requests also
 * hide their path (e.g. the public IP in `common/geo-data/{ip}`). Install only in debuggable builds
 * (`SharedConfig.loggingEnabled`).
 */
val RequestLogging = createClientPlugin("VPlayedRequestLogging", ::RequestLoggingConfig) {
    val logger = pluginConfig.logger

    onRequest { request, _ ->
        request.attributes.put(StartMarkKey, TimeSource.Monotonic.markNow())
    }

    onResponse { response ->
        val request = response.call.request
        val options = request.attributes.getOrNull(RequestOptionsKey) ?: RequestOptions.Default
        val target = if (options.redact) "<redacted>" else request.url.encodedPath
        val elapsed = request.attributes.getOrNull(StartMarkKey)?.elapsedNow()?.inWholeMilliseconds
        logger.debug(TAG, "${request.method.value} $target -> ${response.status.value}${elapsed?.let { " (${it} ms)" }.orEmpty()}")
    }
}
