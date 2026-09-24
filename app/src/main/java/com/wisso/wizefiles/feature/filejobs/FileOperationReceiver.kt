// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.core.app.application

class FileOperationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            ACTION_CANCEL -> {
                val jobId = intent.getIntExtra(EXTRA_JOB_ID, 0)
                FileOperationService.cancelJob(jobId)
            }
            ACTION_PAUSE -> {
                val operationId = intent.getStringExtra(EXTRA_OPERATION_ID) ?: return
                FileOperationService.pauseTransfer(operationId)
            }
            else -> throw IllegalArgumentException(action)
        }
    }

    companion object {
        private const val ACTION_CANCEL = "cancel"
        private const val ACTION_PAUSE = "pause"

        private const val EXTRA_JOB_ID = "jobId"
        private const val EXTRA_OPERATION_ID = "operationId"

        fun createIntent(jobId: Int): Intent =
            Intent(application, FileOperationReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_JOB_ID, jobId)

        fun createPauseIntent(operationId: String): Intent =
            Intent(application, FileOperationReceiver::class.java)
                .setAction(ACTION_PAUSE)
                .putExtra(EXTRA_OPERATION_ID, operationId)
    }
}
