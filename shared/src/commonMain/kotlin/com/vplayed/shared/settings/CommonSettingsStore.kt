package com.vplayed.shared.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last common settings the app loaded (in memory, like [com.vplayed.shared.geo.GeoStore]).
 * Only [CommonSettingsRepository] writes; shared rules that depend on settings (the automatic
 * guest session) read it.
 */
class CommonSettingsStore {
    private val mutableSettings = MutableStateFlow<CommonSettings?>(null)

    /** Null until the first successful load. */
    val settings: StateFlow<CommonSettings?> = mutableSettings.asStateFlow()

    /** The current value, typed for Swift. */
    val current: CommonSettings? get() = mutableSettings.value

    internal fun update(value: CommonSettings) {
        mutableSettings.value = value
    }
}
