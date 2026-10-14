package com.vplayed.shared.core.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCSuccess
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.size_tVar

/** CommonCrypto, as VPlayed's former `CryptoJSAES.swift` used. */
@OptIn(ExperimentalForeignApi::class)
private object AppleCryptoPrimitives : CryptoPrimitives {

    // MD5 is required by the backend's OpenSSL-compatible key derivation, not used for integrity.
    @Suppress("DEPRECATION")
    override fun md5(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "MD5 input must not be empty" }
        val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
        data.usePinned { input ->
            digest.usePinned { output ->
                CC_MD5(input.addressOf(0), data.size.convert(), output.addressOf(0).reinterpret())
            }
        }
        return digest
    }

    override fun aes256CbcEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray = crypt(kCCEncrypt, key, iv, data)

    override fun aes256CbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray = crypt(kCCDecrypt, key, iv, data)

    override fun randomBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        if (count == 0) return bytes
        val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, count.convert(), it.addressOf(0)) }
        check(status == errSecSuccess) { "SecRandomCopyBytes failed: $status" }
        return bytes
    }

    private const val BLOCK_SIZE = 16
    private const val NO_OPTIONS = 0u

    private fun crypt(operation: UInt, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        // Raw CBC: TransportCipher adds and checks the PKCS#7 padding.
        require(data.isNotEmpty() && data.size % BLOCK_SIZE == 0) { "Data must be whole AES blocks" }
        val output = ByteArray(data.size)
        val written = memScoped {
            val moved = alloc<size_tVar>()
            val status = key.usePinned { keyPin ->
                iv.usePinned { ivPin ->
                    output.usePinned { outPin ->
                        data.usePinned { inPin ->
                            CCCrypt(
                                operation.convert(),
                                kCCAlgorithmAES.convert(),
                                NO_OPTIONS,
                                keyPin.addressOf(0), key.size.convert(),
                                ivPin.addressOf(0),
                                inPin.addressOf(0), data.size.convert(),
                                outPin.addressOf(0), output.size.convert(),
                                moved.ptr,
                            )
                        }
                    }
                }
            }
            check(status == kCCSuccess) { "CCCrypt failed: $status" }
            moved.value.toInt()
        }
        return output.copyOf(written)
    }
}

actual fun platformCryptoPrimitives(): CryptoPrimitives = AppleCryptoPrimitives
