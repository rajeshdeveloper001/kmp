package com.vplayed.shared.language

import com.vplayed.shared.core.serialization.lenientBool
import com.vplayed.shared.core.serialization.lenientString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A language the user can pick, from `GET mediaview/api/v1/language`. Its [code] selects both the
 * app's text (the shared string catalog; a language without its own `values-xx` file shows English)
 * and the server's content (`X-Language-Code`).
 */
data class Language(
    val id: String,
    /** ISO 639-1 code, lower case ("en", "ar", "ta"). */
    val code: String,
    /** The backend's name for the language, in English ("Tamil"); apps show the native name when the platform knows it. */
    val name: String,
    val isDefault: Boolean,
) {
    /** Whether text in this language runs right to left; apps still honour the common API's `rtl` switch. */
    val isRightToLeft: Boolean get() = code in RIGHT_TO_LEFT_CODES

    companion object {
        /** English: the catalog's default language and the last fallback. */
        const val DEFAULT_CODE = "en"

        private val RIGHT_TO_LEFT_CODES = setOf("ar", "he", "fa", "ur")

        /**
         * Used until the language list loads, and when it cannot: the languages the app has
         * translations for.
         */
        val FALLBACK: List<Language> = listOf(
            Language(id = "en", code = DEFAULT_CODE, name = "English", isDefault = true),
            Language(id = "ar", code = "ar", name = "Arabic", isDefault = false),
            Language(id = "pt", code = "pt", name = "Portuguese", isDefault = false),
            Language(id = "ta", code = "ta", name = "Tamil", isDefault = false),
        )

        /** Reads one entry of the list; null when it has no usable code. */
        fun from(element: JsonElement): Language? {
            val item = element as? JsonObject ?: return null
            val code = item["code"].lenientString()?.lowercase() ?: return null
            return Language(
                id = item["_id"].lenientString() ?: code,
                code = code,
                name = item["name"].lenientString() ?: code,
                isDefault = item["is_default"].lenientBool() ?: false,
            )
        }

        /** The language part of a locale tag ("pt-BR" → "pt", "zh_Hant" → "zh"). */
        fun languageCodeOf(tag: String): String = tag.substringBefore('-').substringBefore('_').lowercase()
    }
}
