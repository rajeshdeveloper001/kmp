package com.vplayed.shared.session

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Secrets the shared layer persists. Each maps to the item the apps already use, so existing users stay signed in. */
enum class SecureKey {
    /** AndroidJP: DataStore file `session` (Keystore AES-GCM). VPlayed: Keychain service `com.contus.vplayed`, account `session`. */
    SESSION,
}

/**
 * Platform secure storage for credentials: AndroidJP's `KeystoreSecureStore` (Keystore-encrypted
 * DataStore), VPlayed's `KeychainSecureStore` (through [DispatchingSecureStore]). Never a database
 * or plain preferences. Implementations must not throw: failures are reported through the return values.
 */
interface SecureStore {
    /** Null when absent or unreadable (corrupt, or the key was lost); the caller treats that as signed out. */
    suspend fun read(key: SecureKey): String?

    /** False when the value could not be persisted; the caller keeps it in memory for this run only. */
    suspend fun write(key: SecureKey, value: String): Boolean

    suspend fun delete(key: SecureKey)
}

/**
 * A [SecureStore] with synchronous calls, for platforms whose storage API is synchronous (the
 * Apple Keychain): Swift implements this rather than Kotlin suspend functions. Called from a
 * background thread, so implementations must be thread-safe. Same contract as [SecureStore].
 */
interface BlockingSecureStore {
    fun read(key: SecureKey): String?

    fun write(key: SecureKey, value: String): Boolean

    fun delete(key: SecureKey)
}

/** Runs a [BlockingSecureStore] off the caller's thread, so a Keychain call never blocks the main thread. */
class DispatchingSecureStore(
    private val store: BlockingSecureStore,
    private val dispatcher: CoroutineDispatcher,
) : SecureStore {
    constructor(store: BlockingSecureStore) : this(store, Dispatchers.Default)

    override suspend fun read(key: SecureKey): String? = withContext(dispatcher) { store.read(key) }

    override suspend fun write(key: SecureKey, value: String): Boolean = withContext(dispatcher) { store.write(key, value) }

    override suspend fun delete(key: SecureKey) = withContext(dispatcher) { store.delete(key) }
}

/**
 * A [SecureStore] that keeps values for the life of the object: for tests and previews, never for
 * a release build. [isAvailable] = false makes writes fail, as when the Keystore or Keychain is unavailable.
 */
class InMemorySecureStore(initial: Map<SecureKey, String>) : SecureStore {
    constructor() : this(emptyMap())

    private val values = MutableStateFlow(initial)

    var isAvailable: Boolean = true

    /** What is stored now (tests check that sign-out removed it). */
    fun peek(key: SecureKey): String? = values.value[key]

    override suspend fun read(key: SecureKey): String? = values.value[key]

    override suspend fun write(key: SecureKey, value: String): Boolean {
        if (!isAvailable) return false
        values.update { it + (key to value) }
        return true
    }

    override suspend fun delete(key: SecureKey) {
        values.update { it - key }
    }
}
