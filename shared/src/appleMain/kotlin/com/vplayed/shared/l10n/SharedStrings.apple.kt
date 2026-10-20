package com.vplayed.shared.l10n

import com.vplayed.shared.core.interop.Closeable
import com.vplayed.shared.core.interop.FlowWatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Synchronous catalog lookups for SwiftUI, which builds text inside `body` and cannot await.
 *
 * The catalog files ship in the app bundle (`compose-resources/`). Reading a string blocks only on
 * Compose Resources' own cache, which loads on a background dispatcher and keeps every file part it
 * has read, so repeated lookups are memory reads. A key the catalog does not have comes back as the
 * key itself, so a mistake shows on screen instead of crashing.
 */
object SharedStrings {
    /** The language lookups use; see [SharedLocalization.setLanguage]. */
    val languageCode: String get() = SharedLocalization.languageCode.value

    fun setLanguage(code: String) = SharedLocalization.setLanguage(code)

    /** Values arrive on the main thread, starting with the current language. */
    fun watchLanguage(onEach: (String) -> Unit): Closeable = FlowWatcher(SharedLocalization.languageCode).watch(onEach)

    fun hasKey(key: String): Boolean =
        SharedLocalization.stringResource(key) != null || SharedLocalization.pluralResource(key) != null

    /** The string named [key], with `%1$s`… replaced by [args]. */
    fun string(key: String, args: List<String> = emptyList()): String {
        val resource = SharedLocalization.stringResource(key) ?: return key
        return resource(resource, args)
    }

    /** The plural string named [key] for [quantity], with `%1$s`… replaced by [args]. */
    fun plural(key: String, quantity: Int, args: List<String> = emptyList()): String {
        val resource = SharedLocalization.pluralResource(key) ?: return key
        return plural(resource, quantity, args)
    }

    /** [resource] (from the shared message mappings, e.g. `AuthMessageKey.resource`), formatted with [args]. */
    fun resource(resource: StringResource, args: List<String> = emptyList()): String =
        runBlocking { SharedLocalization.string(resource, args) }

    fun plural(resource: PluralStringResource, quantity: Int, args: List<String> = emptyList()): String =
        runBlocking { SharedLocalization.plural(resource, quantity, args) }
}
