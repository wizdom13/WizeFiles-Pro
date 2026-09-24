// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.archiver

import com.wisso.wizefiles.provider.common.PosixFileMode
import com.wisso.wizefiles.provider.common.PosixFileType
import com.wisso.libarchive.Archive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveCompressedSizeMetadataTest {
    @Test
    fun entryCarriesKnownCompressedSizeMetadata() {
        val entry = entry(compressedSize = 2048)

        assertEquals(2048L, entry.compressedSize)
    }

    @Test
    fun entryCarriesUnknownCompressedSizeMetadataAsNull() {
        val entry = entry(compressedSize = null)

        assertNull(entry.compressedSize)
    }

    @Test
    fun fallbackUsesEntrySizeForUnfilteredTarLikeFormats() {
        val compressedSize = ReadArchive.compressedSizeFromBackend(
            stat = null,
            entryType = PosixFileType.REGULAR_FILE,
            entrySize = 4096L,
            archiveFormat = Archive.FORMAT_TAR_USTAR,
            primaryFilterCode = Archive.FILTER_NONE
        )

        assertEquals(4096L, compressedSize)
    }

    @Test
    fun fallbackKeepsCompressedSizeUnknownForFilteredFormats() {
        val compressedSize = ReadArchive.compressedSizeFromBackend(
            stat = null,
            entryType = PosixFileType.REGULAR_FILE,
            entrySize = 4096L,
            archiveFormat = Archive.FORMAT_TAR_USTAR,
            primaryFilterCode = Archive.FILTER_GZIP
        )

        assertNull(compressedSize)
    }

    private fun entry(compressedSize: Long?): ReadArchive.Entry =
        ReadArchive.Entry(
            name = "file.txt",
            isEncrypted = false,
            lastModifiedTime = null,
            lastAccessTime = null,
            creationTime = null,
            type = PosixFileType.REGULAR_FILE,
            size = 1024,
            owner = null,
            group = null,
            mode = PosixFileMode.FILE_DEFAULT,
            symbolicLinkTarget = null,
            compressedSize = compressedSize
        )
}
