package com.vplayed.shared.core.network

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.error.HTTP_UNAUTHORIZED
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * The envelope every VPlayed API answers with (AndroidJP `ApiEnvelope` / `UserResponseDto`,
 * VPlayed `APIEnvelope`).
 */
@Serializable
data class ApiEnvelope<T>(
    val error: Boolean? = null,
    val statusCode: Int? = null,
    val status: String? = null,
    val message: String? = null,
    val response: T? = null,
)

/**
 * A backend answer before interpretation. Status codes carry meaning on this backend (200 / 202 /
 * 203 on auth), and the apps disagree on whether the HTTP status or the envelope's `statusCode`
 * decides (divergence D1), so both are kept and repositories decide per endpoint.
 */
class VPlayedResponse(
    val httpStatus: Int,
    /** Null when the body is not a JSON envelope (empty, HTML error page, malformed). */
    val envelope: ApiEnvelope<JsonElement>?,
) {
    val isHttpSuccess: Boolean get() = httpStatus in HTTP_SUCCESS

    /** The envelope's `statusCode` when present, else the HTTP status (the UIKit app's rule, kept by VPlayed). */
    val effectiveStatusCode: Int get() = envelope?.statusCode ?: httpStatus

    /** The status that decides the outcome (D1): the HTTP status when it is not 2xx, else [effectiveStatusCode]. */
    val outcomeStatus: Int get() = if (isHttpSuccess) effectiveStatusCode else httpStatus

    /** The server's message, trimmed; null when absent or blank. */
    val message: String? get() = envelope?.message?.trim()?.takeIf { it.isNotEmpty() }

    /** True when the envelope explicitly reports `"error": true`. */
    val reportsError: Boolean get() = envelope?.error == true

    /**
     * Decodes `response` with [deserializer]; null when it is missing, `null`, or has another shape
     * (the auth endpoints return a user object on success and anything on errors).
     */
    fun <T> decodeResponse(json: Json, deserializer: DeserializationStrategy<T>): T? {
        val element = envelope?.response ?: return null
        if (element is JsonNull) return null
        return try {
            json.decodeFromJsonElement(deserializer, element)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    // The body may hold tokens or personal data: never print it.
    override fun toString(): String = "VPlayedResponse(httpStatus=$httpStatus, statusCode=${envelope?.statusCode}, hasBody=${envelope != null})"

    private companion object {
        val HTTP_SUCCESS = 200..299
    }
}

/**
 * The read rule for data endpoints (Home, settings): decision D1 — a non-2xx HTTP status fails —
 * and decision D6 — within 2xx the envelope's `statusCode`, when present, must be 200 (`status`
 * is advisory). Returns the `response` object, or the failure: [AppError.Unauthorized] for 401,
 * [AppError.Server] with the server's message for other refusals, [AppError.Parsing] when the body
 * is not an envelope with an object `response`.
 */
fun VPlayedResponse.requireResponse(): DataResult<JsonObject> {
    val status = outcomeStatus
    return when {
        status == HTTP_UNAUTHORIZED -> DataResult.Failure(AppError.Unauthorized)
        status != HTTP_OK -> DataResult.Failure(AppError.Server(status, message))
        else -> (envelope?.response as? JsonObject)?.let { DataResult.Success(it) } ?: DataResult.Failure(AppError.Parsing)
    }
}

private const val HTTP_OK = 200
