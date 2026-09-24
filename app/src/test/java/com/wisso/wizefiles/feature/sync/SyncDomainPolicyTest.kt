// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.storage.SyncDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncDomainPolicyTest {
    @Test fun `same provider distinct endpoints remain valid`() {
        val plan = SyncDomainPolicy.validate(profile("file:///source", "file:///destination"))
        assertEquals(SyncDirection.PUSH, plan.direction)
    }

    @Test fun `identical endpoints are rejected before scanning`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncDomainPolicy.validate(profile("file:///same", "file:///same"))
        }
    }

    @Test fun `baseline tracked two way deletion is not treated as mirror deletion`() {
        val plan = SyncDomainPolicy.validate(
            profile("file:///a", "file:///b").copy(
                mode = SyncMode.TWO_WAY,
                propagateDeletions = true
            )
        )
        assertEquals(false, plan.deleteExtraneous)
    }

    private fun profile(source: String, destination: String) = SyncProfile(
        name = "test",
        sourceUri = source,
        destinationUri = destination,
        mode = SyncMode.UPDATE_DESTINATION
    )
}
