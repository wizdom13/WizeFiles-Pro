// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProfilesPickerFlowTest {

    @Test
    fun destinationPickerLaunch_requiresPendingSourceResumedStateAndWindowFocus() {
        assertTrue(
            shouldLaunchPendingDestinationPicker(
                hasPendingLaunch = true,
                hasPendingSource = true,
                isResumed = true,
                hasWindowFocus = true
            )
        )

        assertFalse(
            shouldLaunchPendingDestinationPicker(
                hasPendingLaunch = false,
                hasPendingSource = true,
                isResumed = true,
                hasWindowFocus = true
            )
        )
        assertFalse(
            shouldLaunchPendingDestinationPicker(
                hasPendingLaunch = true,
                hasPendingSource = false,
                isResumed = true,
                hasWindowFocus = true
            )
        )
        assertFalse(
            shouldLaunchPendingDestinationPicker(
                hasPendingLaunch = true,
                hasPendingSource = true,
                isResumed = false,
                hasWindowFocus = true
            )
        )
        assertFalse(
            shouldLaunchPendingDestinationPicker(
                hasPendingLaunch = true,
                hasPendingSource = true,
                isResumed = true,
                hasWindowFocus = false
            )
        )
    }
}
