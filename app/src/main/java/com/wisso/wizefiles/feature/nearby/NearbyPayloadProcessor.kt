package com.wisso.wizefiles.feature.nearby

import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate

/** Validates and correlates Google Nearby payload callbacks before they reach service policy. */
internal class NearbyPayloadProcessor(
    private val store: NearbyPayloadStore,
    private val cancelPayload: (Long) -> Unit,
    private val handleControl: (NearbyMessage) -> Unit,
    private val consumeIncoming: (Long) -> Unit,
    private val outgoingProgress: (com.wisso.wizefiles.feature.transfer.TransferItemRecord) -> Unit,
    private val correlationChanged: () -> Unit,
    private val fail: (String, Boolean) -> Unit,
    private val cancel: (String) -> Unit
) {
    val callback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> runCatching {
                    handleControl(NearbyProtocol.decode(requireNotNull(payload.asBytes())))
                }.onFailure { cancel("Invalid control message") }
                Payload.Type.STREAM -> {
                    if (store.acceptIncoming(payload)) {
                        correlationChanged()
                        consumeIncoming(payload.id)
                    } else {
                        cancelPayload(payload.id)
                        cancel("Unexpected or duplicate incoming stream")
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.totalBytes > 0) store.outgoingItem(update.payloadId)?.let(outgoingProgress)
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS,
                PayloadTransferUpdate.Status.CANCELED -> {
                    store.finishOutgoing(update.payloadId)
                    correlationChanged()
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    store.finishOutgoing(update.payloadId)
                    correlationChanged()
                    fail("File stream was interrupted", true)
                }
            }
        }
    }
}
