package com.vplayed.shared.session

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The adapter VPlayed's Keychain store runs through (its Swift side is tested in VPlayed). */
class DispatchingSecureStoreTest {

    /** A synchronous store like the Swift Keychain one; [refuseWrites] mimics a Keychain that rejects items. */
    private class FakeBlockingStore(var refuseWrites: Boolean = false) : BlockingSecureStore {
        val values = mutableMapOf<SecureKey, String>()

        override fun read(key: SecureKey): String? = values[key]

        override fun write(key: SecureKey, value: String): Boolean {
            if (refuseWrites) return false
            values[key] = value
            return true
        }

        override fun delete(key: SecureKey) {
            values.remove(key)
        }
    }

    @Test
    fun givenBlockingStore_whenUsedThroughTheAdapter_thenReadsWritesAndDeletesPassThrough() = runTest {
        val blocking = FakeBlockingStore()
        val store = DispatchingSecureStore(blocking, StandardTestDispatcher(testScheduler))

        assertTrue(store.write(SecureKey.SESSION, "value-1"))
        assertEquals("value-1", store.read(SecureKey.SESSION))
        store.delete(SecureKey.SESSION)
        assertNull(store.read(SecureKey.SESSION))
    }

    @Test
    fun givenStoreRefusingWrites_whenSessionManagerSignsIn_thenMemoryOnlyAndNothingStored() = runTest {
        val blocking = FakeBlockingStore(refuseWrites = true)
        val sessions = SessionManager(DispatchingSecureStore(blocking, StandardTestDispatcher(testScheduler)))

        sessions.signIn(testSession())

        assertEquals(testSession(), sessions.session)
        assertFalse(SecureKey.SESSION in blocking.values)
    }

    @Test
    fun givenSessionStoredByTheBlockingStore_whenLoaded_thenRestored() = runTest {
        val blocking = FakeBlockingStore().apply { values[SecureKey.SESSION] = SessionCodec.encode(testSession()) }

        val sessions = SessionManager(DispatchingSecureStore(blocking))
        sessions.awaitLoaded()

        assertEquals(testSession(), sessions.session)
    }
}
