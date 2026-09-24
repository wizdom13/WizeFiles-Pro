// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class GridLayoutPolicyTest {
    @Test
    fun `automatic policy keeps icons balanced across phone and tablet widths`() {
        assertEquals(2, GridLayoutPolicy.automaticSpanCount(0))
        assertEquals(2, GridLayoutPolicy.automaticSpanCount(359))
        assertEquals(3, GridLayoutPolicy.automaticSpanCount(360))
        assertEquals(3, GridLayoutPolicy.automaticSpanCount(399))
        assertEquals(4, GridLayoutPolicy.automaticSpanCount(400))
        assertEquals(4, GridLayoutPolicy.automaticSpanCount(411))
        assertEquals(4, GridLayoutPolicy.automaticSpanCount(599))
        assertEquals(5, GridLayoutPolicy.automaticSpanCount(600))
        assertEquals(5, GridLayoutPolicy.automaticSpanCount(640))
        assertEquals(5, GridLayoutPolicy.automaticSpanCount(719))
        assertEquals(6, GridLayoutPolicy.automaticSpanCount(720))
        assertEquals(6, GridLayoutPolicy.automaticSpanCount(900))
    }

    @Test
    fun `compact and wide overrides are independent`() {
        val overrides = GridColumnOverrides()
            .withValue(GridWidthClass.COMPACT, 3)
            .withValue(GridWidthClass.WIDE, 4)

        assertEquals(3, GridLayoutPolicy.spanCount(411, overrides))
        assertEquals(4, GridLayoutPolicy.spanCount(820, overrides))
    }

    @Test
    fun `automatic and invalid overrides fall back to adaptive policy`() {
        val overrides = GridColumnOverrides(compact = 1, wide = 7)

        assertEquals(4, GridLayoutPolicy.spanCount(411, overrides))
        assertEquals(6, GridLayoutPolicy.spanCount(820, overrides))
    }
}
