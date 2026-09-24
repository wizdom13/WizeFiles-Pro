// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Paths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrowserSessionStateTest {

    @Before
    fun setUp() {
        BrowserSessionState.resetForTests()
    }

    @After
    fun tearDown() {
        BrowserSessionState.resetForTests()
    }

    @Test
    fun coldExternalOpenReturnsToDefaultDirectory() {
        val fallback = Paths.get("/storage/emulated/0")
        val previous = Paths.get("/storage/emulated/0/Download")

        assertFalse(BrowserSessionState.registerBrowserActivity())
        BrowserSessionState.recordDirectory(previous)

        assertEquals(
            fallback,
            BrowserSessionState.returnDirectory(hadExistingBrowser = false, fallback = fallback)
        )
    }

    @Test
    fun externalOpenWithExistingBrowserReturnsToLastDirectory() {
        val fallback = Paths.get("/storage/emulated/0")
        val previous = Paths.get("/storage/emulated/0/Documents")

        assertFalse(BrowserSessionState.registerBrowserActivity())
        BrowserSessionState.recordDirectory(previous)
        assertTrue(BrowserSessionState.registerBrowserActivity())

        assertEquals(
            previous,
            BrowserSessionState.returnDirectory(hadExistingBrowser = true, fallback = fallback)
        )
    }

    @Test
    fun closingLastBrowserDiscardsSessionDirectory() {
        val fallback = Paths.get("/storage/emulated/0")
        val previous = Paths.get("/storage/emulated/0/Documents")

        BrowserSessionState.registerBrowserActivity()
        BrowserSessionState.recordDirectory(previous)
        BrowserSessionState.unregisterBrowserActivity()

        assertFalse(BrowserSessionState.hasActiveBrowserActivity())
        assertEquals(
            fallback,
            BrowserSessionState.returnDirectory(hadExistingBrowser = true, fallback = fallback)
        )
    }
}
