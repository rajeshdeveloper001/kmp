package com.vplayed.shared.language

import com.vplayed.shared.core.interop.Closeable
import com.vplayed.shared.core.interop.FlowWatcher

/*
 * Swift observation of the language list. Values arrive on the main thread, starting with the
 * current list; the Swift adapter closes the returned Closeable when it goes away.
 */

fun SupportedLanguagesStore.watchLanguages(onEach: (List<Language>) -> Unit): Closeable = FlowWatcher(languages).watch(onEach)
