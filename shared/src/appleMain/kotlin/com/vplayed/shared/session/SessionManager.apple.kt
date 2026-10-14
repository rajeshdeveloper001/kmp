package com.vplayed.shared.session

import com.vplayed.shared.core.interop.Closeable
import com.vplayed.shared.core.interop.FlowWatcher

/*
 * Swift observation of the session. Values arrive on the main thread, starting with the current one;
 * the Swift adapter closes the returned Closeable when it goes away.
 */

fun SessionManager.watchStatus(onEach: (SessionStatus) -> Unit): Closeable = FlowWatcher(status).watch(onEach)

/** The current status, typed (a `StateFlow`'s value reaches Swift untyped). */
val SessionManager.currentStatus: SessionStatus get() = status.value
