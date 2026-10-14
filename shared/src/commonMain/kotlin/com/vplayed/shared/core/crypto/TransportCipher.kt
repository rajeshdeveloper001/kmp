package com.vplayed.shared.core.crypto

import kotlin.io.encoding.Base64

/** Platform cryptography the transport format is built on. Implementations may throw; [TransportCipher] catches. */
interface CryptoPrimitives {
    fun md5(data: ByteArray): ByteArray

    /**
     * Raw AES-256-CBC without padding; [data] is a whole number of 16-byte blocks. Padding is done
     * by [TransportCipher] so it is validated identically on every platform (CommonCrypto only
     * checks the last padding byte).
     */
    fun aes256CbcEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray

    fun aes256CbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray

    /** Cryptographically secure random bytes. */
    fun randomBytes(count: Int): ByteArray
}

/** javax.crypto on Android, CommonCrypto on Apple platforms. */
expect fun platformCryptoPrimitives(): CryptoPrimitives

/**
 * Request-payload encryption the VPlayed backend expects for `email-login` / `verifyEmailOtp`
 * (`{"data": "<cipher>"}`): `CryptoJS.AES.encrypt(json, passphrase)` with a string passphrase.
 *
 * - key + IV = OpenSSL `EVP_BytesToKey(MD5, 1 iteration)` over passphrase and an 8-byte random salt
 * - AES-256-CBC, PKCS#7 padding
 * - output = Base64 (no line breaks) of `"Salted__" + salt + ciphertext`
 *
 * Replaces AndroidJP `core/security/CryptoTransport.kt` and VPlayed `Core/Auth/CryptoJSAES.swift`,
 * which implement the same format. Do not change it without a matching backend change.
 */
class TransportCipher(
    private val primitives: CryptoPrimitives,
    private val saltSource: (Int) -> ByteArray = primitives::randomBytes,
) {
    /** The encrypted text, or null when encryption is impossible (no passphrase, crypto failure); never an empty payload. */
    fun encrypt(passphrase: String, plainText: String): String? {
        if (passphrase.isEmpty()) return null
        return try {
            val salt = saltSource(SALT_LENGTH)
            require(salt.size == SALT_LENGTH)
            val (key, iv) = deriveKeyAndIv(passphrase.encodeToByteArray(), salt)
            val cipherText = primitives.aes256CbcEncrypt(key, iv, pkcs7Pad(plainText.encodeToByteArray()))
            Base64.Default.encode(SALTED_PREFIX + salt + cipherText)
        } catch (_: Exception) {
            null
        }
    }

    /** The inverse of [encrypt], for tests and debugging. Null for anything that is not valid ciphertext for [passphrase]. */
    fun decrypt(passphrase: String, cipherText: String): String? {
        if (passphrase.isEmpty()) return null
        return try {
            val bytes = Base64.Default.decode(cipherText)
            if (bytes.size <= HEADER_LENGTH || !bytes.copyOfRange(0, SALTED_PREFIX.size).contentEquals(SALTED_PREFIX)) return null
            val salt = bytes.copyOfRange(SALTED_PREFIX.size, HEADER_LENGTH)
            val (key, iv) = deriveKeyAndIv(passphrase.encodeToByteArray(), salt)
            val body = bytes.copyOfRange(HEADER_LENGTH, bytes.size)
            if (body.size % BLOCK_LENGTH != 0) return null
            val padded = primitives.aes256CbcDecrypt(key, iv, body)
            pkcs7Unpad(padded)?.decodeToString(throwOnInvalidSequence = true)
        } catch (_: Exception) {
            null
        }
    }

    /** OpenSSL EVP_BytesToKey with MD5 and one iteration: D_i = MD5(D_{i-1} + passphrase + salt). */
    internal fun deriveKeyAndIv(passphrase: ByteArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
        var derived = ByteArray(0)
        var block = ByteArray(0)
        while (derived.size < KEY_LENGTH + IV_LENGTH) {
            block = primitives.md5(block + passphrase + salt)
            derived += block
        }
        return derived.copyOfRange(0, KEY_LENGTH) to derived.copyOfRange(KEY_LENGTH, KEY_LENGTH + IV_LENGTH)
    }

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val padding = BLOCK_LENGTH - data.size % BLOCK_LENGTH
        return data + ByteArray(padding) { padding.toByte() }
    }

    /** Null unless every padding byte is valid (a wrong key almost always fails here). */
    private fun pkcs7Unpad(data: ByteArray): ByteArray? {
        if (data.isEmpty()) return null
        val padding = data.last().toInt() and 0xFF
        if (padding !in 1..BLOCK_LENGTH || padding > data.size) return null
        for (index in data.size - padding until data.size) {
            if ((data[index].toInt() and 0xFF) != padding) return null
        }
        return data.copyOf(data.size - padding)
    }

    internal companion object {
        const val BLOCK_LENGTH = 16
        const val SALT_LENGTH = 8
        const val KEY_LENGTH = 32
        const val IV_LENGTH = 16
        val SALTED_PREFIX = "Salted__".encodeToByteArray()
        val HEADER_LENGTH = SALTED_PREFIX.size + SALT_LENGTH
    }
}
