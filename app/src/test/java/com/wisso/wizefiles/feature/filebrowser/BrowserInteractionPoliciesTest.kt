// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.By
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.Order
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserInteractionPoliciesTest {
    @Test fun `selection reducer is immutable and deterministic`() {
        val original = linkedSetOf("a", "b")
        val selected = BrowserSelectionReducer.reduce(original, setOf("b", "c"), true)
        assertEquals(setOf("a", "b", "c"), selected)
        assertEquals(setOf("a", "b"), original)
        assertEquals(setOf("a"), BrowserSelectionReducer.reduce(original, setOf("b"), false))
    }

    @Test fun `sort dialog policy bounds restored grid columns`() {
        val initial = BrowserSortDialogPolicy.initial(
            FileViewType.GRID,
            FileSortOptions(By.SIZE, Order.DESCENDING, true),
            pathSpecific = true,
            gridColumns = Int.MAX_VALUE
        )
        assertEquals(GridColumnOverrides.AUTO, BrowserSortDialogPolicy.normalized(initial).gridColumns)
    }
}
