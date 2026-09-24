// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertThrows
import org.junit.Test

class SyncRunStateMachineTest {
    @Test fun `preview approval is mandatory before queueing`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncRunStateMachine.requireTransition(SyncRunState.PLANNING, SyncRunState.QUEUED)
        }
        SyncRunStateMachine.requireTransition(SyncRunState.PREVIEW_READY, SyncRunState.APPROVED)
        SyncRunStateMachine.requireTransition(SyncRunState.APPROVED, SyncRunState.QUEUED)
    }
}
