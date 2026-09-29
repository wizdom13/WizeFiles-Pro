// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned authenticated envelope. Plaintext identity backups are never exported. */
internal object SyncthingBackupCodec {
    const val MAX_BYTES = 12 * 1024 * 1024
    private val magic = "WFSYNC1\n".toByteArray(Charsets.US_ASCII)
    fun encrypted(bytes: ByteArray): Boolean = bytes.size >= magic.size && bytes.take(magic.size).toByteArray().contentEquals(magic)
    fun read(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Syncthing backup exceeds 12 MiB" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size in 12..1024) { "Use a password of at least 12 characters" }
        require(plain.size <= MAX_BYTES - 64)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val header = magic + salt + iv
        return header + cipher(Cipher.ENCRYPT_MODE, password, salt, iv).apply { updateAAD(header) }.doFinal(plain)
    }
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(encrypted(bytes) && bytes.size in 52..MAX_BYTES) { "Invalid encrypted Syncthing backup" }
        require(password.size in 1..1024)
        val header = bytes.copyOfRange(0, 36)
        return cipher(Cipher.DECRYPT_MODE, password, bytes.copyOfRange(8, 24), bytes.copyOfRange(24, 36))
            .apply { updateAAD(header) }.doFinal(bytes.copyOfRange(36, bytes.size))
    }
    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try { Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        } } finally { key.fill(0) }
    }
}
