package com.vplayed.shared.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private object JvmCryptoPrimitives : CryptoPrimitives {
    // Padding is added and checked by TransportCipher.
    private const val TRANSFORMATION = "AES/CBC/NoPadding"
    private const val ALGORITHM = "AES"
    private val random = SecureRandom()

    override fun md5(data: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(data)

    override fun aes256CbcEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, iv).doFinal(data)

    override fun aes256CbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
        cipher(Cipher.DECRYPT_MODE, key, iv).doFinal(data)

    override fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    private fun cipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(mode, SecretKeySpec(key, ALGORITHM), IvParameterSpec(iv)) }
}

actual fun platformCryptoPrimitives(): CryptoPrimitives = JvmCryptoPrimitives
