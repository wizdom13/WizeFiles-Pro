// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import com.wisso.wizefiles.storage.path.RawAppPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearbyTransferSelectionTest {
    private val destination = RawAppPath("file:/storage/emulated/0/Documents")

    @Test
    fun `picked destination uses the current service offer`() {
        val pendingOffer = offer("pending")
        val serviceOffer = offer("service")

        val result = resolvePickedDestinationOffer(destination, pendingOffer, serviceOffer)

        assertEquals(destination, result?.first)
        assertEquals(serviceOffer, result?.second)
    }

    @Test
    fun `picked destination falls back to the pending offer`() {
        val pendingOffer = offer("pending")

        val result = resolvePickedDestinationOffer(destination, pendingOffer, null)

        assertEquals(destination, result?.first)
        assertEquals(pendingOffer, result?.second)
    }

    @Test
    fun `picked destination is rejected when the destination or offer is missing`() {
        assertNull(resolvePickedDestinationOffer(null, offer("pending"), null))
        assertNull(resolvePickedDestinationOffer(destination, null, null))
    }

    private fun offer(operationId: String) = NearbyOffer(
        operationId = operationId,
        entries = listOf(
            NearbyManifestEntry(
                id = "item",
                relativePath = "file.txt",
                directory = false,
                sizeBytes = 1,
                modifiedMillis = 0,
                fingerprint = ""
            )
        ),
        totalBytes = 1
    )
}
