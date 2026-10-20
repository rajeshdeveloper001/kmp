package com.vplayed.shared.language

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The languages the user can pick (in memory, like [com.vplayed.shared.settings.CommonSettingsStore]).
 * Starts with [Language.FALLBACK]; only [LanguageRepository] replaces it.
 */
class SupportedLanguagesStore {
    private val mutableLanguages = MutableStateFlow(Language.FALLBACK)

    val languages: StateFlow<List<Language>> = mutableLanguages.asStateFlow()

    /** The current list, typed for Swift. */
    val current: List<Language> get() = mutableLanguages.value

    /** The backend's default (`is_default`), else English, else the first language. */
    val default: Language
        get() = current.firstOrNull { it.isDefault }
            ?: current.firstOrNull { it.code == Language.DEFAULT_CODE }
            ?: current.firstOrNull()
            ?: Language.FALLBACK.first()

    /** The language with [code] (any locale tag), or null when it is not offered. */
    fun find(code: String): Language? {
        val wanted = Language.languageCodeOf(code)
        return current.firstOrNull { it.code == wanted }
    }

    /**
     * The language to use for [preferred] locale tags, most preferred first (a saved choice, then the
     * system's languages): the first one offered, else [default].
     */
    fun resolve(preferred: List<String>): Language = preferred.firstNotNullOfOrNull(::find) ?: default

    internal fun update(languages: List<Language>) {
        mutableLanguages.value = languages
    }
}
