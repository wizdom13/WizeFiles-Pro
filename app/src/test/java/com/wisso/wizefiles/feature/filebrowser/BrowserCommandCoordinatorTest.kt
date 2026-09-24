// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCommandCoordinatorTest {
    @Test
    fun `unavailable command does not invoke its effect`() {
        var copied = false
        val coordinator = coordinator(
            context = BrowserCommandContext(0, false, false, false, true, false, true, true),
            copy = { copied = true }
        )

        assertFalse(coordinator.execute(BrowserCommand.COPY))
        assertFalse(copied)
    }

    @Test
    fun `available command invokes exactly the routed effect`() {
        var copied = false
        val coordinator = coordinator(
            context = BrowserCommandContext(1, false, false, false, true, false, true, true),
            copy = { copied = true }
        )

        assertTrue(coordinator.execute(BrowserCommand.COPY))
        assertTrue(copied)
    }

    private fun coordinator(
        context: BrowserCommandContext,
        copy: () -> Unit
    ) = BrowserCommandCoordinator(
        context = { context },
        copy = copy,
        cut = {},
        paste = {},
        delete = {},
        rename = {},
        copyToOtherPane = {},
        moveToOtherPane = {}
    )
}
