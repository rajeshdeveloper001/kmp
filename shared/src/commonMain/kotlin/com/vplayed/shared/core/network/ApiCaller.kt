package com.vplayed.shared.core.network

import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.error.HTTP_UNAUTHORIZED
import com.vplayed.shared.core.error.resultOf
import com.vplayed.shared.session.AccessTokenRefresher
import com.vplayed.shared.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Performs VPlayed backend requests and returns the raw [VPlayedResponse] — or a typed failure for
 * transport problems (offline, timeout). Never throws except for coroutine cancellation.
 *
 * With [sessions], every request first waits for the stored session to be read. A 401 on a
 * request that carried the session's token (see [RequestOptions.endsSessionOnUnauthorized]) first
 * tries [refresher] once and, when a new access token was issued, sends the request again; a refused
 * refresh — or no [refresher] — ends that session (decision D21, revised: refresh once, then expire).
 * The final answer is returned to the caller.
 *
 * Paths are relative to `SharedConfig.serverUrl`, without a leading slash, e.g. `"mediaview/api/v1/homebanners"`.
 */
class ApiCaller(
    private val client: HttpClient,
    private val json: Json,
    private val sessions: SessionManager? = null,
    /** `auth/refresh-token`; null keeps the plain D21 rule (a 401 ends the session). */
    private val refresher: AccessTokenRefresher? = null,
) {

    suspend fun get(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        options: RequestOptions = RequestOptions.Default,
    ): DataResult<VPlayedResponse> = execute(HttpMethod.Get, path, options) {
        query.forEach { (name, value) -> parameter(name, value) }
    }

    /** POST with a JSON body (`Content-Type: application/json`). */
    suspend fun postJson(
        path: String,
        body: JsonElement,
        options: RequestOptions = RequestOptions.Default,
    ): DataResult<VPlayedResponse> = execute(HttpMethod.Post, path, options) {
        setBody(TextContent(json.encodeToString(JsonElement.serializer(), body), ContentType.Application.Json))
    }

    /** POST with a form-encoded body, as `verifyEmailLogin` and `logout-device` expect. */
    suspend fun postForm(
        path: String,
        fields: List<Pair<String, String>>,
        options: RequestOptions = RequestOptions.Default,
    ): DataResult<VPlayedResponse> = execute(HttpMethod.Post, path, options) {
        setBody(FormDataContent(Parameters.build { fields.forEach { (name, value) -> append(name, value) } }))
    }

    private suspend fun execute(
        method: HttpMethod,
        path: String,
        options: RequestOptions,
        configure: HttpRequestBuilder.() -> Unit,
    ): DataResult<VPlayedResponse> = resultOf {
        require(!path.startsWith("/") && !path.contains("://")) { "Use a path relative to the server URL: $path" }
        sessions?.awaitLoaded()
        val first = send(method, path, options, configure)
        val rejectedToken = first.rejectedSessionToken(options) ?: return@resultOf first.answer
        // `sessions` is non-null here: rejectedSessionToken() needs it.
        if (!sessions!!.recoverFromUnauthorized(rejectedToken, refresher)) return@resultOf first.answer
        // Sent once more with the new token (the header plugin reads it); a second 401 ends the session.
        val retry = send(method, path, options, configure)
        retry.rejectedSessionToken(options)?.let { sessions.expire(it) }
        retry.answer
    }

    /** One HTTP exchange: the answer and the bearer token the request carried. */
    private class Exchange(val answer: VPlayedResponse, val bearerToken: String?)

    private suspend fun send(
        method: HttpMethod,
        path: String,
        options: RequestOptions,
        configure: HttpRequestBuilder.() -> Unit,
    ): Exchange {
        val response = client.request(path) {
            this.method = method
            requestOptions(options)
            configure()
        }
        val rawBody = response.bodyAsText()
        return Exchange(
            answer = VPlayedResponse(httpStatus = response.status.value, envelope = parseEnvelope(rawBody), rawBody = rawBody),
            bearerToken = response.call.request.headers[HttpHeaders.Authorization]?.removePrefix(BEARER_PREFIX),
        )
    }

    /** The session token this 401 rejected, when the request carried one and 401s end sessions; else null. */
    private fun Exchange.rejectedSessionToken(options: RequestOptions): String? =
        bearerToken?.takeIf { sessions != null && options.endsSessionOnUnauthorized && answer.outcomeStatus == HTTP_UNAUTHORIZED }

    private fun parseEnvelope(body: String): ApiEnvelope<JsonElement>? {
        if (body.isBlank()) return null
        return try {
            json.decodeFromString(ApiEnvelope.serializer(JsonElement.serializer()), body)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            // Valid JSON of another shape (array, string): not an envelope.
            null
        }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
