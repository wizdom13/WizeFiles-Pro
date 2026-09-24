// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDeadlinePolicyTest {
    @Test
    fun `deadline expires exactly at boundary`() {
        val deadline = NearbyDeadlinePolicy.deadline(nowMillis = 1_000, durationMillis = 500)

        assertFalse(NearbyDeadlinePolicy.isExpired(1_499, deadline))
        assertTrue(NearbyDeadlinePolicy.isExpired(1_500, deadline))
    }

    @Test
    fun `overflow is rejected instead of wrapping`() {
        assertThrows(ArithmeticException::class.java) {
            NearbyDeadlinePolicy.deadline(Long.MAX_VALUE, 1)
        }
    }
}
