// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.archiver

import com.wisso.wizefiles.provider.common.PosixFileMode
import com.wisso.wizefiles.provider.common.PosixFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArchiveReaderBombRatioRuntimeTest {
    @Test
    fun runtimePathRejectsUnsafeRatioWhenCompressedSizeIsKnown() {
        val entry = archiveEntry(size = 10_000, compressedSize = 1)
        val state = ArchiveEntryValidator.LimitsState()

        assertThrows(java.io.IOException::class.java) {
            ArchiveReader.enforceBombLimitsForEntry(entry, state)
        }
    }

    @Test
    fun runtimePathAllowsReasonableRatioWhenCompressedSizeIsKnown() {
        val entry = archiveEntry(size = 1_000, compressedSize = 100)
        val state = ArchiveEntryValidator.LimitsState()

        ArchiveReader.enforceBombLimitsForEntry(entry, state)
        assertEquals(1, state.entryCount)
        assertEquals(1_000, state.totalUncompressedBytes)
    }

    @Test
    fun runtimePathFallsBackToOtherBombLimitsWhenCompressedSizeUnknown() {
        val state = ArchiveEntryValidator.LimitsState()
        repeat(4) {
            ArchiveReader.enforceBombLimitsForEntry(
                archiveEntry(size = 1024L * 1024 * 1024, compressedSize = null),
                state
            )
        }

        assertThrows(java.io.IOException::class.java) {
            ArchiveReader.enforceBombLimitsForEntry(archiveEntry(size = 1, compressedSize = null), state)
        }
    }

    @Test
    fun runtimePathStillRejectsPerEntryLimitWhenCompressedSizeUnknown() {
        val state = ArchiveEntryValidator.LimitsState()
        assertThrows(java.io.IOException::class.java) {
            ArchiveReader.enforceBombLimitsForEntry(
                archiveEntry(size = 1024L * 1024 * 1024 + 1, compressedSize = null),
                state
            )
        }
    }

    private fun archiveEntry(size: Long, compressedSize: Long?): ReadArchive.Entry =
        ReadArchive.Entry(
            name = "folder/file.txt",
            isEncrypted = false,
            lastModifiedTime = null,
            lastAccessTime = null,
            creationTime = null,
            type = PosixFileType.REGULAR_FILE,
            size = size,
            owner = null,
            group = null,
            mode = PosixFileMode.FILE_DEFAULT,
            symbolicLinkTarget = null,
            compressedSize = compressedSize
        )
}
