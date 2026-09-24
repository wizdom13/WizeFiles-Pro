package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduledSyncDispositionTest {
    @Test
    fun `destructive safety block takes precedence over ask conflicts`() {
        val disposition = scheduledSyncDisposition(
            runState = SyncRunState.PREVIEW_READY,
            safetyAllowed = false,
            deletions = 4,
            conflicts = 2,
            askBeforeResolvingConflicts = true
        )

        assertEquals(ScheduledSyncDisposition.SAFETY_BLOCKED, disposition)
    }

    @Test
    fun `existing non-overridable safety block is preserved without deletions`() {
        val disposition = scheduledSyncDisposition(
            runState = SyncRunState.SAFETY_BLOCKED,
            safetyAllowed = false,
            deletions = 0,
            conflicts = 1,
            askBeforeResolvingConflicts = true
        )

        assertEquals(ScheduledSyncDisposition.SAFETY_BLOCKED, disposition)
    }

    @Test
    fun `safe ask conflict waits for attention`() {
        val disposition = scheduledSyncDisposition(
            runState = SyncRunState.PREVIEW_READY,
            safetyAllowed = true,
            deletions = 0,
            conflicts = 1,
            askBeforeResolvingConflicts = true
        )

        assertEquals(ScheduledSyncDisposition.NEEDS_ATTENTION, disposition)
    }

    @Test
    fun `safe plan without ask conflicts executes`() {
        val disposition = scheduledSyncDisposition(
            runState = SyncRunState.PREVIEW_READY,
            safetyAllowed = true,
            deletions = 0,
            conflicts = 0,
            askBeforeResolvingConflicts = true
        )

        assertEquals(ScheduledSyncDisposition.EXECUTE, disposition)
    }
}
