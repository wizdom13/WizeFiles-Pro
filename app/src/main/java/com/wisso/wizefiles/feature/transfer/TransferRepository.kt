package com.wisso.wizefiles.feature.transfer

import com.wisso.wizefiles.provider.archive.editor.ArchiveMutationStore
import com.wisso.wizefiles.feature.apksigning.AabSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.ApkSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecretRegistry
import com.wisso.wizefiles.feature.apksigning.ApksSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.ApkmImportOperationStore
import com.wisso.wizefiles.feature.apksigning.XapkSigningOperationStore
import com.wisso.wizefiles.feature.filejobs.DeleteOperationStore

internal object TransferRepository {
    fun enqueue(spec: TransferOperationSpec): TransferOperationRecord =
        TransferDatabase.insertOperation(spec)

    fun operation(id: String): TransferOperationRecord? = TransferDatabase.operation(id)

    fun sourceUris(id: String): List<String> = TransferDatabase.sourceUris(id)

    fun activeAndQueued(): List<TransferOperationRecord> = TransferDatabase.operations(
        setOf(
            TransferOperationState.QUEUED,
            TransferOperationState.PLANNING,
            TransferOperationState.RUNNING,
            TransferOperationState.PAUSE_REQUESTED,
            TransferOperationState.PAUSED,
            TransferOperationState.WAITING_FOR_USER,
            TransferOperationState.RECOVERABLE
        )
    )

    fun history(): List<TransferOperationRecord> = TransferDatabase.operations(
        setOf(
            TransferOperationState.COMPLETED,
            TransferOperationState.COMPLETED_WITH_WARNINGS,
            TransferOperationState.FAILED,
            TransferOperationState.CANCELLED
        )
    )

    fun transition(
        id: String,
        state: TransferOperationState,
        reason: String = "",
        errorCategory: String = "",
        errorMessage: String = "",
        requiresUserAction: Boolean = false
    ): TransferOperationRecord = TransferDatabase.transition(
        id,
        state,
        reason,
        errorCategory,
        errorMessage,
        requiresUserAction
    )

    fun reorderQueued(operationIds: List<String>) = TransferDatabase.reorderQueued(operationIds)

    fun removeQueued(id: String): Boolean {
        val operation = operation(id)
        val removed = TransferDatabase.deleteQueued(id)
        if (removed && operation?.type == TransferOperationType.DELETE) {
            DeleteOperationStore.delete(id)
        }
        return removed
    }

    fun recoverInterrupted(): Int = TransferDatabase.recoverInterrupted()

    fun updatePlanSummary(id: String, totalItems: Long, totalBytes: Long) =
        TransferDatabase.updatePlanSummary(id, totalItems, totalBytes)

    fun checkpoint(checkpoint: TransferProgressCheckpoint) =
        TransferDatabase.checkpoint(checkpoint)

    fun items(id: String): List<TransferItemRecord> = TransferDatabase.items(id)

    fun retryFailedItems(id: String): Int = TransferDatabase.retryFailedItems(id)

    fun clearCompletedHistory(): Int = clearHistory(
        setOf(
            TransferOperationState.COMPLETED,
            TransferOperationState.COMPLETED_WITH_WARNINGS
        )
    )

    fun clearFailedHistory(): Int = clearHistory(
        setOf(
            TransferOperationState.FAILED,
            TransferOperationState.CANCELLED
        )
    )

    private fun clearHistory(states: Set<TransferOperationState>): Int = history()
        .filter { it.state in states }
        .count { deleteHistory(it.id) }

    fun deleteHistory(id: String): Boolean {
        val operation = operation(id)
        val deleted = TransferDatabase.deleteHistory(id)
        if (!deleted) return false
        when (operation?.type) {
            TransferOperationType.DELETE -> DeleteOperationStore.delete(id)
            TransferOperationType.ARCHIVE_MODIFY -> ArchiveMutationStore.delete(id)
            TransferOperationType.APK_SIGN -> {
                ApkSigningSecretRegistry.clear(id)
                ApkSigningOperationStore.delete(id)
            }
            TransferOperationType.AAB_SIGN -> {
                ApkSigningSecretRegistry.clear(id)
                AabSigningOperationStore.delete(id)
            }
            TransferOperationType.APKS_SIGN -> {
                ApkSigningSecretRegistry.clear(id)
                ApksSigningOperationStore.delete(id)
            }
            TransferOperationType.XAPK_SIGN -> {
                ApkSigningSecretRegistry.clear(id)
                XapkSigningOperationStore.delete(id)
            }
            TransferOperationType.APKM_IMPORT -> ApkmImportOperationStore.delete(id)
            else -> Unit
        }
        return true
    }

    fun beginDecision(
        operationId: String,
        itemId: Long,
        type: String,
        payload: String,
        availableResponses: String
    ): Long {
        val decisionId = TransferDatabase.insertDecision(
            operationId,
            itemId,
            type,
            payload,
            availableResponses
        )
        transition(operationId, TransferOperationState.WAITING_FOR_USER, requiresUserAction = true)
        return decisionId
    }

    fun resolveDecision(operationId: String, decisionId: Long, response: String, applyToAll: Boolean) {
        TransferDatabase.resolveDecision(decisionId, response, applyToAll)
        transition(operationId, TransferOperationState.RUNNING)
    }

    fun pendingDecisions(id: String): List<PendingTransferDecision> =
        TransferDatabase.pendingDecisions(id)
}
