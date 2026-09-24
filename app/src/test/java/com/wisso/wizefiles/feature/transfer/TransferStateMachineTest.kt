// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferStateMachineTest {
    @Test
    fun `running transfer can pause wait recover complete or fail`() {
        val allowed = setOf(
            TransferOperationState.PAUSE_REQUESTED,
            TransferOperationState.WAITING_FOR_USER,
            TransferOperationState.RECOVERABLE,
            TransferOperationState.COMPLETED,
            TransferOperationState.COMPLETED_WITH_WARNINGS,
            TransferOperationState.FAILED,
            TransferOperationState.CANCELLED
        )
        TransferOperationState.entries.forEach { state ->
            assertTrue(
                "Unexpected RUNNING -> $state result",
                TransferStateMachine.canTransition(TransferOperationState.RUNNING, state) ==
                    (state == TransferOperationState.RUNNING || state in allowed)
            )
        }
    }

    @Test
    fun `waiting transfer can recover after service interruption`() {
        assertTrue(
            TransferStateMachine.canTransition(
                TransferOperationState.WAITING_FOR_USER,
                TransferOperationState.RECOVERABLE
            )
        )
    }

    @Test
    fun `cancelled transfer remains terminal`() {
        TransferOperationState.entries.forEach { state ->
            assertFalse(
                "CANCELLED must not transition to $state",
                TransferStateMachine.canTransition(TransferOperationState.CANCELLED, state) &&
                    state != TransferOperationState.CANCELLED
            )
        }
    }

    @Test
    fun `completed transfer cannot be restarted or cancelled`() {
        assertFalse(
            TransferStateMachine.canTransition(
                TransferOperationState.COMPLETED,
                TransferOperationState.QUEUED
            )
        )
        assertFalse(
            TransferStateMachine.canTransition(
                TransferOperationState.COMPLETED,
                TransferOperationState.CANCELLED
            )
        )
    }
}
