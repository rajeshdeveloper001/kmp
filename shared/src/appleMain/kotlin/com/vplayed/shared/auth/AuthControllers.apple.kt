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

fun PhoneLoginController.watchState(onEach: (PhoneLoginState) -> Unit): Closeable = FlowWatcher(state).watch(onEach)

fun PhoneLoginController.watchEffects(onEach: (PhoneLoginEffect) -> Unit): Closeable = FlowWatcher(effects).watch(onEach)

fun SocialLoginController.watchState(onEach: (SocialLoginState) -> Unit): Closeable = FlowWatcher(state).watch(onEach)

fun SocialLoginController.watchEffects(onEach: (SocialLoginEffect) -> Unit): Closeable = FlowWatcher(effects).watch(onEach)

val PhoneLoginController.currentState: PhoneLoginState get() = state.value

val SocialLoginController.currentState: SocialLoginState get() = state.value

fun RegistrationController.watchState(onEach: (RegistrationState) -> Unit): Closeable = FlowWatcher(state).watch(onEach)

fun RegistrationController.watchEffects(onEach: (RegistrationEffect) -> Unit): Closeable = FlowWatcher(effects).watch(onEach)

val RegistrationController.currentState: RegistrationState get() = state.value
