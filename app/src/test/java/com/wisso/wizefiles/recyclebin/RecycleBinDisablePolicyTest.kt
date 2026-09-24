// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.recyclebin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecycleBinDisablePolicyTest {

    @Test
    fun shouldDisableImmediatelyOnlyWhenRecycleBinIsEmpty() {
        assertTrue(RecycleBinDisablePolicy.shouldDisableImmediately(hasContents = false))
        assertFalse(RecycleBinDisablePolicy.shouldDisableImmediately(hasContents = true))
    }

    @Test
    fun canDisableAfterClearOnlyWhenThereAreNoFailures() {
        assertTrue(
            RecycleBinDisablePolicy.canDisableAfterClear(
                RecycleBinOperationSummary(successCount = 2, failures = emptyList())
            )
        )
        assertFalse(
            RecycleBinDisablePolicy.canDisableAfterClear(
                RecycleBinOperationSummary(successCount = 1, failures = listOf("sample.txt: denied"))
            )
        )
    }
}
