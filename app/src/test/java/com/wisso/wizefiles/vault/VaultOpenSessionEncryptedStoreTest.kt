// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.files.mime.MimeType
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VaultOpenSessionEncryptedStoreTest {
    private lateinit var context: Context
    private lateinit var session: VaultOpenSession
    private lateinit var key: ByteArray

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        session = VaultOpenSession(
            sessionId = UUID.randomUUID().toString(),
            vaultId = "vault-${UUID.randomUUID()}",
            entryId = "entry-${UUID.randomUUID()}",
            displayName = "large.bin",
            mimeType = MimeType("application/octet-stream"),
            originalSize = 0L,
            originalModifiedAt = 1L,
            openedAt = System.currentTimeMillis()
        )
        key = ByteArray(32) { index -> (index * 3).toByte() }
    }

    @After
    fun tearDown() {
        VaultOpenSessionEncryptedStore.delete(context, session.sessionId)
        VaultCrypto.zero(key)
    }

    @Test
    fun encryptedChunkStoreSupportsCrossChunkWritesTruncationAndSparseGrowth() {
        val original = ByteArray(VaultOpenSessionEncryptedStore.CHUNK_SIZE_BYTES * 2 + 137) { index ->
            (index * 31).toByte()
        }
        val expectedAfterPatch = original.copyOf()
        val patch = ByteArray(97) { index -> (255 - index).toByte() }
        val patchOffset = VaultOpenSessionEncryptedStore.CHUNK_SIZE_BYTES - 23
        System.arraycopy(patch, 0, expectedAfterPatch, patchOffset, patch.size)

        val store = VaultOpenSessionEncryptedStore.create(context, session, key)
        store.openReplacingOutputStream().use { output -> output.write(original) }
        assertEquals(original.size.toLong(), store.size())
        assertEquals(patch.size, store.write(patchOffset.toLong(), patch.size, patch))
        assertArrayEquals(expectedAfterPatch, store.openInputStream().use { it.readBytes() })

        val truncatedSize = VaultOpenSessionEncryptedStore.CHUNK_SIZE_BYTES + 211
        store.truncate(truncatedSize.toLong())
        val sparseOffset = truncatedSize + 4096
        val sparsePatch = byteArrayOf(9, 8, 7, 6)
        store.write(sparseOffset.toLong(), sparsePatch.size, sparsePatch)
        val sparseExpected = expectedAfterPatch.copyOf(sparseOffset + sparsePatch.size)
        sparseExpected.fill(0, truncatedSize, sparseOffset)
        System.arraycopy(sparsePatch, 0, sparseExpected, sparseOffset, sparsePatch.size)
        assertArrayEquals(sparseExpected, store.openInputStream().use { it.readBytes() })

        store.markPendingRecovery()
        store.close()

        val reopened = requireNotNull(VaultOpenSessionEncryptedStore.open(context, session, key))
        assertArrayEquals(sparseExpected, reopened.openInputStream().use { it.readBytes() })
        reopened.close()
        assertTrue(
            VaultOpenSessionEncryptedStore.pendingSessions(context, session.vaultId)
                .any { it.sessionId == session.sessionId }
        )
        assertEncryptedAtRest(original)
    }

    private fun assertEncryptedAtRest(plaintext: ByteArray) {
        val chunks = File(
            context.noBackupFilesDir,
            "vault_open_stores/${session.sessionId}/chunks"
        ).listFiles().orEmpty()
        assertTrue(chunks.isNotEmpty())
        val stored = chunks.first().readBytes()
        val plaintextPrefix = plaintext.copyOfRange(0, 32)
        assertFalse(stored.containsSequence(plaintextPrefix))
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
        if (sequence.isEmpty() || sequence.size > size) return false
        for (start in 0..size - sequence.size) {
            var matches = true
            for (offset in sequence.indices) {
                if (this[start + offset] != sequence[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }
}
