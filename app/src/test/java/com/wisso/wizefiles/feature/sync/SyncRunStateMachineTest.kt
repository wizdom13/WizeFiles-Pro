package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertThrows
import org.junit.Test

class SyncRunStateMachineTest {
    @Test
    fun firstRunCannotBypassPreview() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncRunStateMachine.requireTransition(SyncRunState.PLANNING, SyncRunState.QUEUED)
        }
    }

    @Test
    fun approvedPreviewCanEnterPersistentQueue() {
        SyncRunStateMachine.requireTransition(SyncRunState.PREVIEW_READY, SyncRunState.APPROVED)
        SyncRunStateMachine.requireTransition(SyncRunState.APPROVED, SyncRunState.QUEUED)
    }
}
