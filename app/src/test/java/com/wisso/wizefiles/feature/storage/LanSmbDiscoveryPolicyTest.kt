// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSmbDiscoveryPolicyTest {

    @Test
    fun `subnet scan covers every host exactly once`() {
        val octets = LanSmbDiscoveryPolicy.hostOctets().toList()

        assertEquals(256, octets.size)
        assertEquals((0..255).toSet(), octets.toSet())
    }

    @Test
    fun `mobile discovery concurrency stays bounded`() {
        assertTrue(LanSmbDiscoveryPolicy.MAX_CONCURRENT_PROBES in 4..16)
    }
}
