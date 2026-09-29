// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import com.wisso.wizefiles.feature.transfer.TransferItemRecord
import com.wisso.wizefiles.storage.NearbyPayloadLedger
import com.wisso.wizefiles.storage.NearbyPayloadCheckpoint
import java.io.Closeable

/** Owns payload correlation and stream resources for one transport session. */
internal class NearbyPayloadStore(private val maxPendingIncoming: Int) : Closeable {
    private val ledger = NearbyPayloadLedger(maxPendingIncoming)
    private val incomingPayloads = mutableMapOf<Long, NearbyPayload>()
    private var activeIncoming: NearbyPayload? = null
    private val incomingMetadata = mutableMapOf<Long, NearbyIncomingStream>()
    private val outgoingStreams = mutableMapOf<Long, Closeable>()
    private val outgoingItems = mutableMapOf<Long, TransferItemRecord>()

    val activePayloadId: Long? @Synchronized get() = ledger.activePayloadId
    @Synchronized fun checkpoints(): List<NearbyPayloadCheckpoint> = ledger.checkpoints()
    @Synchronized fun hasIncomingMetadata(payloadId: Long): Boolean = payloadId in incomingMetadata

    @Synchronized fun acceptIncoming(payload: NearbyPayload): Boolean {
        if (!ledger.acceptIncomingPayload(payload.id)) return false
        incomingPayloads[payload.id] = payload
        return true
    }

    @Synchronized fun acceptMetadata(metadata: NearbyIncomingStream) {
        require(ledger.acceptIncomingMetadata(metadata.payloadId)) {
            "Duplicate, active, or excessive incoming stream metadata"
        }
        incomingMetadata[metadata.payloadId] = metadata
    }

    @Synchronized fun claimIncoming(payloadId: Long): Pair<NearbyPayload, NearbyIncomingStream>? {
        val payload = incomingPayloads[payloadId] ?: return null
        val metadata = incomingMetadata[payloadId] ?: return null
        require(activePayloadId == null) { "Overlapping incoming streams are forbidden" }
        check(ledger.claimIncoming(payloadId)) { "Payload correlation state is inconsistent" }
        incomingPayloads.remove(payloadId)
        activeIncoming = payload
        incomingMetadata.remove(payloadId)
        return payload to metadata
    }

    @Synchronized fun trackOutgoing(payload: NearbyPayload, stream: Closeable, item: TransferItemRecord) {
        require(ledger.trackOutgoing(payload.id)) { "Overlapping or duplicate outgoing stream" }
        outgoingStreams[payload.id] = stream
        outgoingItems[payload.id] = item
    }

    @Synchronized fun outgoingItem(payloadId: Long): TransferItemRecord? = outgoingItems[payloadId]

    @Synchronized fun finishOutgoing(payloadId: Long) {
        outgoingStreams.remove(payloadId)?.let { runCatching(it::close) }
        outgoingItems.remove(payloadId)
        ledger.finish(payloadId)
    }

    @Synchronized fun clearActive(): Long? {
        activeIncoming?.let { payload ->
            payload.markCanceled()
            runCatching { payload.asStream()?.asInputStream()?.close() }
        }
        activeIncoming = null
        return ledger.completeIncoming()
    }

    @Synchronized fun completeIncoming(payloadId: Long) {
        if (activeIncoming?.id != payloadId) return
        activeIncoming = null
        ledger.finish(payloadId)
    }

    @Synchronized override fun close() {
        clearActive()
        outgoingStreams.values.forEach { runCatching(it::close) }
        outgoingStreams.clear()
        outgoingItems.clear()
        incomingPayloads.values.forEach { runCatching { it.asStream()?.asInputStream()?.close() } }
        incomingPayloads.clear()
        incomingMetadata.clear()
        ledger.clear()
    }
}
