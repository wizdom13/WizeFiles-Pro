// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.recyclebin

object RecycleBinDisablePolicy {
    fun shouldDisableImmediately(hasContents: Boolean): Boolean = !hasContents

    fun canDisableAfterClear(summary: RecycleBinOperationSummary): Boolean = !summary.hasFailures
}
