package com.vplayed.shared.session

import com.vplayed.shared.auth.AuthSession

internal fun testSession(accessToken: String = "access-1", profileId: String? = "profile-1", isGuest: Boolean = false) = AuthSession(
    accessToken = accessToken,
    refreshToken = "refresh-1",
    userId = "u-1",
    uniqueUserId = 42,
    email = "user@example.com",
    phone = null,
    firstName = "Sam",
    profileId = profileId,
    isGuest = isGuest,
)

/** A store holding [session] as a previous run saved it. */
internal fun storeHolding(session: AuthSession) = InMemorySecureStore(mapOf(SecureKey.SESSION to SessionCodec.encode(session)))

/** A manager over an in-memory store, holding [stored] as if a previous run had saved it. */
internal fun testSessions(stored: AuthSession? = null) = SessionManager(stored?.let(::storeHolding) ?: InMemorySecureStore())
