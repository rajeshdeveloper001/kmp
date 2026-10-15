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

/**
 * Formatted API request and response logging. In debuggable builds (`SharedConfig.loggingEnabled`),
 * requests and responses are logged with pretty-printed JSON. Sensitive requests ([RequestOptions.redact])
 * mask their target URL and bodies so credentials, codes, tokens and personal data cannot leak.
 */
val RequestLogging = createClientPlugin("VPlayedRequestLogging", ::RequestLoggingConfig) {
    val logger = pluginConfig.logger

    onRequest { request, content ->
        request.attributes.put(StartMarkKey, TimeSource.Monotonic.markNow())
        val options = request.attributes.getOrNull(RequestOptionsKey) ?: RequestOptions.Default
        val isRedacted = options.redact || request.url.host.contains("ipify.org")
        val target = if (isRedacted) "<redacted>" else request.url.toString()

        val sb = StringBuilder()
        sb.append("--> ${request.method.value} $target")
        if (!isRedacted) {
            val visibleHeaders = request.headers.entries()
                .filterNot { (name, _) -> VPlayedHeaderNames.SENSITIVE.any { it.equals(name, ignoreCase = true) } }
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
        logger.debug(TAG, sb.toString())
    }

    onResponse { response ->
        val request = response.call.request
        val options = request.attributes.getOrNull(RequestOptionsKey) ?: RequestOptions.Default
        val isRedacted = options.redact || request.url.host.contains("ipify.org")
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
        logger.debug(TAG, sb.toString())
    }
}

