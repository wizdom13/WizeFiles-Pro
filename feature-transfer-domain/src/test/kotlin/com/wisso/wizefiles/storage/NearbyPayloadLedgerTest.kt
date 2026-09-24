package com.wisso.wizefiles.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPayloadLedgerTest {
    @Test
    fun `metadata and stream correlate in either order and expose a durable checkpoint`() {
        val ledger = NearbyPayloadLedger(maxPendingIncoming = 2)

        assertTrue(ledger.acceptIncomingMetadata(7))
        assertTrue(ledger.acceptIncomingPayload(7))
        assertEquals(NearbyPayloadState.READY, ledger.checkpoints().single().state)
        assertTrue(ledger.claimIncoming(7))
        assertEquals(NearbyPayloadState.ACTIVE, ledger.checkpoints().single().state)
        assertEquals(7L, ledger.completeIncoming())
        assertNull(ledger.activePayloadId)
    }

    @Test
    fun `duplicates overlap and excessive pending payloads are rejected`() {
        val ledger = NearbyPayloadLedger(maxPendingIncoming = 1)

        assertTrue(ledger.acceptIncomingPayload(1))
        assertFalse(ledger.acceptIncomingPayload(1))
        assertFalse(ledger.acceptIncomingMetadata(2))
        assertTrue(ledger.acceptIncomingMetadata(1))
        assertTrue(ledger.claimIncoming(1))
        assertFalse(ledger.claimIncoming(1))
        assertFalse(ledger.trackOutgoing(2))
    }

    @Test
    fun `late completion cannot finish a different outgoing payload`() {
        val ledger = NearbyPayloadLedger(maxPendingIncoming = 1)
        assertTrue(ledger.trackOutgoing(9))

        assertFalse(ledger.finish(10))
        assertEquals(9L, ledger.activePayloadId)
        assertTrue(ledger.finish(9))
        assertNull(ledger.activePayloadId)
    }

    @Test
    fun `process recreation never restores active or ready transport correlation`() {
        val checkpoints = listOf(
            NearbyPayloadCheckpoint(1, NearbyPayloadDirection.INCOMING, NearbyPayloadState.PENDING),
            NearbyPayloadCheckpoint(2, NearbyPayloadDirection.INCOMING, NearbyPayloadState.READY),
            NearbyPayloadCheckpoint(3, NearbyPayloadDirection.INCOMING, NearbyPayloadState.ACTIVE),
            NearbyPayloadCheckpoint(4, NearbyPayloadDirection.OUTGOING, NearbyPayloadState.ACTIVE)
        )

        NearbyDurableOperationState.entries.forEach { durableState ->
            val recovery = NearbyPayloadRecoveryPolicy.restore(
                checkpoints, durableState, 1_000, 2_000, maxPendingIncoming = 2
            )
            assertNull(recovery.ledger.activePayloadId)
            assertTrue(recovery.ledger.checkpoints().isEmpty())
            assertEquals(4, recovery.discardedCheckpointCount)
        }
    }

    @Test
    fun `recreated ledger accepts fresh correlation and enforces normal limits`() {
        val recovery = NearbyPayloadRecoveryPolicy.restore(
            listOf(NearbyPayloadCheckpoint(9, NearbyPayloadDirection.INCOMING, NearbyPayloadState.ACTIVE)),
            NearbyDurableOperationState.ACTIVE,
            1_000,
            2_000,
            maxPendingIncoming = 1
        )

        assertTrue(recovery.ledger.acceptIncomingMetadata(10))
        assertFalse(recovery.ledger.acceptIncomingPayload(11))
        assertTrue(recovery.ledger.acceptIncomingPayload(10))
        assertTrue(recovery.ledger.claimIncoming(10))
        assertFalse(recovery.ledger.trackOutgoing(12))
        assertFalse(recovery.ledger.finish(9))
    }

    @Test
    fun `stale future and oversized snapshots are bounded`() {
        val oversized = (0..NearbyPayloadRecoveryPolicy.MAX_CHECKPOINTS + 20).map {
            NearbyPayloadCheckpoint(it.toLong(), NearbyPayloadDirection.OUTGOING, NearbyPayloadState.PENDING)
        }
        val stale = NearbyPayloadRecoveryPolicy.restore(
            oversized, NearbyDurableOperationState.ACTIVE, 1, NearbyPayloadRecoveryPolicy.MAX_AGE_MILLIS + 2,
            maxPendingIncoming = 1
        )
        assertTrue(stale.stale)
        assertEquals(NearbyPayloadRecoveryPolicy.MAX_CHECKPOINTS, stale.discardedCheckpointCount)

        val future = NearbyPayloadRecoveryPolicy.restore(
            emptyList(), NearbyDurableOperationState.ACTIVE, 3, 2, maxPendingIncoming = 1
        )
        assertTrue(future.stale)
    }
}
