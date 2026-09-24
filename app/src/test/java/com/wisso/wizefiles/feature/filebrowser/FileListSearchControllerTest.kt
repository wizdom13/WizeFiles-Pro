// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileListSearchControllerTest {
    @Test
    fun `query normalization trims usable queries`() {
        assertEquals("reports", FileListSearchController.normalizedQuery("  reports  "))
    }

    @Test
    fun `query normalization rejects empty and one-character queries`() {
        assertNull(FileListSearchController.normalizedQuery("  "))
        assertNull(FileListSearchController.normalizedQuery(" x "))
    }
}
