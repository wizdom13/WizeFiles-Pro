// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferProgressTrackerTest {
    @Test
    fun `speed is smoothed and eta uses remaining known bytes`() {
        var now = 0L
        val tracker = TransferProgressTracker { now }

        assertEquals(TransferSpeedSnapshot(0, -1), tracker.sample("copy", 0, 10_000))
        now = 1_000
        assertEquals(TransferSpeedSnapshot(2_000, 4), tracker.sample("copy", 2_000, 10_000))
        now = 2_000
        assertEquals(TransferSpeedSnapshot(2_000, 3), tracker.sample("copy", 4_000, 10_000))
    }

    @Test
    fun `reset removes paused time from the next speed window`() {
        var now = 0L
        val tracker = TransferProgressTracker { now }
        tracker.sample("move", 100, 1_000)
        now = 1_000
        tracker.sample("move", 200, 1_000)
        tracker.reset("move")
        now = 100_000

        assertEquals(TransferSpeedSnapshot(0, -1), tracker.sample("move", 200, 1_000))
    }
}
