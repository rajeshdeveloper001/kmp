package com.vplayed.shared.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionManagerTest {

    @Test
    fun givenNothingStored_whenLoaded_thenSignedOutWithoutReason() = runTest {
        val sessions = testSessions()
        assertEquals(SessionStatus.Loading, sessions.status.value)

        sessions.awaitLoaded()

        assertEquals(SessionStatus.SignedOut(reason = null), sessions.status.value)
        assertNull(sessions.session)
    }

    @Test
    fun givenStoredSession_whenLoaded_thenRestored() = runTest {
        val sessions = testSessions(stored = testSession())

        sessions.awaitLoaded()

        assertEquals(SessionStatus.SignedIn(testSession()), sessions.status.value)
    }

    @Test
    fun givenUnreadableStoredValue_whenLoaded_thenSignedOutAndValueRemoved() = runTest {
        val store = InMemorySecureStore(mapOf(SecureKey.SESSION to "{not json"))
        val sessions = SessionManager(store)

        sessions.awaitLoaded()

        assertEquals(SessionStatus.SignedOut(reason = null), sessions.status.value)
        assertNull(store.peek(SecureKey.SESSION))
    }

    @Test
    fun givenSignIn_whenANewManagerLoads_thenTheSessionIsRestored() = runTest {
        val store = InMemorySecureStore()
        SessionManager(store).signIn(testSession())

        val next = SessionManager(store)
        next.awaitLoaded()

        assertEquals(testSession(), next.session)
    }

    @Test
    fun givenSignInBeforeLoading_whenSignedIn_thenTheStoredSessionDoesNotOverwriteIt() = runTest {
        val sessions = testSessions(stored = testSession("old-token"))

        sessions.signIn(testSession("new-token"))

        assertEquals("new-token", sessions.session?.accessToken)
        sessions.awaitLoaded()
        assertEquals("new-token", sessions.session?.accessToken)
    }

    @Test
    fun givenStorageUnavailable_whenSigningIn_thenKeptInMemoryAndTheOldSessionIsRemoved() = runTest {
        val store = storeHolding(testSession("previous-user"))
        store.isAvailable = false
        val sessions = SessionManager(store)

        sessions.signIn(testSession("access-2"))

        assertEquals("access-2", sessions.session?.accessToken)
        assertNull(store.peek(SecureKey.SESSION), "A relaunch must not restore the previous account")
    }

    @Test
    fun givenSession_whenSignedOut_thenStoreClearedAndReasonKept() = runTest {
        val store = storeHolding(testSession())
        val sessions = SessionManager(store)

        sessions.signOut(SignOutReason.USER)

        assertEquals(SessionStatus.SignedOut(SignOutReason.USER), sessions.status.value)
        assertNull(store.peek(SecureKey.SESSION))
    }

    @Test
    fun givenRejectedToken_whenItIsTheCurrentOne_thenSessionExpires() = runTest {
        val store = storeHolding(testSession("access-1"))
        val sessions = SessionManager(store)
        sessions.awaitLoaded()

        sessions.expire("access-1")

        assertEquals(SessionStatus.SignedOut(SignOutReason.EXPIRED), sessions.status.value)
        assertNull(store.peek(SecureKey.SESSION))
    }

    @Test
    fun givenRejectedToken_whenANewerSessionIsActive_thenItIsKept() = runTest {
        val sessions = testSessions(stored = testSession("access-1"))
        sessions.signIn(testSession("access-2"))

        sessions.expire("access-1")

        assertEquals("access-2", sessions.session?.accessToken)
    }

    @Test
    fun givenSession_whenPrinted_thenTokensAreMasked() = runTest {
        val sessions = testSessions(stored = testSession("secret-access"))
        sessions.awaitLoaded()

        val text = sessions.status.value.toString()

        assertEquals(false, "secret-access" in text || "refresh-1" in text, text)
    }
}
