// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import com.wisso.wizefiles.storage.VaultPayloadFrame

object VaultCrypto {
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val NONCE_LENGTH_BYTES = 12

    val defaultArgon2Params = Argon2Params(memoryKiB = 64 * 1024, iterations = 2, parallelism = 1)

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun derivePasswordKey(password: CharArray, salt: ByteArray, params: Argon2Params): ByteArray {
        val passwordBytes = password.concatToString().toByteArray(Charsets.UTF_8)
        return try {
            val generator = Argon2BytesGenerator()
            val argonParams = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .build()
            generator.init(argonParams)
            ByteArray(params.outputLength).also { generator.generateBytes(passwordBytes, it, 0, it.size) }
        } finally {
            password.fill('\u0000')
            zero(passwordBytes)
        }
    }

    fun encryptAesGcm(plaintext: ByteArray, keyBytes: ByteArray, aad: ByteArray? = null): EncryptedPayload {
        val iv = randomBytes(NONCE_LENGTH_BYTES)
        val cipher = createAesGcmCipher(Cipher.ENCRYPT_MODE, keyBytes, iv, aad)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedPayload(iv, ciphertext)
    }

    fun decryptAesGcm(payload: EncryptedPayload, keyBytes: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = createAesGcmCipher(Cipher.DECRYPT_MODE, keyBytes, payload.iv, aad)
        return cipher.doFinal(payload.ciphertext)
    }

    fun createEncryptCipher(keyBytes: ByteArray, aad: ByteArray? = null): Pair<Cipher, ByteArray> {
        val iv = randomBytes(NONCE_LENGTH_BYTES)
        return createAesGcmCipher(Cipher.ENCRYPT_MODE, keyBytes, iv, aad) to iv
    }

    fun createDecryptCipher(iv: ByteArray, keyBytes: ByteArray, aad: ByteArray? = null): Cipher =
        createAesGcmCipher(Cipher.DECRYPT_MODE, keyBytes, iv, aad)

    fun zero(bytes: ByteArray) {
        bytes.fill(0)
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun fromBase64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)


    private fun createAesGcmCipher(mode: Int, keyBytes: ByteArray, iv: ByteArray, aad: ByteArray?): Cipher {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val key: SecretKey = SecretKeySpec(keyBytes, "AES")
        cipher.init(mode, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        aad?.let(cipher::updateAAD)
        return cipher
    }

    data class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray) {
        fun encode(): ByteArray = VaultPayloadFrame.encode(encodeLegacy())

        private fun encodeLegacy(): ByteArray = ByteArray(1 + iv.size + ciphertext.size).also {
            it[0] = iv.size.toByte()
            System.arraycopy(iv, 0, it, 1, iv.size)
            System.arraycopy(ciphertext, 0, it, 1 + iv.size, ciphertext.size)
        }

        companion object {
            fun decode(bytes: ByteArray): EncryptedPayload {
                val payload = if (bytes.size >= 4 && bytes[0] == 'W'.code.toByte() &&
                    bytes[1] == 'Z'.code.toByte() && bytes[2] == 'V'.code.toByte() &&
                    bytes[3] == 'F'.code.toByte()
                ) VaultPayloadFrame.decode(bytes) else bytes // Read pre-v1 vaults during migration.
                require(payload.isNotEmpty()) { "Encrypted payload is empty" }
                val ivSize = payload[0].toInt() and 0xFF
                require(ivSize == NONCE_LENGTH_BYTES) { "Encrypted payload has an invalid IV length" }
                require(payload.size >= 1 + ivSize + GCM_TAG_LENGTH_BITS / Byte.SIZE_BITS) {
                    "Encrypted payload is truncated"
                }
                val iv = payload.copyOfRange(1, 1 + ivSize)
                val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
                return EncryptedPayload(iv, ciphertext)
            }
        }
    }
}
