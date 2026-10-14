package com.vplayed.shared.home

import com.vplayed.shared.core.interop.Closeable
import com.vplayed.shared.core.interop.FlowWatcher

/*
 * Swift observation of the Home feed. Values arrive on the main thread; the Swift adapter closes
 * each returned Closeable together with the controller.
 */

fun HomeFeedController.watchState(onEach: (HomeFeedState) -> Unit): Closeable = FlowWatcher(state).watch(onEach)

fun HomeFeedController.watchEffects(onEach: (HomeFeedEffect) -> Unit): Closeable = FlowWatcher(effects).watch(onEach)

/** The current state, typed (a `StateFlow`'s value reaches Swift untyped). */
val HomeFeedController.currentState: HomeFeedState get() = state.value
