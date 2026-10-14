package com.vplayed.shared.session

/** Secrets the shared layer persists. Each maps to the item the apps already use, so existing users stay signed in. */
enum class SecureKey {
    /** AndroidJP: DataStore file `session` (Keystore AES-GCM). VPlayed: Keychain service `com.contus.vplayed`, account `session`. */
    SESSION,
}

/**
 * Platform secure storage for credentials (Android Keystore-backed, Apple Keychain). Implemented by
 * each app from its existing code in Phase 5; never a database or plain preferences.
 * Implementations must not throw: failures are reported through the return values.
 */
interface SecureStore {
    /** Null when absent or unreadable (corrupt, or the key was lost); the caller treats that as signed out. */
    suspend fun read(key: SecureKey): String?

    /** False when the value could not be persisted; the caller keeps it in memory for this run only. */
    suspend fun write(key: SecureKey, value: String): Boolean

    suspend fun delete(key: SecureKey)
}
