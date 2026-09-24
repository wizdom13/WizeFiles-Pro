// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.archiver

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveReaderSanitizationTest {
    @Test
    fun `sanitizeArchiveEntryName rejects traversal-like names`() {
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            ArchiveReader.sanitizeArchiveEntryName("../outside/file.txt", false)
        }
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            ArchiveReader.sanitizeArchiveEntryName("folder/./sub/../file.txt", false)
        }
    }

    @Test
    fun `sanitizeArchiveEntryName rejects root file and keeps root directory`() {
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            ArchiveReader.sanitizeArchiveEntryName(".", false)
        }
        assertEquals("", ArchiveReader.sanitizeArchiveEntryName(".", true))
    }
}
