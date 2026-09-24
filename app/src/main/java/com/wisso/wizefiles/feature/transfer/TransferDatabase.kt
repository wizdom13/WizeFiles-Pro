package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection
import com.wisso.wizefiles.feature.sync.SyncAction
import com.wisso.wizefiles.feature.sync.SyncActionState
import com.wisso.wizefiles.feature.sync.SyncProfile
import com.wisso.wizefiles.feature.sync.SyncRun
import com.wisso.wizefiles.feature.sync.SyncRunState
import com.wisso.wizefiles.feature.sync.SyncSide
import com.wisso.wizefiles.feature.sync.SyncSnapshotEntry
import com.wisso.wizefiles.feature.sync.SyncPlanSummary

internal object TransferDatabase {

    fun insertOperation(spec: TransferOperationSpec): TransferOperationRecord =
        TransferOperationDao.insertOperation(spec)

    fun operation(id: String): TransferOperationRecord? =
        TransferOperationDao.operation(id)

    fun sourceUris(operationId: String): List<String> =
        TransferOperationDao.sourceUris(operationId)

    fun operations(
        states: Set<TransferOperationState>? = null
    ): List<TransferOperationRecord> = TransferOperationDao.operations(states)

    fun transition(
        operationId: String,
        state: TransferOperationState,
        reason: String = "",
        errorCategory: String = "",
        errorMessage: String = "",
        requiresUserAction: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): TransferOperationRecord = TransferOperationDao.transition(
        operationId = operationId,
        state = state,
        reason = reason,
        errorCategory = errorCategory,
        errorMessage = errorMessage,
        requiresUserAction = requiresUserAction,
        nowMillis = nowMillis
    )

    fun reorderQueued(operationIds: List<String>) =
        TransferOperationDao.reorderQueued(operationIds)

    fun deleteQueued(operationId: String): Boolean =
        TransferOperationDao.deleteQueued(operationId)

    fun recoverInterrupted(nowMillis: Long = System.currentTimeMillis()): Int =
        TransferOperationDao.recoverInterrupted(nowMillis)

    fun updatePlanSummary(operationId: String, totalItems: Long, totalBytes: Long) =
        TransferOperationDao.updatePlanSummary(operationId, totalItems, totalBytes)

    fun beginItem(
        operationId: String,
        sourceUri: String,
        targetUri: String,
        relativePath: String,
        isDirectory: Boolean,
        sizeBytes: Long,
        modifiedMillis: Long,
        sourceFingerprint: String
    ): TransferItemRecord = TransferItemDao.beginItem(
        operationId = operationId,
        sourceUri = sourceUri,
        targetUri = targetUri,
        relativePath = relativePath,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        modifiedMillis = modifiedMillis,
        sourceFingerprint = sourceFingerprint
    )

    fun checkpoint(checkpoint: TransferProgressCheckpoint) =
        TransferItemDao.checkpoint(checkpoint)

    fun setTemporaryTarget(itemId: Long, temporaryTargetUri: String) =
        TransferItemDao.setTemporaryTarget(itemId, temporaryTargetUri)

    fun completeItem(
        itemId: Long,
        resultUri: String,
        nowMillis: Long = System.currentTimeMillis()
    ) = TransferItemDao.completeItem(itemId, resultUri, nowMillis)

    fun skipItem(itemId: Long, nowMillis: Long = System.currentTimeMillis()) =
        TransferItemDao.skipItem(itemId, nowMillis)

    fun failItem(
        itemId: Long,
        errorCategory: String,
        errorMessage: String,
        nowMillis: Long = System.currentTimeMillis()
    ) = TransferItemDao.failItem(itemId, errorCategory, errorMessage, nowMillis)

    fun items(operationId: String): List<TransferItemRecord> =
        TransferItemDao.items(operationId)

    fun retryFailedItems(operationId: String): Int =
        TransferItemDao.retryFailedItems(operationId)

    fun deleteHistory(operationId: String): Boolean =
        TransferOperationDao.deleteHistory(operationId)

    fun pruneHistory(
        olderThanMillis: Long,
        keepLatest: Int = 500
    ): Int = TransferOperationDao.pruneHistory(olderThanMillis, keepLatest)

    fun insertDecision(
        operationId: String,
        itemId: Long,
        type: String,
        payload: String,
        availableResponses: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long = TransferDecisionDao.insertDecision(
        operationId = operationId,
        itemId = itemId,
        type = type,
        payload = payload,
        availableResponses = availableResponses,
        nowMillis = nowMillis
    )

    fun pendingDecisions(operationId: String): List<PendingTransferDecision> =
        TransferDecisionDao.pendingDecisions(operationId)

    fun resolveDecision(decisionId: Long, response: String, applyToAll: Boolean) =
        TransferDecisionDao.resolveDecision(decisionId, response, applyToAll)

    fun abandonPendingDecisions(operationId: String) =
        TransferDecisionDao.abandonPendingDecisions(operationId)

    fun upsertSyncProfile(profile: SyncProfile) =
        SyncProfileDao.upsertSyncProfile(profile)

    fun syncProfile(profileId: String): SyncProfile? =
        SyncProfileDao.syncProfile(profileId)

    fun syncProfiles(): List<SyncProfile> =
        SyncProfileDao.syncProfiles()

    fun deleteSyncProfile(profileId: String): Boolean =
        SyncProfileDao.deleteSyncProfile(profileId)

    fun insertSyncRun(run: SyncRun): SyncRun =
        SyncRunDao.insertSyncRun(run)

    fun syncRun(runId: String): SyncRun? =
        SyncRunDao.syncRun(runId)

    fun syncRuns(profileId: String): List<SyncRun> =
        SyncRunDao.syncRuns(profileId)

    fun syncRunForTransfer(operationId: String): SyncRun? =
        SyncRunDao.syncRunForTransfer(operationId)

    fun resetRunningSyncActions(runId: String) =
        SyncActionDao.resetRunningSyncActions(runId)

    fun retryFailedSyncActions(runId: String) =
        SyncActionDao.retryFailedSyncActions(runId)

    fun reconcileInterruptedSyncRuns(): Int =
        SyncRunDao.reconcileInterruptedSyncRuns()

    fun transitionSyncRun(
        runId: String,
        state: SyncRunState,
        safetyBlockReason: String = "",
        transferOperationId: String = "",
        safetyBlockDetails: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): SyncRun = SyncRunDao.transitionSyncRun(
        runId = runId,
        state = state,
        safetyBlockReason = safetyBlockReason,
        safetyBlockDetails = safetyBlockDetails,
        transferOperationId = transferOperationId,
        nowMillis = nowMillis
    )

    fun markSyncRunPreviewReady(runId: String, summary: SyncPlanSummary): SyncRun =
        SyncRunDao.markSyncRunPreviewReady(runId, summary)

    fun insertSyncSnapshotEntries(entries: List<SyncSnapshotEntry>) =
        SyncSnapshotDao.insertSyncSnapshotEntries(entries)

    fun syncSnapshotEntries(
        profileId: String,
        generation: Long,
        side: SyncSide
    ): List<SyncSnapshotEntry> =
        SyncSnapshotDao.syncSnapshotEntries(profileId, generation, side)

    fun commitSyncBaseline(
        runId: String,
        sourceEntries: List<SyncSnapshotEntry>,
        destinationEntries: List<SyncSnapshotEntry>,
        nowMillis: Long = System.currentTimeMillis()
    ): SyncRun = SyncSnapshotDao.commitSyncBaseline(
        runId = runId,
        sourceEntries = sourceEntries,
        destinationEntries = destinationEntries,
        nowMillis = nowMillis
    )

    fun replaceSyncActions(runId: String, actions: List<SyncAction>) =
        SyncActionDao.replaceSyncActions(runId, actions)

    fun syncActions(runId: String): List<SyncAction> =
        SyncActionDao.syncActions(runId)

    fun updateSyncAction(
        actionId: Long,
        state: SyncActionState,
        transferItemId: Long = 0,
        protectedResultUri: String = "",
        errorMessage: String = ""
    ) = SyncActionDao.updateSyncAction(
        actionId = actionId,
        state = state,
        transferItemId = transferItemId,
        protectedResultUri = protectedResultUri,
        errorMessage = errorMessage
    )

    fun rewriteSyncConflict(action: SyncAction) =
        SyncActionDao.rewriteSyncConflict(action)

    fun skipUnusedConflictProtection(
        runId: String,
        relativePath: String,
        retainedSide: SyncSide?
    ) = SyncActionDao.skipUnusedConflictProtection(runId, relativePath, retainedSide)

    fun clearForTests() = TransferDatabaseConnection.clearForTests()

    fun closeForTests() = TransferDatabaseConnection.closeForTests()

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T =
        TransferDatabaseConnection.withConnection(block)

}
