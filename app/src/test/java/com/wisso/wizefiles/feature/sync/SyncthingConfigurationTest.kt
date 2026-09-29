// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncthingConfigurationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store() = SyncthingConfiguration(SyncthingJsonStore(java.io.File(temporary.root, "config.json")))

    @Test fun migrationIsIdempotentAndRemovedPeersStayRemovedAfterRestart() {
        store().migrate("photos", "A")
        store().saveDevice(SyncthingDevice("B", "Laptop", listOf("tcp://192.168.1.2:22000")))
        store().setPeers("photos", listOf("A", "B"))
        store().migrate("notes", "A")
        store().removeDevice("A")
        store().migrate("photos", "A")
        store().migrate("notes", "A")
        assertEquals(listOf("B"), store().devices().map { it.id })
        assertEquals(listOf("B"), store().peers("photos").map { it.id })
        assertTrue(store().peers("notes").isEmpty())
    }

    @Test fun renamePreservesSharesAndFolderPauseIsIndependent() {
        store().migrate("photos", "A")
        store().migrate("notes", "A")
        store().saveDevice(SyncthingDevice("A", "Desktop"))
        store().setPaused("photos", true)
        assertEquals("Desktop", store().peers("notes").single().name)
        assertTrue(store().isPaused("photos"))
        assertFalse(store().isPaused("notes"))
        assertThrows(IllegalArgumentException::class.java) { store().setPeers("photos", listOf("missing")) }
        assertEquals(listOf("A"), store().peers("photos").map { it.id })
    }

    @Test fun invalidAddressesAreRejected() {
        listOf("https://example.com", "tcp://user:password@example.com", "tcp://", "bad").forEach {
            assertThrows(IllegalArgumentException::class.java) { SyncthingDevice("A", addresses = listOf(it)) }
        }
    }
}
