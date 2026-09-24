package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.feature.transfer.TransferOperationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileOperationInterruptionPolicyTest {
    @Test
    fun `explicit cancellation targets cancelled without recovery`() {
        assertEquals(
            TransferOperationState.CANCELLED,
            interruptedTransferTargetState(
                cancellationRequested = true,
                currentState = TransferOperationState.RUNNING
            )
        )
        assertEquals(
            TransferOperationState.CANCELLED,
            interruptedTransferTargetState(
                cancellationRequested = true,
                currentState = TransferOperationState.WAITING_FOR_USER
            )
        )
    }

    @Test
    fun `service interruption makes active and waiting transfers recoverable`() {
        assertEquals(
            TransferOperationState.RECOVERABLE,
            interruptedTransferTargetState(
                cancellationRequested = false,
                currentState = TransferOperationState.RUNNING
            )
        )
        assertEquals(
            TransferOperationState.RECOVERABLE,
            interruptedTransferTargetState(
                cancellationRequested = false,
                currentState = TransferOperationState.WAITING_FOR_USER
            )
        )
    }

    @Test
    fun `terminal transfers are preserved after a late interruption`() {
        TransferOperationState.entries
            .filter(TransferOperationState::isTerminal)
            .forEach { state ->
                assertNull(
                    interruptedTransferTargetState(
                        cancellationRequested = state == TransferOperationState.CANCELLED,
                        currentState = state
                    )
                )
            }
    }
}
