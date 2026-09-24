// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferSchedulerTest {
    private val scheduler = TransferScheduler()

    @Test
    fun `scheduler starts at most two operations in queue order`() {
        val selected = scheduler.selectRunnable(
            listOf(
                operation("third", 2, "file:///storage/emulated/0/Movies"),
                operation("first", 0, "file:///storage/emulated/0/Download"),
                operation("second", 1, "smb://server/share/backup")
            ),
            emptyList()
        )

        assertEquals(listOf("first", "second"), selected.map(TransferOperationRecord::id))
    }

    @Test
    fun `scheduler serializes overlapping destinations but runs independent transfer`() {
        val selected = scheduler.selectRunnable(
            listOf(
                operation("overlap", 0, "smb://server/share/photos/2026"),
                operation("independent", 1, "file:///storage/emulated/0/Download")
            ),
            listOf(operation("running", 0, "smb://server/share/photos"))
        )

        assertEquals(listOf("independent"), selected.map(TransferOperationRecord::id))
    }

    private fun operation(id: String, queue: Long, destination: String) = TransferOperationRecord(
        id = id,
        type = TransferOperationType.COPY,
        state = TransferOperationState.QUEUED,
        destinationUri = destination,
        pathSchemaVersion = TRANSFER_PATH_SCHEMA_VERSION,
        queuePosition = queue,
        createdAtMillis = queue,
        startedAtMillis = 0,
        updatedAtMillis = 0,
        completedAtMillis = 0,
        totalItems = 0,
        completedItems = 0,
        failedItems = 0,
        skippedItems = 0,
        totalBytes = 0,
        transferredBytes = 0,
        currentItem = "",
        lastErrorCategory = "",
        lastErrorMessage = "",
        requiresUserAction = false,
        recoveryReason = ""
    )
}
