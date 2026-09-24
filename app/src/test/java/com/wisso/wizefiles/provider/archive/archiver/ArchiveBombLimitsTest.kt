// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.archiver

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArchiveBombLimitsTest {
    @Test
    fun abortsWhenTotalUncompressedBytesExceeded() {
        val state = ArchiveEntryValidator.LimitsState()
        repeat(4) { ArchiveEntryValidator.checkArchiveBombLimits(state, 1024L * 1024 * 1024) }
        assertThrows(IOException::class.java) { ArchiveEntryValidator.checkArchiveBombLimits(state, 1) }
    }

    @Test
    fun abortsWhenPerEntryLimitExceeded() {
        val state = ArchiveEntryValidator.LimitsState()
        assertThrows(IOException::class.java) {
            ArchiveEntryValidator.checkArchiveBombLimits(state, 1024L * 1024 * 1024 + 1)
        }
    }

    @Test
    fun abortsWhenEntryCountExceeded() {
        val state = ArchiveEntryValidator.LimitsState()
        repeat(10_000) { ArchiveEntryValidator.checkArchiveBombLimits(state, 0) }
        assertThrows(IOException::class.java) { ArchiveEntryValidator.checkArchiveBombLimits(state, 0) }
    }

    @Test
    fun abortsWhenExpansionRatioExceeded() {
        val state = ArchiveEntryValidator.LimitsState()
        assertThrows(IOException::class.java) { ArchiveEntryValidator.checkArchiveBombLimits(state, 10_000, 1) }
    }

    @Test
    fun failsClosedWhenCompressedSizeIsZeroAndUncompressedIsNonZero() {
        val state = ArchiveEntryValidator.LimitsState()
        assertThrows(IOException::class.java) { ArchiveEntryValidator.checkArchiveBombLimits(state, 1, 0) }
    }

    @Test
    fun treatsNegativeCompressedSizeAsUnknownAndStillEnforcesOtherLimits() {
        val state = ArchiveEntryValidator.LimitsState()
        repeat(4) { ArchiveEntryValidator.checkArchiveBombLimits(state, 1024L * 1024 * 1024, -1) }
        assertThrows(IOException::class.java) { ArchiveEntryValidator.checkArchiveBombLimits(state, 1, -1) }
    }

    @Test
    fun overflowRiskCompressedSizeDoesNotOverflowAndKeepsOtherLimitsActive() {
        val state = ArchiveEntryValidator.LimitsState()
        ArchiveEntryValidator.checkArchiveBombLimits(state, 1024, Long.MAX_VALUE)
        assertEquals(1, state.entryCount)
        assertEquals(1024, state.totalUncompressedBytes)
    }
}
