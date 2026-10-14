package com.vplayed.shared.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs on every target, so it checks the javax.crypto and the CommonCrypto primitives alike. */
class TransportCipherTest {

    private val fixedSalt = ByteArray(8) { it.toByte() } // 00 01 … 07
    private val cipher = TransportCipher(platformCryptoPrimitives()) { fixedSalt }

    @Test
    fun givenFixedSalt_whenEncrypting_thenOutputMatchesOpenSsl() {
        // Independent vector from the OpenSSL CLI (test-only passphrase, not the backend key):
        // openssl enc -aes-256-cbc -md md5 -S 0001020304050607 -pass pass:unit-test-passphrase
        // with the "Salted__" + salt header prepended.
        val expected = "U2FsdGVkX18AAQIDBAUGB1SmCw6SM2RihiMLMuIzTLut/9jlUU9FG1J2YbkpEIbj"

        assertEquals(expected, cipher.encrypt(PASSPHRASE, """{"email":"user@example.com"}"""))
    }

    @Test
    fun givenPassphraseAndSalt_whenDerivingKey_thenMatchesEvpBytesToKey() {
        val (key, iv) = cipher.deriveKeyAndIv(PASSPHRASE.encodeToByteArray(), fixedSalt)

        assertEquals("DE5E5B57E879AB014999042E0EEC26A63681316E6FD80FEBB1040BECE9FF03F1", key.toHex())
        assertEquals("95D06E8B8B71C5FE67D2BF31C8D88CE5", iv.toHex())
    }

    @Test
    fun givenRandomSalt_whenEncryptingTwice_thenOutputsDifferAndBothDecrypt() {
        val random = TransportCipher(platformCryptoPrimitives())
        val plain = """{"email":"user@example.com","otp":"123456","deviceDetail":"Pixel 9"}"""

        val first = random.encrypt(PASSPHRASE, plain)
        val second = random.encrypt(PASSPHRASE, plain)

        assertNotEquals(first, second)
        assertTrue(first!!.startsWith("U2FsdGVkX1")) // Base64 of "Salted__"
        assertEquals(plain, random.decrypt(PASSPHRASE, first))
        assertEquals(plain, random.decrypt(PASSPHRASE, second!!))
    }

    @Test
    fun givenNonAsciiText_whenRoundTripping_thenTextIsPreserved() {
        val plain = """{"name":"தமிழ் العربية ação"}"""

        assertEquals(plain, cipher.decrypt(PASSPHRASE, cipher.encrypt(PASSPHRASE, plain)!!))
    }

    @Test
    fun givenPlainTextOfWholeBlocks_whenRoundTripping_thenAFullPaddingBlockIsAddedAndRemoved() {
        listOf("", "0123456789abcdef", "0123456789abcdef0123456789abcdef").forEach { plain ->
            val encrypted = cipher.encrypt(PASSPHRASE, plain)!!

            // "Salted__" + salt (16 bytes) + ciphertext rounded up to the next whole block.
            assertEquals(16 + (plain.length / 16 + 1) * 16, kotlin.io.encoding.Base64.Default.decode(encrypted).size, plain)
            assertEquals(plain, cipher.decrypt(PASSPHRASE, encrypted))
        }
    }

    @Test
    fun givenEmptyPassphrase_whenEncrypting_thenNoPayloadIsProduced() {
        assertNull(cipher.encrypt("", "{}"))
    }

    @Test
    fun givenWrongPassphraseOrGarbage_whenDecrypting_thenNull() {
        val encrypted = cipher.encrypt(PASSPHRASE, "{}")!!

        assertNull(cipher.decrypt("another-passphrase", encrypted))
        assertNull(cipher.decrypt(PASSPHRASE, "not base64 !!"))
        assertNull(cipher.decrypt(PASSPHRASE, "AAAA"))
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.uppercase()

    private companion object {
        const val PASSPHRASE = "unit-test-passphrase"
    }
}
