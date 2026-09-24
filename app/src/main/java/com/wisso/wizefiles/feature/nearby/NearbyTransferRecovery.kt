package com.wisso.wizefiles.feature.nearby

import com.wisso.wizefiles.feature.transfer.TransferResumePolicy
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.storage.NearbyDurableOperationState
import com.wisso.wizefiles.storage.NearbyPayloadRecovery
import com.wisso.wizefiles.storage.NearbyPayloadRecoveryPolicy

/** Reconciles durable transfer state when a service resumes or reconnects. */
internal class NearbyTransferRecovery(private val store: NearbySessionStore) {
    fun load(operationId: String): NearbySessionSnapshot? = store.load(operationId)

    fun findReceive(operationId: String, sessionId: String): NearbySessionSnapshot? =
        operationId.takeIf(String::isNotBlank)?.let(store::load)
            ?.takeIf { it.role == NearbyRole.RECEIVE && it.sessionId == sessionId }

    fun reconcilePayloads(
        snapshot: NearbySessionSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
        maxPendingIncoming: Int
    ): NearbyPayloadRecovery {
        val state = when (TransferRepository.operation(snapshot.operationId)?.state) {
            null -> NearbyDurableOperationState.MISSING
            TransferOperationState.CANCELLED -> NearbyDurableOperationState.CANCELLED
            TransferOperationState.COMPLETED, TransferOperationState.FAILED -> NearbyDurableOperationState.TERMINAL
            else -> NearbyDurableOperationState.ACTIVE
        }
        return NearbyPayloadRecoveryPolicy.restore(
            snapshot.payloadCheckpoints,
            state,
            snapshot.updatedAtMillis,
            nowMillis,
            maxPendingIncoming
        )
    }

    fun markRunning(operationId: String) {
        val operation = TransferRepository.operation(operationId) ?: return
        runCatching {
            TransferResumePolicy.transitionsFrom(operation.state).forEach {
                TransferRepository.transition(operationId, it)
            }
        }
    }
}
