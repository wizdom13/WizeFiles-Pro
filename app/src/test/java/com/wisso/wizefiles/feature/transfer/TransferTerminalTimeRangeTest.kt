package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferTerminalTimeRangeTest {
    @Test
    fun `completed transfer uses recorded start and completion times`() {
        assertEquals(
            TransferTimeRange(200, 500),
            record(
                state = TransferOperationState.COMPLETED,
                createdAtMillis = 100,
                startedAtMillis = 200,
                updatedAtMillis = 600,
                completedAtMillis = 500
            ).terminalTimeRangeOrNull()
        )
    }

    @Test
    fun `cancelled transfer falls back to creation and update times`() {
        assertEquals(
            TransferTimeRange(100, 600),
            record(
                state = TransferOperationState.CANCELLED,
                createdAtMillis = 100,
                startedAtMillis = 0,
                updatedAtMillis = 600,
                completedAtMillis = 0
            ).terminalTimeRangeOrNull()
        )
    }

    @Test
    fun `completed with warnings has a terminal time range`() {
        assertEquals(
            TransferTimeRange(200, 500),
            record(
                state = TransferOperationState.COMPLETED_WITH_WARNINGS,
                startedAtMillis = 200,
                completedAtMillis = 500
            ).terminalTimeRangeOrNull()
        )
    }

    @Test
    fun `running transfer does not expose terminal timestamps`() {
        assertNull(record(state = TransferOperationState.RUNNING).terminalTimeRangeOrNull())
    }

    private fun record(
        state: TransferOperationState,
        createdAtMillis: Long = 100,
        startedAtMillis: Long = 0,
        updatedAtMillis: Long = 600,
        completedAtMillis: Long = 0
    ) = TransferOperationRecord(
        id = "operation",
        type = TransferOperationType.COPY,
        state = state,
        destinationUri = "file:///destination",
        pathSchemaVersion = TRANSFER_PATH_SCHEMA_VERSION,
        queuePosition = 0,
        createdAtMillis = createdAtMillis,
        startedAtMillis = startedAtMillis,
        updatedAtMillis = updatedAtMillis,
        completedAtMillis = completedAtMillis,
        totalItems = 1,
        completedItems = 1,
        failedItems = 0,
        skippedItems = 0,
        totalBytes = 1,
        transferredBytes = 1,
        currentItem = "",
        lastErrorCategory = "",
        lastErrorMessage = "",
        requiresUserAction = false,
        recoveryReason = ""
    )
}
