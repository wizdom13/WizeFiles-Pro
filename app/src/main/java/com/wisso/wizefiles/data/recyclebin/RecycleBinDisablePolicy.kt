package com.wisso.wizefiles.recyclebin

object RecycleBinDisablePolicy {
    fun shouldDisableImmediately(hasContents: Boolean): Boolean = !hasContents

    fun canDisableAfterClear(summary: RecycleBinOperationSummary): Boolean = !summary.hasFailures
}
