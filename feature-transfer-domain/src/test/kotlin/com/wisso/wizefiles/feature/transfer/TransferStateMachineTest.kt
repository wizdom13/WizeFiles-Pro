// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferStateMachineTest {
    @Test
    fun `resume policy advances recoverable work through queued without skipping transitions`() {
        assertEquals(
            listOf(TransferOperationState.QUEUED, TransferOperationState.RUNNING),
            TransferResumePolicy.transitionsFrom(TransferOperationState.RECOVERABLE)
        )
        assertEquals(
            listOf(TransferOperationState.RUNNING),
            TransferResumePolicy.transitionsFrom(TransferOperationState.QUEUED)
        )
        assertTrue(TransferResumePolicy.transitionsFrom(TransferOperationState.COMPLETED).isEmpty())
    }
    @Test fun `terminal operations cannot restart`() {
        assertFalse(TransferStateMachine.canTransition(TransferOperationState.COMPLETED, TransferOperationState.QUEUED))
        assertFalse(TransferStateMachine.canTransition(TransferOperationState.CANCELLED, TransferOperationState.RUNNING))
    }

    @Test fun `recoverable operations may be queued without persistence dependencies`() {
        assertTrue(TransferStateMachine.canTransition(TransferOperationState.RECOVERABLE, TransferOperationState.QUEUED))
    }
}
