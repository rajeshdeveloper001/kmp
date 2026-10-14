package com.vplayed.shared.geo

import com.vplayed.shared.core.serialization.SharedJson
import com.vplayed.shared.core.serialization.lenientString
import kotlin.io.encoding.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

/**
 * Reads the location from a geo token (Base64 of `{"currency": "…", "countryCode": "…", …}`).
 * Any invalid input yields null instead of an exception, so a bad token can never crash the app.
 */
object GeoTokenDecoder {
    // Same as android.util.Base64.DEFAULT used by the previous Android app: standard alphabet, padding optional.
    private val base64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    fun decode(token: String): GeoInfo? {
        val compact = token.filterNot(Char::isWhitespace)
        if (compact.isEmpty()) return null
        val fields = try {
            SharedJson.parseToJsonElement(base64.decode(compact).decodeToString()) as? JsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            // Not valid Base64.
            null
        } ?: return null
        return GeoInfo(
            country = fields["country"].lenientString(),
            countryCode = fields["countryCode"].lenientString(),
            currency = fields["currency"].lenientString(),
            region = fields["region"].lenientString(),
            regionName = fields["regionName"].lenientString(),
            city = fields["city"].lenientString(),
            timezone = fields["timezone"].lenientString(),
        )
    }

    /** The token's currency, or null (AndroidJP `GeoTokenDecoder.decodeCurrency`). */
    fun decodeCurrency(token: String): String? = decode(token)?.currency
}
