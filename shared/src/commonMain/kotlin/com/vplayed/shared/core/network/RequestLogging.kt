package com.vplayed.shared.core.network

import com.vplayed.shared.core.platform.NoOpLogger
import com.vplayed.shared.core.platform.SharedLogger
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.TextContent
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class RequestLoggingConfig {
    var logger: SharedLogger = NoOpLogger
    var redactSensitive: Boolean = false
}

private val StartMarkKey = AttributeKey<TimeMark>("VPlayedRequestStart")
private const val TAG = "API"

private val PrettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

internal fun formatJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return try {
        val element = Json.parseToJsonElement(trimmed)
        PrettyJson.encodeToString(JsonElement.serializer(), element)
    } catch (_: Throwable) {
        raw
    }
}

private const val LOG_CHUNK_SIZE = 2000

internal fun logInChunks(text: String, action: (String) -> Unit) {
    if (text.length <= LOG_CHUNK_SIZE) {
        action(text)
        return
    }
    var start = 0
    val len = text.length
    while (start < len) {
        val remaining = len - start
        if (remaining <= LOG_CHUNK_SIZE) {
            action(text.substring(start))
            break
        }
        val targetEnd = start + LOG_CHUNK_SIZE
        val newline = text.lastIndexOf('\n', targetEnd)
        val end = if (newline > start) newline + 1 else targetEnd
        action(text.substring(start, end))
        start = end
    }
}

/**
 * Formatted API request and response logging. In debuggable builds (`SharedConfig.loggingEnabled`),
 * requests and responses are logged with pretty-printed JSON. In production builds (`redactSensitive = true`),
 * sensitive requests ([RequestOptions.redact]) mask their target URL and bodies so credentials, codes,
 * tokens and personal data cannot leak. In dev/QA builds (`redactSensitive = false`), complete logs are printed.
 * Long payloads are split cleanly across line boundaries to prevent OS log truncation.
 */
val RequestLogging = createClientPlugin("VPlayedRequestLogging", ::RequestLoggingConfig) {
    val logger = pluginConfig.logger
    val shouldRedact = pluginConfig.redactSensitive

    onRequest { request, content ->
        request.attributes.put(StartMarkKey, TimeSource.Monotonic.markNow())
        val options = request.attributes.getOrNull(RequestOptionsKey) ?: RequestOptions.Default
        val isRedacted = shouldRedact && (options.redact || request.url.host.contains("ipify.org"))
        val target = if (isRedacted) "<redacted>" else request.url.toString()

        val sb = StringBuilder()
        sb.append("--> ${request.method.value} $target")
        if (!isRedacted) {
            val visibleHeaders = request.headers.entries()
                .filterNot { (name, _) -> shouldRedact && VPlayedHeaderNames.SENSITIVE.any { it.equals(name, ignoreCase = true) } }
            if (visibleHeaders.isNotEmpty()) {
                sb.append("\nHeaders:")
                for ((name, values) in visibleHeaders) {
                    sb.append("\n  $name: ${values.joinToString(", ")}")
                }
            }
            val bodyText = when (content) {
                is TextContent -> formatJson(content.text)
                is FormDataContent -> content.formData.entries().joinToString("&") { (k, v) -> "$k=${v.joinToString(",")}" }
                else -> null
            }
            if (!bodyText.isNullOrBlank()) {
                sb.append("\nBody:\n").append(bodyText)
            }
        }
        sb.append("\n--> END ${request.method.value}")
        logInChunks(sb.toString()) { logger.debug(TAG, it) }
    }

    onResponse { response ->
        val request = response.call.request
        val options = request.attributes.getOrNull(RequestOptionsKey) ?: RequestOptions.Default
        val isRedacted = shouldRedact && (options.redact || request.url.host.contains("ipify.org"))
        val target = if (isRedacted) "<redacted>" else request.url.toString()
        val elapsed = request.attributes.getOrNull(StartMarkKey)?.elapsedNow()?.inWholeMilliseconds
        val duration = elapsed?.let { " (${it} ms)" }.orEmpty()

        val sb = StringBuilder()
        sb.append("<-- ${response.status.value} ${response.status.description} ${request.method.value} $target$duration")
        if (isRedacted) {
            sb.append("\nBody: <redacted>")
        } else {
            val rawBody = try {
                response.bodyAsText()
            } catch (_: Throwable) {
                ""
            }
            if (rawBody.isNotBlank()) {
                sb.append("\nResponse Body:\n").append(formatJson(rawBody))
            }
        }
        sb.append("\n<-- END HTTP")
        logInChunks(sb.toString()) { logger.debug(TAG, it) }
    }
}

