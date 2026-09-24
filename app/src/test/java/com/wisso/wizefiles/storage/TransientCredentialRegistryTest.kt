// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransientCredentialRegistryTest {
    private data class Server(val authority: String, val secret: String)

    @Test
    fun temporaryCredentialOverridesStoredCredentialUntilRemoved() {
        val stored = Server("host", "stored")
        val temporary = Server("host", "temporary")
        val registry = TransientCredentialRegistry(Server::authority) { listOf(stored) }

        assertEquals("stored", registry.find("host")?.secret)
        registry.add(temporary)
        assertEquals("temporary", registry.find("host")?.secret)
        registry.remove(temporary)
        assertEquals("stored", registry.find("host")?.secret)
        assertNull(registry.find("missing"))
    }
}
