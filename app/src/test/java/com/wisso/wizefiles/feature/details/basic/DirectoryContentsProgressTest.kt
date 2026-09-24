// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details.basic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectoryContentsProgressTest {

    @Test
    fun `progress is throttled while retaining every item and byte`() {
        var now = 0L
        val progress = DirectoryContentsProgress(intervalMillis = 100) { now }

        now = 20
        assertNull(progress.record(includePath = true, byteSize = 4))
        now = 80
        assertNull(progress.record(includePath = true, byteSize = 6))
        now = 100
        assertEquals(
            DirectoryContentsSnapshot(count = 3, size = 15),
            progress.record(includePath = true, byteSize = 5)
        )
    }

    @Test
    fun `failed entries count as contents without inventing bytes`() {
        val progress = DirectoryContentsProgress(intervalMillis = Long.MAX_VALUE) { 0 }

        progress.record(includePath = true, byteSize = null)

        assertEquals(DirectoryContentsSnapshot(1, 0), progress.snapshot())
    }

    @Test
    fun `post-visit callbacks do not add phantom entries`() {
        val progress = DirectoryContentsProgress(intervalMillis = Long.MAX_VALUE) { 0 }

        progress.record(includePath = false, byteSize = null)

        assertEquals(DirectoryContentsSnapshot(0, 0), progress.snapshot())
    }
}
