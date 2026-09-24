// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
class VaultMutationSafetyTest {
    private lateinit var context: Context
    private lateinit var writer: FaultInjectingWriter
    private lateinit var repository: VaultRepository
    private lateinit var manager: VaultManager
    private lateinit var vaultId: String
    private lateinit var key: ByteArray

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        writer = FaultInjectingWriter()
        repository = VaultRepository(context, writer)
        manager = VaultManager(context, repository)
        vaultId = "mutation-test-${UUID.randomUUID()}"
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
    fun replacementIndexFailureKeepsOriginalFileAndRemovesNewObject() {
        val original = "original".toByteArray()
        manager.importFile(vaultId, null, "note.txt", original).getOrThrow()
        val entry = manager.listEntries(vaultId, null).single()
        val originalObjectId = requireNotNull(entry.objectId)

        writer.failNextIndexWrite = true
        assertTrue(manager.replaceFile(vaultId, entry.id, "replacement".toByteArray()).isFailure)

        assertArrayEquals(original, manager.readFile(vaultId, entry.id))
        assertEquals(setOf(originalObjectId), committedObjectIds())
    }

    @Test
    fun deletionIndexFailureKeepsEntryAndObject() {
        val original = "keep me".toByteArray()
        manager.importFile(vaultId, null, "note.txt", original).getOrThrow()
        val entry = manager.listEntries(vaultId, null).single()
        val originalObjectId = requireNotNull(entry.objectId)

        writer.failNextIndexWrite = true
        assertTrue(manager.deleteEntry(vaultId, entry.id).isFailure)

        assertEquals(listOf(entry.id), manager.listEntries(vaultId, null).map(VaultEntry::id))
        assertArrayEquals(original, manager.readFile(vaultId, entry.id))
        assertEquals(setOf(originalObjectId), committedObjectIds())
    }

    @Test
    fun successfulReplacementCommitsNewObjectBeforeDeletingOldObject() {
        manager.importFile(vaultId, null, "note.txt", "original".toByteArray()).getOrThrow()
        val entry = manager.listEntries(vaultId, null).single()
        val originalObjectId = requireNotNull(entry.objectId)
        val replacement = "replacement".toByteArray()

        manager.replaceFile(vaultId, entry.id, replacement).getOrThrow()

        val updatedEntry = manager.listEntries(vaultId, null).single()
        assertArrayEquals(replacement, manager.readFile(vaultId, updatedEntry.id))
        assertFalse(originalObjectId in committedObjectIds())
        assertEquals(setOf(requireNotNull(updatedEntry.objectId)), committedObjectIds())
    }

    @Test
    fun concurrentImportsBothPersist() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val imports = listOf("one.txt", "two.txt").map { name ->
                executor.submit {
                    ready.countDown()
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    manager.importFile(vaultId, null, name, name.toByteArray()).getOrThrow()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            imports.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(setOf("one.txt", "two.txt"), manager.listEntries(vaultId, null).map(VaultEntry::name).toSet())
        assertEquals(2, committedObjectIds().size)
    }

    private fun committedObjectIds(): Set<String> =
        repository.objectsDir(vaultId).listFiles().orEmpty()
            .filter(File::isFile)
            .map(File::getName)
            .filterNot { it.endsWith(".new") || it.endsWith(".bak") }
            .toSet()

    private class FaultInjectingWriter : VaultAtomicWriter {
        var failNextIndexWrite = false

        override fun write(file: File, block: (OutputStream) -> Unit) {
            AndroidVaultAtomicWriter.write(file) { output ->
                block(output)
                if (failNextIndexWrite && file.name == "index.bin") {
                    failNextIndexWrite = false
                    throw IOException("Injected index commit failure")
                }
            }
        }
    }
}
