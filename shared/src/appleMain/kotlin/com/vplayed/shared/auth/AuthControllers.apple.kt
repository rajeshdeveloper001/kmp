package com.vplayed.shared.auth

import com.vplayed.shared.core.interop.Closeable
import com.vplayed.shared.core.interop.FlowWatcher

/*
 * Swift observation of the sign-in controllers. Values arrive on the main thread; the Swift adapter
 * closes each returned Closeable together with the controller.
 */

fun EmailLoginController.watchState(onEach: (EmailLoginState) -> Unit): Closeable = FlowWatcher(state).watch(onEach)

fun EmailLoginController.watchEffects(onEach: (EmailLoginEffect) -> Unit): Closeable = FlowWatcher(effects).watch(onEach)

fun OtpController.watchState(onEach: (OtpState) -> Unit): Closeable = FlowWatcher(state).watch(onEach)

fun OtpController.watchEffects(onEach: (OtpEffect) -> Unit): Closeable = FlowWatcher(effects).watch(onEach)

/** The current state, typed (a `StateFlow`'s value reaches Swift untyped). */
val EmailLoginController.currentState: EmailLoginState get() = state.value

val OtpController.currentState: OtpState get() = state.value
