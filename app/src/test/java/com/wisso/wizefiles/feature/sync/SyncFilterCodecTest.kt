// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncFilterCodecTest {
    @Test
    fun orderedConfigurationRoundTripsWithoutEnablingSymlinks() {
        val rules = SyncFilterRules(
            includeHidden = true,
            includeSymlinks = false,
            excludedExtensions = linkedSetOf("tmp", "part"),
            excludedPathPrefixes = linkedSetOf("cache", "private")
        )
        val restored = SyncFilterCodec.decode(SyncFilterCodec.encode(rules))
        assertEquals(rules, restored)
        assertFalse(restored.includeSymlinks)
    }
}
