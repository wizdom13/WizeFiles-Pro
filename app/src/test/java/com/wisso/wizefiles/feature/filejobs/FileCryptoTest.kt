// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCryptoTest {

    @Test
    fun roundTripAesArgon2id() = assertRoundTrip(FileEncryptionAlgorithm.AES_256_GCM, FileKdfAlgorithm.ARGON2ID)

    @Test
    fun roundTripChachaArgon2id() = assertRoundTrip(FileEncryptionAlgorithm.CHACHA20_POLY1305, FileKdfAlgorithm.ARGON2ID)

    @Test
    fun roundTripAesBcrypt() = assertRoundTrip(FileEncryptionAlgorithm.AES_256_GCM, FileKdfAlgorithm.BCRYPT)

    @Test
    fun roundTripChachaBcrypt() = assertRoundTrip(FileEncryptionAlgorithm.CHACHA20_POLY1305, FileKdfAlgorithm.BCRYPT)

    @Test
    fun tamperedFileFailsDecrypt() {
        val plain = "hello".toByteArray()
        val encrypted = java.io.ByteArrayOutputStream()
        FileCrypto.encrypt(plain.inputStream(), encrypted, "pw".toCharArray(), FileEncryptionAlgorithm.AES_256_GCM, FileKdfAlgorithm.ARGON2ID, "a.txt")
        val bytes = encrypted.toByteArray()
        bytes[bytes.lastIndex] = (bytes.last() xor 0x01)

        val error = runCatching {
            FileCrypto.decrypt(bytes.inputStream(), java.io.ByteArrayOutputStream(), "pw".toCharArray())
        }.exceptionOrNull()

        assertTrue(error is FileCryptoException)
    }

    @Test
    fun tamperedHeaderMetadataFailsDecrypt() {
        val encrypted = java.io.ByteArrayOutputStream()
        FileCrypto.encrypt(
            "authenticated".toByteArray().inputStream(),
            encrypted,
            "pw".toCharArray(),
            FileEncryptionAlgorithm.AES_256_GCM,
            FileKdfAlgorithm.ARGON2ID,
            "original.txt"
        )
        val bytes = encrypted.toByteArray()
        val name = "original.txt".toByteArray()
        val nameOffset = bytes.indexOfSubsequence(name)
        assertTrue(nameOffset >= 0)
        bytes[nameOffset] = 'x'.code.toByte()

        val error = runCatching {
            FileCrypto.decrypt(
                bytes.inputStream(),
                java.io.ByteArrayOutputStream(),
                "pw".toCharArray()
            )
        }.exceptionOrNull()

        assertTrue(error is FileCryptoException)
    }

    @Test
    fun unsafeOriginalNamesAreRejected() {
        listOf(
            "../outside.txt",
            "..\\outside.txt",
            "/absolute.txt",
            "C:\\absolute.txt",
            ".",
            "..",
            "folder/file.txt"
        ).forEach { unsafeName ->
            val error = runCatching {
                FileCrypto.encrypt(
                    "content".toByteArray().inputStream(),
                    java.io.ByteArrayOutputStream(),
                    "pw".toCharArray(),
                    FileEncryptionAlgorithm.AES_256_GCM,
                    FileKdfAlgorithm.ARGON2ID,
                    unsafeName
                )
            }.exceptionOrNull()
            assertTrue("Expected rejection for $unsafeName", error is FileCryptoException)
        }
    }

    @Test
    fun encryptedNamingUsesEncSuffix() {
        val source = File("notes.txt")
        assertEquals("notes.txt.enc", "${source.name}.enc")
        assertEquals("notes.txt", "notes.txt.enc".removeSuffix(".enc"))
    }

    @Test
    fun encryptedHeaderUsesVersion3() {
        val encrypted = java.io.ByteArrayOutputStream()
        FileCrypto.encrypt(
            "v".toByteArray().inputStream(),
            encrypted,
            "pw".toCharArray(),
            FileEncryptionAlgorithm.AES_256_GCM,
            FileKdfAlgorithm.BCRYPT,
            "a.txt"
        )
        assertEquals(3, encrypted.toByteArray()[7].toInt() and 0xFF)
    }

    @Test
    fun folderRecursionEncryptsAndDecryptsEligibleFiles() {
        val root = Files.createTempDirectory("wzf-crypto").toFile()
        val sub = File(root, "sub").apply { mkdirs() }
        File(root, "a.txt").writeText("a")
        File(sub, "b.txt").writeText("b")

        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val out = File(file.parentFile, "${file.name}.enc")
            out.outputStream().use { output ->
                file.inputStream().use { input ->
                    FileCrypto.encrypt(input, output, "pw".toCharArray(), FileEncryptionAlgorithm.AES_256_GCM, FileKdfAlgorithm.ARGON2ID, file.name)
                }
            }
        }

        val encFiles = root.walkTopDown().filter { it.isFile && it.name.endsWith(".enc") }.toList()
        assertEquals(2, encFiles.size)

        encFiles.forEach { enc ->
            val out = File(enc.parentFile, enc.name.removeSuffix(".enc"))
            enc.inputStream().use { input ->
                out.outputStream().use { output ->
                    FileCrypto.decrypt(input, output, "pw".toCharArray())
                }
            }
        }

        assertArrayEquals("a".toByteArray(), File(root, "a.txt").readBytes())
        assertArrayEquals("b".toByteArray(), File(sub, "b.txt").readBytes())
    }

    private fun assertRoundTrip(algorithm: FileEncryptionAlgorithm, kdf: FileKdfAlgorithm) {
        val data = "round-trip-data".toByteArray()
        val encrypted = java.io.ByteArrayOutputStream()
        FileCrypto.encrypt(data.inputStream(), encrypted, "secret".toCharArray(), algorithm, kdf, "file.txt")
        val decrypted = java.io.ByteArrayOutputStream()
        val header = FileCrypto.decrypt(encrypted.toByteArray().inputStream(), decrypted, "secret".toCharArray())
        assertArrayEquals(data, decrypted.toByteArray())
        assertEquals("file.txt", header.originalName)
        assertEquals(algorithm, header.algorithm)
        assertEquals(kdf, header.kdf)
    }

    private fun ByteArray.indexOfSubsequence(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) return -1
        return (0..size - needle.size).firstOrNull { offset ->
            needle.indices.all { index -> this[offset + index] == needle[index] }
        } ?: -1
    }

    private infix fun Byte.xor(other: Int): Byte = (toInt() xor other).toByte()
}

