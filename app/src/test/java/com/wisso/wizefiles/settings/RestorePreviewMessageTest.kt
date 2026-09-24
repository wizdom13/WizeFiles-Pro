// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePreviewMessageTest {
    @Test
    fun `summary is unchanged without warnings`() {
        assertEquals("Apply: 3", formatRestorePreviewMessage("Apply: 3", emptyList()))
    }

    @Test
    fun `warnings are listed once below summary`() {
        val message = formatRestorePreviewMessage(
            summary = "Apply: 3\nSkipped: 2\nWarnings: 1",
            warnings = listOf("Some settings were skipped.", "Some settings were skipped.")
        )

        assertTrue(message.startsWith("Apply: 3\nSkipped: 2\nWarnings: 1\n\n"))
        assertEquals(1, message.lines().count { it == "• Some settings were skipped." })
    }
}
