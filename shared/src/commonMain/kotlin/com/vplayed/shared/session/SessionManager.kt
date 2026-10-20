package com.vplayed.shared.session

import com.vplayed.shared.auth.AuthRepository
import com.vplayed.shared.auth.AuthSession
import com.vplayed.shared.auth.LogoutResult
import com.vplayed.shared.core.network.AuthTokens
import com.vplayed.shared.core.network.RequestCredentials
import com.vplayed.shared.geo.GeoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why the last session ended. */
enum class SignOutReason {
    /** The user signed out (logout), or the app ended the session. */
    USER,

    /** The server rejected the access token (401) and it could not be refreshed (decision D21, revised). */
    EXPIRED,
}

sealed interface SessionStatus {
    /** The stored session has not been read yet. Backend requests wait for it. */
    data object Loading : SessionStatus

    /** No session. [reason] is null when none was stored at launch. */
    data class SignedOut(val reason: SignOutReason?) : SessionStatus

    /** A signed-in or guest ([AuthSession.isGuest]) session; its tokens go with every backend request. */
    data class SignedIn(val session: AuthSession) : SessionStatus
}

/**
 * Owns the signed-in (or guest) session: restores it from the [SecureStore] once, stores new
 * sessions, ends them on sign-out or when the server rejects the token. Each app creates exactly
 * one, before the [com.vplayed.shared.SharedGraph], and passes it in.
 *
 * Restoring is lazy: the first [awaitLoaded] reads the store (backend requests call it, so the first
 * request after a launch already carries the stored session). Until then [status] is [SessionStatus.Loading].
 *
 * Storage failures never lose a sign-in: a session that cannot be stored is kept in memory for this
 * run, and the previously stored one is removed so a relaunch cannot bring back an older account.
 * Tokens are never logged.
 */
class SessionManager(private val store: SecureStore) {

    private val mutex = Mutex()

    /** One token refresh at a time. Always taken before [mutex], never while holding it. */
    private val refreshMutex = Mutex()
    private val _status = MutableStateFlow<SessionStatus>(SessionStatus.Loading)

    val status: StateFlow<SessionStatus> = _status.asStateFlow()

    /** The current session; null while loading or signed out. */
    val session: AuthSession? get() = (_status.value as? SessionStatus.SignedIn)?.session

    /** Reads the stored session if that has not happened yet. A stored value that cannot be read is removed. */
    suspend fun awaitLoaded() {
        if (_status.value != SessionStatus.Loading) return
        mutex.withLock {
            if (_status.value != SessionStatus.Loading) return
            val stored = store.read(SecureKey.SESSION)
            val session = stored?.let(SessionCodec::decode)
            if (stored != null && session == null) store.delete(SecureKey.SESSION)
            _status.value = session?.let { SessionStatus.SignedIn(it) } ?: SessionStatus.SignedOut(reason = null)
        }
    }

    /** Starts [session] (sign-in or guest) and stores it. Replaces any current session. */
    suspend fun signIn(session: AuthSession) {
        awaitLoaded()
        mutex.withLock {
            if (!store.write(SecureKey.SESSION, SessionCodec.encode(session))) store.delete(SecureKey.SESSION)
            _status.value = SessionStatus.SignedIn(session)
        }
    }

    /**
     * Logs out on the server ([AuthRepository.logout]), then ends the session here when the server
     * confirms it or no longer knows the token. Other failures keep the session, so the user can
     * retry (VPlayed's UIKit rule). Both apps' sign-out entries use this.
     */
    suspend fun logout(authRepository: AuthRepository): LogoutResult {
        val result = authRepository.logout()
        if (result is LogoutResult.LoggedOut || result is LogoutResult.SessionAlreadyEnded) signOut(SignOutReason.USER)
        return result
    }

    /** Ends the session here only, without telling the server (see [logout]). */
    suspend fun signOut(reason: SignOutReason) {
        awaitLoaded()
        mutex.withLock {
            store.delete(SecureKey.SESSION)
            _status.value = SessionStatus.SignedOut(reason)
        }
    }

    /**
     * Starts [session] only when nobody is signed in, and stores it. For sessions nobody asked for
     * (the automatic guest session): one that arrives after a sign-in must not replace it.
     * Returns whether it was started.
     */
    suspend fun signInIfSignedOut(session: AuthSession): Boolean {
        awaitLoaded()
        return mutex.withLock {
            if (_status.value !is SessionStatus.SignedOut) return@withLock false
            if (!store.write(SecureKey.SESSION, SessionCodec.encode(session))) store.delete(SecureKey.SESSION)
            _status.value = SessionStatus.SignedIn(session)
            true
        }
    }

    /**
     * A request carrying [rejectedAccessToken] got a 401. Tries once to get a new access token with
     * the session's refresh token (one refresh at a time; requests that fail together share it) and
     * returns true when the request should be sent again:
     * - another request already replaced the token → true;
     * - no refresher, no refresh token, or the server refuses it → the session expires, false;
     * - no usable answer (offline) → the session is kept, false.
     */
    internal suspend fun recoverFromUnauthorized(rejectedAccessToken: String, refresher: AccessTokenRefresher?): Boolean =
        refreshMutex.withLock {
            val current = session ?: return@withLock false
            if (current.accessToken != rejectedAccessToken) return@withLock true
            val refreshToken = current.refreshToken?.takeIf { it.isNotBlank() }
            if (refresher == null || refreshToken == null) {
                expire(rejectedAccessToken)
                return@withLock false
            }
            when (val result = refresher.refresh(refreshToken)) {
                is TokenRefreshResult.Refreshed -> replaceAccessToken(rejectedAccessToken, result.accessToken)
                TokenRefreshResult.Rejected -> {
                    expire(rejectedAccessToken)
                    false
                }
                is TokenRefreshResult.Failed -> false
            }
        }

    /** Swaps the access token of the session that still uses [oldAccessToken] and stores it. */
    private suspend fun replaceAccessToken(oldAccessToken: String, accessToken: String): Boolean = mutex.withLock {
        val current = session?.takeIf { it.accessToken == oldAccessToken } ?: return@withLock false
        val refreshed = current.copy(accessToken = accessToken)
        if (!store.write(SecureKey.SESSION, SessionCodec.encode(refreshed))) store.delete(SecureKey.SESSION)
        _status.value = SessionStatus.SignedIn(refreshed)
        true
    }

    /**
     * The server rejected [rejectedAccessToken]. Ends the session only if it still uses that token:
     * a request sent before a new sign-in must not end the new session.
     */
    internal suspend fun expire(rejectedAccessToken: String) {
        mutex.withLock {
            if (session?.accessToken != rejectedAccessToken) return
            store.delete(SecureKey.SESSION)
            _status.value = SessionStatus.SignedOut(SignOutReason.EXPIRED)
        }
    }
}

/** What the request headers send: this session's tokens and the geo token. */
internal class SessionCredentials(
    private val sessions: SessionManager,
    private val geoStore: GeoStore,
) : RequestCredentials {
    override val authTokens: AuthTokens?
        get() = sessions.session?.let { AuthTokens(it.accessToken, it.profileId) }?.takeIf { it.accessToken.isNotBlank() }

    override val geoToken: String? get() = geoStore.geoToken
}
