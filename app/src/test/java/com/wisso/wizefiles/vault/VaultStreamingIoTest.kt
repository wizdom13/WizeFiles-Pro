// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VaultStreamingIoTest {
    private lateinit var context: Context
    private lateinit var repository: VaultRepository
    private lateinit var manager: VaultManager
    private lateinit var vaultId: String
    private lateinit var key: ByteArray

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = VaultRepository(context)
        manager = VaultManager(context, repository)
        vaultId = "stream-test-${UUID.randomUUID()}"
        key = ByteArray(32) { index -> index.toByte() }
        repository.objectsDir(vaultId).mkdirs()
        repository.writeEncryptedIndex(vaultId, key, emptyList())
        VaultSessionManager.putUnlockedKey(vaultId, key)
    }

    @After
    fun tearDown() {
        VaultSessionManager.lock(vaultId)
        repository.deleteVault(vaultId)
    }

    @Test
    fun importReadAndReplacementStayOnStreamingApis() {
        val originalSize = 2 * 1024 * 1024 + 19
        val originalInput = PatternInputStream(originalSize, seed = 17)
        manager.importFile(vaultId, null, "large.bin", originalInput).getOrThrow()
        val entry = manager.listEntries(vaultId, null).single()

        assertTrue(originalInput.maxRequestedBytes <= DEFAULT_BUFFER_SIZE)
        assertEquals(originalSize.toLong(), entry.size)
        assertArrayEquals(
            digestOf(PatternInputStream(originalSize, seed = 17)),
            digestFromVault(entry.id)
        )

        val replacementSize = 3 * 1024 * 1024 + 73
        val replacementInput = PatternInputStream(replacementSize, seed = 91)
        manager.replaceFile(vaultId, entry.id, replacementInput).getOrThrow()
        val updated = manager.listEntries(vaultId, null).single()

        assertTrue(replacementInput.maxRequestedBytes <= DEFAULT_BUFFER_SIZE)
        assertEquals(replacementSize.toLong(), updated.size)
        assertArrayEquals(
            digestOf(PatternInputStream(replacementSize, seed = 91)),
            digestFromVault(updated.id)
        )
    }

    private fun digestFromVault(entryId: String): ByteArray {
        val output = DigestOutputStream()
        manager.copyFileTo(vaultId, entryId, output)
        return output.digest()
    }

    private fun digestOf(input: InputStream): ByteArray {
        val output = DigestOutputStream()
        input.use { it.copyTo(output) }
        return output.digest()
    }

    private class DigestOutputStream : OutputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")

        override fun write(value: Int) {
            digest.update(value.toByte())
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            digest.update(bytes, offset, length)
        }

        fun digest(): ByteArray = digest.digest()
    }

    private class PatternInputStream(
        private val totalSize: Int,
        private val seed: Int
    ) : InputStream() {
        private var position = 0
        var maxRequestedBytes = 0
            private set

        override fun read(): Int {
            if (position >= totalSize) return -1
            return valueAt(position++).toInt() and 0xff
        }

        override fun read(destination: ByteArray, offset: Int, length: Int): Int {
            if (position >= totalSize) return -1
            maxRequestedBytes = maxOf(maxRequestedBytes, length)
            val count = minOf(length, totalSize - position)
            for (index in 0 until count) {
                destination[offset + index] = valueAt(position + index)
            }
            position += count
            return count
        }

        private fun valueAt(index: Int): Byte = ((index * 37 + seed) and 0xff).toByte()
    }
}
