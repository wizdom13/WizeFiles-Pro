// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserProcessRestorationPolicyTest {
    @Test
    fun `process recreation never persists paths grants credentials or selections`() {
        val keys = BrowserProcessRestorationPolicy.durableKeys()
        assertFalse(BrowserProcessRestorationPolicy.restoresNavigationTrail)
        assertFalse(BrowserProcessRestorationPolicy.restoresSelection)
        assertTrue(keys.isNotEmpty())
        assertTrue(keys.none { key ->
            listOf("path", "uri", "grant", "credential", "password", "selection")
                .any { key.contains(it, ignoreCase = true) }
        })
    }

    @Test
    fun `process recreation actively removes unsafe legacy state`() {
        val handle = SavedStateHandle(
            mapOf(
                BrowserProcessRestorationPolicy.SEARCH_QUERY to "report",
                "browser.currentPath" to "smb://user:secret@server/share",
                "browser.selection" to listOf("content://provider/private"),
                "browser.uriGrant" to true
            )
        )

        BrowserProcessRestorationPolicy.sanitize(handle)

        assertEquals(setOf(BrowserProcessRestorationPolicy.SEARCH_QUERY), handle.keys())
        assertEquals("report", handle.get<String>(BrowserProcessRestorationPolicy.SEARCH_QUERY))
    }
}
