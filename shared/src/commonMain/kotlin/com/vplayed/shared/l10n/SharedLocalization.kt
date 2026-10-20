package com.vplayed.shared.l10n

import com.vplayed.shared.language.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * The app language for text read from the shared string catalog (`composeResources/values*`).
 *
 * The catalog is the one source of user-facing text for AndroidJP and VPlayed. A language without a
 * `values-xx` file (or a key missing from one) falls back to `values/` (English).
 *
 * Catalog lookups follow the platform's app language, which each app sets when the user picks one:
 * AndroidJP through the per-app locale (`Locale.getDefault()`), VPlayed through its `AppleLanguages`
 * setting (`NSLocale.preferredLanguages`). The app then reports the choice with [setLanguage], so
 * shared state and observers ([languageCode]) see it too. Compose code on Android reads the catalog
 * with `stringResource(Res.string.…)`, which follows the activity's locale.
 */
object SharedLocalization {
    private val mutableLanguageCode = MutableStateFlow(Language.DEFAULT_CODE)

    /** The language code currently shown ("en", "ar", "te"…). */
    val languageCode: StateFlow<String> = mutableLanguageCode.asStateFlow()

    /** Records the language the app now shows; [code] may be any locale tag ("pt-BR" selects "pt"). */
    fun setLanguage(code: String) {
        mutableLanguageCode.value = Language.languageCodeOf(code).ifEmpty { Language.DEFAULT_CODE }
    }

    /** The string with [key] (its name in `strings.xml`), or null when the catalog has none. */
    @OptIn(ExperimentalResourceApi::class)
    fun stringResource(key: String): StringResource? = Res.allStringResources[key]

    /** The plural string with [key], or null when the catalog has none. */
    @OptIn(ExperimentalResourceApi::class)
    fun pluralResource(key: String): PluralStringResource? = Res.allPluralStringResources[key]

    /** [resource] in the app language, with `%1$s`… replaced by [args]. */
    suspend fun string(resource: StringResource, args: List<Any> = emptyList()): String =
        getString(resource, *args.toTypedArray())

    /** The plural form of [resource] for [quantity] in the app language, with `%1$s`… replaced by [args]. */
    suspend fun plural(resource: PluralStringResource, quantity: Int, args: List<Any> = emptyList()): String =
        getPluralString(resource, quantity, *args.toTypedArray())
}
