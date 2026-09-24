// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.archiver

import com.wisso.libarchive.ArchiveEntry
import com.wisso.libarchive.Archive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadArchiveCompressedSizeSourceTest {
    @Test
    fun mapsKnownBlocksToCompressedSizeBytes() {
        val stat = ArchiveEntry.StructStat().apply { stBlocks = 4 }

        assertEquals(2048L, ReadArchive.compressedSizeFromStat(stat))
    }

    @Test
    fun mapsMissingOrInvalidBlocksToNull() {
        assertNull(ReadArchive.compressedSizeFromStat(null))
        assertNull(ReadArchive.compressedSizeFromStat(ArchiveEntry.StructStat().apply { stBlocks = 0 }))
        assertNull(ReadArchive.compressedSizeFromStat(ArchiveEntry.StructStat().apply { stBlocks = -1 }))
    }

    @Test
    fun mapsOverflowingBlocksToNull() {
        val stat = ArchiveEntry.StructStat().apply { stBlocks = Long.MAX_VALUE }

        assertNull(ReadArchive.compressedSizeFromStat(stat))
    }

    @Test
    fun backendFallbackUsesEntrySizeForKnownUnfilteredContainerFormats() {
        assertEquals(
            123L,
            ReadArchive.compressedSizeFromBackend(
                stat = null,
                entryType = com.wisso.wizefiles.provider.common.PosixFileType.REGULAR_FILE,
                entrySize = 123L,
                archiveFormat = Archive.FORMAT_CPIO_SVR4_CRC,
                primaryFilterCode = Archive.FILTER_NONE
            )
        )
    }

    @Test
    fun backendFallbackReturnsNullWhenFormatMetadataCannotProvideCompressedSize() {
        assertNull(
            ReadArchive.compressedSizeFromBackend(
                stat = null,
                entryType = com.wisso.wizefiles.provider.common.PosixFileType.REGULAR_FILE,
                entrySize = 123L,
                archiveFormat = Archive.FORMAT_ZIP,
                primaryFilterCode = Archive.FILTER_GZIP
            )
        )
    }
}
