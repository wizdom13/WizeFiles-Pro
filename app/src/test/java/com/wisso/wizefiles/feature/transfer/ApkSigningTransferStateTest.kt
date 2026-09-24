// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSigningTransferStateTest {
    @Test
    fun `queued or interrupted signing may wait for password reentry`() {
        assertTrue(
            TransferStateMachine.canTransition(
                TransferOperationState.QUEUED,
                TransferOperationState.WAITING_FOR_USER
            )
        )
        assertTrue(
            TransferStateMachine.canTransition(
                TransferOperationState.RECOVERABLE,
                TransferOperationState.WAITING_FOR_USER
            )
        )
    }
}
