// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.wisso.wizefiles.feature.transfer.OperationControlRegistry
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferRepository

internal object SyncthingRunControls {
    fun stop(context: Context, profileId: String, cancel: Boolean) {
        val run = SyncRepository.runs(profileId).firstOrNull { !it.state.isTerminal } ?: return
        val attached = if (cancel) OperationControlRegistry.requestCancel(run.transferOperationId)
            else OperationControlRegistry.requestPause(run.transferOperationId)
        if (attached) return
        // Stop queued continuation without cancelling the folder's future schedule.
        WorkManager.getInstance(context).cancelUniqueWork("syncthing-manual-$profileId")
        WorkManager.getInstance(context).cancelUniqueWork("folder-sync-resume-${run.id}")
        TransferRepository.operation(run.transferOperationId)?.let {
            TransferRepository.transition(it.id, if (cancel) TransferOperationState.CANCELLED else TransferOperationState.PAUSED)
        }
        SyncRepository.transitionRun(run.id, if (cancel) SyncRunState.CANCELLED else SyncRunState.PAUSED)
        val outcome = if (cancel) SyncthingSessionOutcome.CANCELLED else SyncthingSessionOutcome.PAUSED
        SyncthingProgressJournal.finish(profileId, run.id, outcome)
        SyncthingSessionHistory.record(profileId, run.id, outcome)
    }
}

class SyncthingControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val profileId = intent.getStringExtra("profile") ?: return
        if (intent.action !in setOf(ACTION_PAUSE, ACTION_CANCEL)) return
        runCatching { SyncthingRunControls.stop(context, profileId, intent.action == ACTION_CANCEL) }
    }
    companion object {
        const val ACTION_PAUSE = "com.wisso.wizefiles.SYNCTHING_PAUSE"
        const val ACTION_CANCEL = "com.wisso.wizefiles.SYNCTHING_CANCEL"
    }
}
