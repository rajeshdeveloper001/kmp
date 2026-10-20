package com.vplayed.shared.session

import com.vplayed.shared.auth.AuthRepository
import com.vplayed.shared.auth.GuestLoginResult
import com.vplayed.shared.core.platform.SharedLogger
import com.vplayed.shared.settings.CommonSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * The UIKit app's automatic guest session (`checkIfUserLoggedIn`): whenever nobody is signed in and
 * common settings allow guests (`is_guest_user`), start a guest session for this device
 * (`auth/generate`). One rule covers launch, sign-out and an expired session. A guest session that
 * arrives after a real sign-in is dropped ([SessionManager.signInIfSignedOut]). A failed attempt is
 * logged and tried again on the next status or settings change.
 *
 * Each app calls [start] once at launch, next to loading the common settings.
 */
class GuestSessionKeeper internal constructor(
    private val sessions: SessionManager,
    private val auth: AuthRepository,
    private val settings: CommonSettingsStore,
    private val scope: CoroutineScope,
    private val logger: SharedLogger,
) {
    private val started = MutableStateFlow(false)

    /** Starts watching; later calls do nothing. */
    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        scope.launch {
            sessions.awaitLoaded()
            combine(sessions.status, settings.settings) { status, current ->
                status is SessionStatus.SignedOut && current?.isGuestUser == true
            }
                .distinctUntilChanged()
                .filter { it }
                .collect { startGuestSession() }
        }
    }

    private suspend fun startGuestSession() {
        when (val result = auth.guestLogin()) {
            is GuestLoginResult.SignedIn -> sessions.signInIfSignedOut(result.session)
            is GuestLoginResult.Rejected -> logger.info(TAG, "Guest session refused")
            is GuestLoginResult.Failure -> logger.info(TAG, "Guest session failed: ${result.error::class.simpleName}")
        }
    }

    private companion object {
        const val TAG = "GuestSession"
    }
}
