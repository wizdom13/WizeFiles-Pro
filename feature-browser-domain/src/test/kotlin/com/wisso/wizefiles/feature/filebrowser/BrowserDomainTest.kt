// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDomainTest {
    @Test fun `destructive commands require a mutable selection`() {
        val context = BrowserCommandContext(1, false, false, false, false, false, true, true)
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.DELETE, context))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.RENAME, context))
        assertTrue(BrowserCommandPolicy.isAvailable(BrowserCommand.COPY, context))
        assertTrue(BrowserCommandPlanner.create(BrowserCommand.DELETE, context) == null)
        assertTrue(BrowserCommandPlanner.create(BrowserCommand.COPY, context) != null)
    }

    @Test fun `single item actions enforce cardinality without Android menu ids`() {
        val properties = BrowserSelectionAction.Direct(BrowserDirectSelectionAction.PROPERTIES)
        assertFalse(BrowserSelectionPolicy.isAvailable(properties, 0))
        assertTrue(BrowserSelectionPolicy.isAvailable(properties, 1))
        assertFalse(BrowserSelectionPolicy.isAvailable(properties, 2))
        assertTrue(BrowserSelectionPolicy.isAvailable(
            BrowserSelectionAction.Direct(BrowserDirectSelectionAction.SELECT_ALL), 0
        ))
    }
}
