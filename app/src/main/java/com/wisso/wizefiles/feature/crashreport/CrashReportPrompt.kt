// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.crashreport

import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R

object CrashReportPrompt {
    @Volatile
    private var shownThisProcess = false

    fun showIfPending(activity: FragmentActivity) {
        if (shownThisProcess || activity.isFinishing || !CrashReportStore.hasPending(activity)) return
        shownThisProcess = true

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.crash_report_pending_title)
            .setMessage(R.string.crash_report_pending_message)
            .setPositiveButton(R.string.crash_report_review) { _, _ ->
                activity.startActivity(CrashReportActivity.createIntent(activity))
            }
            .setNegativeButton(R.string.crash_report_later, null)
            .setNeutralButton(R.string.crash_report_discard) { _, _ ->
                CrashReportStore.delete(activity)
                CrashReportNotification.cancel(activity)
            }
            .show()
    }
}
