// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LargeFileScannerTest {
    @Test
    fun thresholdFiltersSmallFiles() {
        val dir = createTempDirectory().toFile()
        val small = File(dir, "small.bin").apply { writeBytes(ByteArray(8)) }
        val big = File(dir, "big.bin").apply { writeBytes(ByteArray(128)) }
        val candidates = LargeFileScanner().scan(
            listOf(small, big),
            ScanFilters(minLargeFileBytes = 32)
        )
        assertEquals(1, candidates.size)
        assertEquals(big.path, candidates.first().path)
    }
}
