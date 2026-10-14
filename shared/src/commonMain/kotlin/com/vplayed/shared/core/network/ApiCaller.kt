package com.vplayed.shared.core.network

import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.error.resultOf
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
 * Paths are relative to `SharedConfig.serverUrl`, without a leading slash, e.g. `"mediaview/api/v1/homebanners"`.
 */
class ApiCaller(private val client: HttpClient, private val json: Json) {

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
        val response = client.request(path) {
            this.method = method
            requestOptions(options)
            configure()
        }
        VPlayedResponse(httpStatus = response.status.value, envelope = parseEnvelope(response.bodyAsText()))
    }

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
}
