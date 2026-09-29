// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserTabSessionStoreTest {
    private var disk: String? = null
    private fun newStore() = BrowserTabSessionStore({ disk }, { disk = it })

    @Test
    fun freshStoreRestoresEveryFolderSelectedTabAndPaneLayout() {
        val expected = BrowserTabSession(
            listOf(
                BrowserTabSnapshot("Internal", BrowserTabLocation("/storage/emulated/0/Documents")),
                BrowserTabSnapshot(
                    "External", BrowserTabLocation("/storage/1234-ABCD/Photos"),
                    BrowserTabLocation("Camera/été", 42L), true, BrowserPane.SECONDARY, 0.6f
                )
            ),
            activeIndex = 1
        )

        newStore().save(expected)

        assertEquals(expected, newStore().load())
    }

    @Test
    fun laterSaveReplacesClosedTabsAndUpdatesNavigatedFolders() {
        newStore().save(BrowserTabSession(listOf(tab("Internal"), tab("External")), 1))
        val remaining = BrowserTabSession(
            listOf(BrowserTabSnapshot("Downloads", BrowserTabLocation("/storage/emulated/0/Download"))),
            0
        )

        newStore().save(remaining)

        assertEquals(remaining, newStore().load())
    }

    @Test
    fun unsupportedLocationKeepsItsTabAndOtherLocations() {
        val expected = BrowserTabSession(
            listOf(tab("Temporary location"), BrowserTabSnapshot("External", BrowserTabLocation("/storage/ABCD"))),
            0
        )
        newStore().save(expected)

        assertEquals(expected, newStore().load())
        assertNull(newStore().load()!!.tabs.first().primaryLocation)
    }

    @Test
    fun missingCorruptEmptyAndUnknownVersionSessionsAreIgnored() {
        listOf(null, "broken", "{}", "{\"version\":1,\"tabs\":[]}",
            "{\"version\":2,\"tabs\":[{\"title\":\"Files\"}]}").forEach {
            disk = it
            assertNull(newStore().load())
        }
    }

    @Test
    fun restoredSessionLimitsTabsAndRepairsSelectionAndLayout() {
        disk = """{"version":1,"activeIndex":99,"tabs":[${
            (0..14).joinToString(",") {
                """{"title":"$it","activePane":99,"divider":5} """
            }
        }]}"""

        val restored = newStore().load()!!

        assertEquals(BrowserTabsController.MAXIMUM_TAB_COUNT, restored.tabs.size)
        assertEquals(restored.tabs.lastIndex, restored.activeIndex)
        assertEquals(BrowserPane.PRIMARY, restored.tabs.first().activePane)
        assertEquals(0.75f, restored.tabs.first().dividerFraction)
    }

    private fun tab(title: String) = BrowserTabSnapshot(title, null)
}
