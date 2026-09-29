// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class NearbyPayloadProcessorTest {
    @Test fun lateStreamAfterPauseCannotBlockTheResumedStream() {
        val store = NearbyPayloadStore(2)
        val canceled = mutableListOf<Long>()
        val consumed = mutableListOf<Long>()
        val processor = NearbyPayloadProcessor(store, { canceled.add(it) }, {}, { consumed.add(it) }, {}, {},
            { _, _ -> fail("Unexpected failure") }, { fail(it) })
        val old = NearbyPayload.fromStream(ByteArrayInputStream(byteArrayOf(1)), 1)
        store.acceptMetadata(NearbyIncomingStream("session", "item", old.id, 0, 1))
        store.close() // Pause clears metadata before the transport delivers the stream callback.
        processor.callback.onPayloadReceived("sender", old)
        assertTrue(old.isCanceled)
        assertEquals(listOf(old.id), canceled)
        assertTrue(store.checkpoints().isEmpty())
        val resumed = NearbyPayload.fromStream(ByteArrayInputStream(byteArrayOf(2)), 1)
        store.acceptMetadata(NearbyIncomingStream("session", "item", resumed.id, 0, 1))
        processor.callback.onPayloadReceived("sender", resumed)
        assertEquals(listOf(resumed.id), consumed)
        assertNotNull(store.claimIncoming(resumed.id))
        store.close()
    }

    @Test fun transportEofCannotCompleteAFileStillBeingSaved() {
        val store = NearbyPayloadStore(2)
        val payload = NearbyPayload.fromStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3)
        val metadata = NearbyIncomingStream("session", "item", payload.id, 0, 3)
        store.acceptMetadata(metadata)
        assertTrue(store.acceptIncoming(payload))
        assertNotNull(store.claimIncoming(payload.id))
        val processor = NearbyPayloadProcessor(store, {}, {}, {}, {}, {}, { _, _ -> fail("Unexpected failure") }, { fail(it) })
        processor.callback.onPayloadTransferUpdate("sender", NearbyPayloadUpdate(payload.id, 3, NearbyPayloadUpdate.Status.SUCCESS))
        assertEquals(payload.id, store.activePayloadId)
        store.completeIncoming(payload.id)
        assertNull(store.activePayloadId)
        store.close()
    }
    @Test fun canceledWriterCannotClearTheNextResumedStream() {
        val store = NearbyPayloadStore(2)
        fun claim(): NearbyPayload {
            val payload = NearbyPayload.fromStream(ByteArrayInputStream(byteArrayOf(1)), 1)
            store.acceptMetadata(NearbyIncomingStream("session", "item", payload.id, 0, 1))
            assertTrue(store.acceptIncoming(payload))
            assertNotNull(store.claimIncoming(payload.id))
            return payload
        }
        val canceled = claim()
        store.clearActive()
        assertTrue(canceled.isCanceled)
        val resumed = claim()
        store.completeIncoming(canceled.id)
        assertEquals(resumed.id, store.activePayloadId)
        assertFalse(resumed.isCanceled)
        store.close()
        assertTrue(resumed.isCanceled)
    }

}
