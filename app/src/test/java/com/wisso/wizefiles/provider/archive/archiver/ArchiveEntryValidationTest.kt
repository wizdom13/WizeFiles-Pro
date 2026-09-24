// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.archiver

import java.io.IOException
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveEntryValidationTest {
    @Test
    fun rejectsTraversalEntries() {
        assertThrows(IOException::class.java) { ArchiveEntryValidator.sanitizeEntryName("../evil.txt", false) }
        assertThrows(IOException::class.java) { ArchiveEntryValidator.sanitizeEntryName("dir/../../evil.txt", false) }
    }

    @Test
    fun rejectsAbsolutePaths() {
        assertThrows(IOException::class.java) { ArchiveEntryValidator.sanitizeEntryName("/absolute/path.txt", false) }
        assertThrows(IOException::class.java) { ArchiveEntryValidator.sanitizeEntryName("C:\\absolute\\path.txt", false) }
        assertThrows(IOException::class.java) { ArchiveEntryValidator.sanitizeEntryName("\\\\server\\share\\evil.txt", false) }
    }

    @Test
    fun rejectsNulAndEmptyNames() {
        assertThrows(IOException::class.java) { ArchiveEntryValidator.sanitizeEntryName("bad\u0000name.txt", false) }
        assertThrows(IOException::class.java) { ArchiveEntryValidator.sanitizeEntryName("   ", false) }
    }

    @Test
    fun validatesSafeNestedPath() {
        assertEquals("folder/file.txt", ArchiveEntryValidator.sanitizeEntryName("folder/file.txt", false))
    }

    @Test
    fun duplicateKeyNormalizesCase() {
        val key1 = ArchiveEntryValidator.duplicateKey(java.nio.file.Paths.get("Folder/File.txt"))
        val key2 = ArchiveEntryValidator.duplicateKey(java.nio.file.Paths.get("folder/file.txt"))
        assertTrue(key1 == key2)
    }

    @Test
    fun rejectsHostileSymlinkTargetsAndExcessiveNesting() {
        listOf("../outside", "/absolute", "C:/drive", "a\\b", "a//b", "a/./b", "a/\u0000b")
            .forEach { assertTrue(!ArchiveEntryValidator.validateSymlinkTarget(it)) }
        val deep = List(129) { "d" }.joinToString("/") + "/file"
        assertThrows(IOException::class.java) { ArchiveEntryValidator.sanitizeEntryName(deep, false) }
    }

    @Test
    fun deterministicHostileNameFuzzHasOnlyBoundedParserFailures() {
        val random = Random(0x41524348L)
        repeat(10_000) {
            val chars = CharArray(random.nextInt(512)) { random.nextInt(0x10000).toChar() }
            val failure = runCatching {
                ArchiveEntryValidator.sanitizeEntryName(String(chars), false)
            }.exceptionOrNull()
            assertTrue(failure == null || failure is IOException)
        }
    }

    @Test
    fun archiveBombAccountingRejectsOverflowAndExtremeExpansion() {
        val state = ArchiveEntryValidator.LimitsState(totalUncompressedBytes = Long.MAX_VALUE - 1)
        assertThrows(IOException::class.java) {
            ArchiveEntryValidator.checkArchiveBombLimits(state, 2, 1)
        }
        assertThrows(IOException::class.java) {
            ArchiveEntryValidator.checkArchiveBombLimits(ArchiveEntryValidator.LimitsState(), 201, 1)
        }
    }
}
