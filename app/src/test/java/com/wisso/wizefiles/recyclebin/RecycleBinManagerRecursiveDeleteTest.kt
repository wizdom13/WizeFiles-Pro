package com.wisso.wizefiles.recyclebin

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecycleBinManagerRecursiveDeleteTest {

    @Test
    fun moveToRecycleBin_preservesDirectoryTree_and_deleteAllPermanently_removesItRecursively() {
        val sandboxRoot = Files.createTempDirectory("recycle-bin-test")
        val recycleBinRoot = sandboxRoot.resolve("recycle-bin")
        val sourceDirectory = sandboxRoot.resolve("source-dir")
        val nestedDirectory = sourceDirectory.resolve("nested")
        Files.createDirectories(nestedDirectory)
        Files.write(sourceDirectory.resolve("top.txt"), "top".toByteArray(StandardCharsets.UTF_8))
        Files.write(nestedDirectory.resolve("child.txt"), "child".toByteArray(StandardCharsets.UTF_8))

        RecycleBinManager.moveToRecycleBin(sourceDirectory, recycleBinRoot)

        assertFalse(Files.exists(sourceDirectory))
        val entries = RecycleBinManager.listEntries(recycleBinRoot)
        assertEquals(1, entries.size)
        val recycledDirectory = entries.single().path
        assertTrue(Files.exists(recycledDirectory.resolve("top.txt")))
        assertTrue(Files.exists(recycledDirectory.resolve("nested").resolve("child.txt")))

        val summary = RecycleBinManager.deleteAllPermanently(recycleBinRoot)

        assertEquals(1, summary.successCount)
        assertTrue(summary.failures.isEmpty())
        assertFalse(Files.exists(recycledDirectory))
        assertFalse(Files.exists(recycledDirectory.resolve("nested")))
    }
    @Test
    fun defaultTrashBinRoot_usesNewDirectoryName() {
        assertEquals(
            ".wizefiles_trash_bin",
            RecycleBinManager.recycleBinRootPath.fileName.toString()
        )
        assertNotEquals(
            RecycleBinManager.recycleBinRootPath,
            RecycleBinManager.legacyRecycleBinRootPath
        )
    }

    @Test
    fun migrateLegacyRecycleBin_preservesMetadataAndRestoresOriginalFile() {
        val sandboxRoot = Files.createTempDirectory("trash-bin-migration-test")
        val legacyRoot = sandboxRoot.resolve(".wizefiles_recycle_bin")
        val newRoot = sandboxRoot.resolve(".wizefiles_trash_bin")
        val original = sandboxRoot.resolve("original.txt")
        val expected = "preserved"
        Files.write(original, expected.toByteArray(StandardCharsets.UTF_8))
        RecycleBinManager.moveToRecycleBin(original, legacyRoot)

        RecycleBinManager.migrateLegacyRecycleBin(legacyRoot, newRoot)

        assertFalse(Files.exists(legacyRoot))
        val migratedEntry = RecycleBinManager.listEntries(newRoot).single()
        assertEquals(original.toString(), migratedEntry.metadata.originalPath)
        RecycleBinManager.restore(migratedEntry.path, newRoot)
        assertEquals(expected, String(Files.readAllBytes(original), StandardCharsets.UTF_8))
    }

    @Test
    fun migrateLegacyRecycleBin_renamesCollidingPayloadWithoutLosingEitherEntry() {
        val sandboxRoot = Files.createTempDirectory("trash-bin-collision-test")
        val legacyRoot = sandboxRoot.resolve(".wizefiles_recycle_bin")
        val newRoot = sandboxRoot.resolve(".wizefiles_trash_bin")
        val legacySource = sandboxRoot.resolve("legacy").resolve("same.txt")
        val newSource = sandboxRoot.resolve("new").resolve("same.txt")
        Files.createDirectories(legacySource.parent)
        Files.createDirectories(newSource.parent)
        Files.write(legacySource, "legacy".toByteArray(StandardCharsets.UTF_8))
        Files.write(newSource, "new".toByteArray(StandardCharsets.UTF_8))
        RecycleBinManager.moveToRecycleBin(legacySource, legacyRoot)
        RecycleBinManager.moveToRecycleBin(newSource, newRoot)

        RecycleBinManager.migrateLegacyRecycleBin(legacyRoot, newRoot)

        val entries = RecycleBinManager.listEntries(newRoot)
        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.path.fileName.toString() }.toSet().size)
        assertTrue(entries.any { it.metadata.originalPath == legacySource.toString() })
        assertTrue(entries.any { it.metadata.originalPath == newSource.toString() })
    }
}
