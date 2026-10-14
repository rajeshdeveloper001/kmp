package com.vplayed.shared.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * The VPlayed API is inconsistent about scalar types: `video_duration` is "267.72" or 125, counts
 * are numbers or numeric strings, flags are true/false, 0/1 or "1", titles are a string or a
 * `{"en": "…"}` map. These helpers read such values and treat anything malformed as absent, so one
 * odd field never fails a whole response. They combine AndroidJP's former `HomeMapper` helpers
 * and VPlayed's former `LenientDecoding.swift`.
 */

/** Trimmed text of a JSON string or number; null for blanks, objects, arrays and `null`. */
fun JsonElement?.lenientString(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.trim().takeIf { it.isNotEmpty() }
}

/** A finite number from a JSON number or numeric string. */
fun JsonElement?.lenientDouble(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** A whole number from a JSON number or numeric string; fractions are truncated (`"12.9"` → 12). */
fun JsonElement?.lenientLong(): Long? = lenientDouble()?.toLong()

fun JsonElement?.lenientInt(): Int? = lenientLong()?.let { value ->
    value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
}

/** `true`/`false`, `1`/`0` or their string forms; anything else is null. */
fun JsonElement?.lenientBool(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return when (primitive.content.trim().lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}

/**
 * Text that is either a plain string or a translation map such as `{"en": "Action", "ar": "…"}`.
 * A map yields [language] when present, then English, then the first non-blank value by key
 * (VPlayed's `LocalizedText` rule; the language is passed in because only the app knows it).
 */
fun JsonElement?.localizedText(language: String? = null): String? = when (this) {
    is JsonPrimitive -> if (isString) lenientString() else null
    is JsonObject -> {
        val preferred = language?.let { this[it].lenientString() }
        preferred
            ?: this["en"].lenientString()
            ?: entries.sortedBy { it.key }.firstNotNullOfOrNull { it.value.lenientString() }
    }
    else -> null
}

/**
 * Decodes a JSON array, dropping elements that fail to decode instead of failing the whole list
 * (VPlayed's `LossyArray`). Anything that is not an array decodes as an empty list.
 *
 * Use on a property: `@Serializable(with = LossyListSerializer::class) val data: List<ItemDto> = emptyList()`.
 */
class LossyListSerializer<T>(private val elementSerializer: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val array = jsonDecoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            try {
                jsonDecoder.json.decodeFromJsonElement(elementSerializer, element)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                // kotlinx.serialization reports some shape mismatches as IllegalArgumentException.
                null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)
}
