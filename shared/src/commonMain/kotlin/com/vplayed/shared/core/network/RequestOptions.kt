package com.vplayed.shared.core.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey

/**
 * Per-request switches, the shared equivalent of AndroidJP's `@WithoutGeoToken` / `@RedactBody`
 * Retrofit annotations.
 */
data class RequestOptions(
    /** False for the request that fetches the geo token itself: it must not carry the old one. */
    val sendGeoToken: Boolean = true,
    /** True when the request or response holds credentials, codes or personal data: only method and status are logged. */
    val redact: Boolean = false,
) {
    companion object {
        val Default = RequestOptions()

        /** Auth endpoints: bodies hold codes, tokens and emails. */
        val Sensitive = RequestOptions(redact = true)
    }
}

internal val RequestOptionsKey = AttributeKey<RequestOptions>("VPlayedRequestOptions")

internal fun HttpRequestBuilder.requestOptions(options: RequestOptions) {
    attributes.put(RequestOptionsKey, options)
}
