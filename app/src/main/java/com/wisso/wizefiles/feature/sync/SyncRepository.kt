package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.feature.transfer.TransferDatabase

internal object SyncRepository {
    fun saveProfile(profile: SyncProfile) = TransferDatabase.upsertSyncProfile(profile)

    fun profile(profileId: String): SyncProfile? = TransferDatabase.syncProfile(profileId)

    fun profiles(): List<SyncProfile> = TransferDatabase.syncProfiles()

    fun deleteProfile(profileId: String): Boolean = TransferDatabase.deleteSyncProfile(profileId)

    fun createRun(run: SyncRun): SyncRun = TransferDatabase.insertSyncRun(run)

    fun run(runId: String): SyncRun? = TransferDatabase.syncRun(runId)

    fun runs(profileId: String): List<SyncRun> = TransferDatabase.syncRuns(profileId)

    fun runForTransfer(operationId: String): SyncRun? =
        TransferDatabase.syncRunForTransfer(operationId)

    fun resetRunningActions(runId: String) = TransferDatabase.resetRunningSyncActions(runId)

    fun retryFailedActions(runId: String) = TransferDatabase.retryFailedSyncActions(runId)

    fun reconcileInterruptedRuns(): Int = TransferDatabase.reconcileInterruptedSyncRuns()

    fun transitionRun(
        runId: String,
        state: SyncRunState,
        safetyBlockReason: String = "",
        transferOperationId: String = "",
        safetyBlockDetails: String = ""
    ): SyncRun = TransferDatabase.transitionSyncRun(
        runId = runId,
        state = state,
        safetyBlockReason = safetyBlockReason,
        transferOperationId = transferOperationId,
        safetyBlockDetails = safetyBlockDetails
    )

    fun markRunPreviewReady(runId: String, summary: SyncPlanSummary): SyncRun =
        TransferDatabase.markSyncRunPreviewReady(runId, summary)

    fun addSnapshotEntries(entries: List<SyncSnapshotEntry>) =
        TransferDatabase.insertSyncSnapshotEntries(entries)

    fun snapshotEntries(profileId: String, generation: Long, side: SyncSide) =
        TransferDatabase.syncSnapshotEntries(profileId, generation, side)

    fun commitBaseline(
        runId: String,
        source: List<SyncSnapshotEntry>,
        destination: List<SyncSnapshotEntry>
    ): SyncRun = TransferDatabase.commitSyncBaseline(runId, source, destination)

    fun replaceActions(runId: String, actions: List<SyncAction>) =
        TransferDatabase.replaceSyncActions(runId, actions)

    fun actions(runId: String): List<SyncAction> = TransferDatabase.syncActions(runId)

    fun updateAction(
        actionId: Long,
        state: SyncActionState,
        transferItemId: Long = 0,
        protectedResultUri: String = "",
        errorMessage: String = ""
    ) = TransferDatabase.updateSyncAction(
        actionId, state, transferItemId, protectedResultUri, errorMessage
    )

    fun resolveConflict(action: SyncAction, resolution: String) {
        val run = requireNotNull(run(action.runId))
        val profile = requireNotNull(profile(run.profileId))
        val replacement = when (resolution) {
            "SOURCE" -> if (action.sourceUri.isBlank()) {
                action.copy(
                    type = SyncActionType.DELETE,
                    direction = SyncSide.DESTINATION,
                    sourceUri = action.targetUri,
                    targetUri = action.targetUri,
                    sourceFingerprint = action.targetFingerprint,
                    comparisonReason = "USER_PREFERRED_SOURCE_DELETION",
                    state = SyncActionState.PENDING
                )
            } else {
                action.copy(
                    type = if (action.targetUri.isBlank()) SyncActionType.COPY else SyncActionType.UPDATE,
                    direction = SyncSide.DESTINATION,
                    targetUri = action.targetUri.ifBlank {
                        SyncPathResolver.childUri(profile.destinationUri, action.relativePath)
                    },
                    comparisonReason = "USER_PREFERRED_SOURCE",
                    state = SyncActionState.PENDING
                )
            }
            "DESTINATION" -> if (action.targetUri.isBlank()) {
                action.copy(
                    type = SyncActionType.DELETE,
                    direction = SyncSide.SOURCE,
                    targetUri = action.sourceUri,
                    comparisonReason = "USER_PREFERRED_DESTINATION_DELETION",
                    state = SyncActionState.PENDING
                )
            } else {
                action.copy(
                    type = if (action.sourceUri.isBlank()) SyncActionType.COPY else SyncActionType.UPDATE,
                    direction = SyncSide.SOURCE,
                    sourceUri = action.targetUri,
                    targetUri = action.sourceUri.ifBlank {
                        SyncPathResolver.childUri(profile.sourceUri, action.relativePath)
                    },
                    sourceFingerprint = action.targetFingerprint,
                    targetFingerprint = action.sourceFingerprint,
                    comparisonReason = "USER_PREFERRED_DESTINATION",
                    state = SyncActionState.PENDING
                )
            }
            "SKIP" -> action.copy(
                type = SyncActionType.SKIP,
                state = SyncActionState.SKIPPED,
                comparisonReason = "USER_SKIPPED_CONFLICT"
            )
            else -> error("Unknown conflict resolution")
        }
        TransferDatabase.rewriteSyncConflict(replacement)
        TransferDatabase.skipUnusedConflictProtection(
            action.runId,
            action.relativePath,
            when (resolution) {
                "SOURCE" -> SyncSide.DESTINATION
                "DESTINATION" -> SyncSide.SOURCE
                else -> null
            }
        )
    }
}
