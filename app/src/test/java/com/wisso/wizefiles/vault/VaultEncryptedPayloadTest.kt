package com.wisso.wizefiles.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultEncryptedPayloadTest {
    @Test
    fun `encoded payload round trips`() {
        val payload = VaultCrypto.EncryptedPayload(
            iv = ByteArray(12) { it.toByte() },
            ciphertext = ByteArray(16) { (it + 12).toByte() }
        )

        val decoded = VaultCrypto.EncryptedPayload.decode(payload.encode())

        assertArrayEquals(payload.iv, decoded.iv)
        assertArrayEquals(payload.ciphertext, decoded.ciphertext)
        assertTrue(payload.encode().copyOfRange(0, 4).contentEquals("WZVF".encodeToByteArray()))
    }

    @Test
    fun `legacy unframed payload remains readable during migration`() {
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = ByteArray(16) { (it + 12).toByte() }
        val legacy = byteArrayOf(12) + iv + ciphertext
        val decoded = VaultCrypto.EncryptedPayload.decode(legacy)
        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(ciphertext, decoded.ciphertext)
    }

    @Test
    fun `empty payload is rejected`() {
        assertMalformed(byteArrayOf())
    }

    @Test
    fun `zero length IV is rejected`() {
        assertMalformed(byteArrayOf(0) + ByteArray(16))
    }

    @Test
    fun `oversized unsigned IV length is rejected`() {
        assertMalformed(byteArrayOf(0xFF.toByte()) + ByteArray(16))
    }

    @Test
    fun `nonstandard IV length is rejected`() {
        assertMalformed(byteArrayOf(11) + ByteArray(11 + 16))
    }

    @Test
    fun `missing authentication tag is rejected`() {
        assertMalformed(byteArrayOf(12) + ByteArray(12 + 15))
    }

    private fun assertMalformed(bytes: ByteArray) {
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.EncryptedPayload.decode(bytes)
        }
    }
}
