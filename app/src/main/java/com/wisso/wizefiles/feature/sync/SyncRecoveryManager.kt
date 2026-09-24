// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.feature.transfer.OperationControlRegistry
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferRepository

internal object SyncRecoveryManager {
    fun reconcileDetachedRun(profileId: String? = null): Int {
        val profiles = if (profileId == null) SyncRepository.profiles() else {
            listOfNotNull(SyncRepository.profile(profileId))
        }
        var recovered = 0
        profiles.forEach { profile ->
            SyncRepository.runs(profile.id).filter { it.state == SyncRunState.RUNNING }.forEach runLoop@ { run ->
                val operationId = run.transferOperationId
                if (operationId.isBlank() || OperationControlRegistry.isAttached(operationId)) return@runLoop
                val operation = TransferRepository.operation(operationId)
                if (operation?.state == TransferOperationState.PLANNING ||
                    operation?.state == TransferOperationState.RUNNING ||
                    operation?.state == TransferOperationState.PAUSE_REQUESTED) {
                    runCatching {
                        TransferRepository.transition(
                            operationId,
                            TransferOperationState.RECOVERABLE,
                            reason = "SYNC_WORKER_TERMINATED"
                        )
                    }
                }
                recovered++
            }
        }
        SyncRepository.reconcileInterruptedRuns()
        return recovered
    }
}
