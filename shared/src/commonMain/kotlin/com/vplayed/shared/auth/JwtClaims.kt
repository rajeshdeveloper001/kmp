package com.vplayed.shared.auth

import com.vplayed.shared.core.serialization.SharedJson
import com.vplayed.shared.core.serialization.lenientString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Reads claims of a JWT payload without verifying it (the backend verifies tokens). Used for what the
 * sign-in SDKs leave out: Apple's `email` when the credential has none, Google's `sub` on Android.
 */
object JwtClaims {
    /** The [claim] of [token] as text; null when the token or claim is missing or unreadable. Never throws. */
    @OptIn(ExperimentalEncodingApi::class)
    fun string(token: String, claim: String): String? = runCatching {
        val payload = token.split('.').getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload)
        val element = SharedJson.decodeFromString(JsonElement.serializer(), bytes.decodeToString())
        (element as? JsonObject)?.get(claim).lenientString()
    }.getOrNull()
}
