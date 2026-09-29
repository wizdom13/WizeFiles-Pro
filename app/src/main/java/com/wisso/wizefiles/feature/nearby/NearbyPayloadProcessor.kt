// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

/** Validates and correlates LAN payload callbacks before they reach service policy. */
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
    val callback: NearbyPayloadCallback = object : NearbyPayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: NearbyPayload) {
            when (payload.type) {
                NearbyPayload.Type.BYTES -> runCatching {
                    handleControl(NearbyProtocol.decode(requireNotNull(payload.asBytes())))
                }.onFailure { cancel("Invalid control message") }
                NearbyPayload.Type.STREAM -> {
                    // This transport orders FILE_BEGIN before its stream. A missing entry was
                    // cleared by pause/cancel; discard its late stream even after a quick resume.
                    if (!store.hasIncomingMetadata(payload.id)) {
                        cancelPayload(payload.id)
                        payload.markCanceled()
                        runCatching { payload.asStream()?.asInputStream()?.close() }
                        return
                    }
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

        override fun onPayloadTransferUpdate(endpointId: String, update: NearbyPayloadUpdate) {
            // The file writer owns incoming completion, including flush and final rename.
            // A transport EOF must not release its active correlation early.
            val item = store.outgoingItem(update.payloadId) ?: return
            if (update.totalBytes > 0) outgoingProgress(item)
            when (update.status) {
                NearbyPayloadUpdate.Status.SUCCESS,
                NearbyPayloadUpdate.Status.CANCELED -> {
                    store.finishOutgoing(update.payloadId)
                    correlationChanged()
                }
                NearbyPayloadUpdate.Status.FAILURE -> {
                    store.finishOutgoing(update.payloadId)
                    correlationChanged()
                    fail("File stream was interrupted", true)
                }
            }
        }
    }
}
