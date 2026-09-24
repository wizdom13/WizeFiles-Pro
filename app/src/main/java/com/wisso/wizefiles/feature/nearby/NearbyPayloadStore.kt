package com.wisso.wizefiles.feature.nearby

import com.google.android.gms.nearby.connection.Payload
import com.wisso.wizefiles.feature.transfer.TransferItemRecord
import com.wisso.wizefiles.storage.NearbyPayloadLedger
import com.wisso.wizefiles.storage.NearbyPayloadCheckpoint
import java.io.Closeable

/** Owns payload correlation and stream resources for one transport session. */
internal class NearbyPayloadStore(private val maxPendingIncoming: Int) : Closeable {
    private val ledger = NearbyPayloadLedger(maxPendingIncoming)
    private val incomingPayloads = mutableMapOf<Long, Payload>()
    private val incomingMetadata = mutableMapOf<Long, NearbyIncomingStream>()
    private val outgoingStreams = mutableMapOf<Long, Closeable>()
    private val outgoingItems = mutableMapOf<Long, TransferItemRecord>()

    val activePayloadId: Long? get() = ledger.activePayloadId
    fun checkpoints(): List<NearbyPayloadCheckpoint> = ledger.checkpoints()

    fun acceptIncoming(payload: Payload): Boolean {
        if (!ledger.acceptIncomingPayload(payload.id)) return false
        incomingPayloads[payload.id] = payload
        return true
    }

    fun acceptMetadata(metadata: NearbyIncomingStream) {
        require(ledger.acceptIncomingMetadata(metadata.payloadId)) {
            "Duplicate, active, or excessive incoming stream metadata"
        }
        incomingMetadata[metadata.payloadId] = metadata
    }

    fun claimIncoming(payloadId: Long): Pair<Payload, NearbyIncomingStream>? {
        val payload = incomingPayloads[payloadId] ?: return null
        val metadata = incomingMetadata[payloadId] ?: return null
        require(activePayloadId == null) { "Overlapping incoming streams are forbidden" }
        check(ledger.claimIncoming(payloadId)) { "Payload correlation state is inconsistent" }
        incomingPayloads.remove(payloadId)
        incomingMetadata.remove(payloadId)
        return payload to metadata
    }

    fun trackOutgoing(payload: Payload, stream: Closeable, item: TransferItemRecord) {
        require(ledger.trackOutgoing(payload.id)) { "Overlapping or duplicate outgoing stream" }
        outgoingStreams[payload.id] = stream
        outgoingItems[payload.id] = item
    }

    fun outgoingItem(payloadId: Long): TransferItemRecord? = outgoingItems[payloadId]

    fun finishOutgoing(payloadId: Long) {
        outgoingStreams.remove(payloadId)?.let { runCatching(it::close) }
        outgoingItems.remove(payloadId)
        ledger.finish(payloadId)
    }

    fun clearActive(): Long? = ledger.completeIncoming()

    fun completeIncoming() {
        ledger.completeIncoming()
    }

    override fun close() {
        outgoingStreams.values.forEach { runCatching(it::close) }
        outgoingStreams.clear()
        outgoingItems.clear()
        incomingPayloads.clear()
        incomingMetadata.clear()
        ledger.clear()
    }
}
