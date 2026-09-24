// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultCryptoTest {
    @Test
    fun derivePasswordKey_isDeterministic() {
        val salt = ByteArray(16) { it.toByte() }
        val params = Argon2Params(memoryKiB = 32 * 1024, iterations = 2, parallelism = 1)
        val key1 = VaultCrypto.derivePasswordKey("hello".toCharArray(), salt.copyOf(), params)
        val key2 = VaultCrypto.derivePasswordKey("hello".toCharArray(), salt.copyOf(), params)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun derivePasswordKey_changesWithSalt() {
        val params = Argon2Params(memoryKiB = 32 * 1024, iterations = 2, parallelism = 1)
        val key1 = VaultCrypto.derivePasswordKey("hello".toCharArray(), ByteArray(16) { 1 }, params)
        val key2 = VaultCrypto.derivePasswordKey("hello".toCharArray(), ByteArray(16) { 2 }, params)
        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun aesRoundTripAndTamperDetection() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "secret".toByteArray()
        val encrypted = VaultCrypto.encryptAesGcm(plaintext, key)
        val decrypted = VaultCrypto.decryptAesGcm(encrypted, key)
        assertArrayEquals(plaintext, decrypted)

        val tampered = encrypted.copy(ciphertext = encrypted.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0x1).toByte() })
        org.junit.Assert.assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decryptAesGcm(tampered, key)
        }
    }

    @Test
    fun passwordValidation() {
        assertNotEquals(null, VaultPasswordValidator.validate("", ""))
        assertNotEquals(null, VaultPasswordValidator.validate("a", "b"))
        assertNull(VaultPasswordValidator.validate("abc", "abc"))
    }
}
