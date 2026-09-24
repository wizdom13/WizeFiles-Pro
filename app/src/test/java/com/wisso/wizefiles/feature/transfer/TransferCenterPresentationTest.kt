package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class TransferCenterPresentationTest {
    @Test
    fun `destination resolution accepts valid browsable operations`() {
        val destination = resolveOpenDestination(operation(TransferOperationType.COPY, "file:/target"))

        assertEquals("/target", destination?.toString())
    }

    @Test
    fun `destination resolution rejects unsupported operations and unresolved destinations`() {
        assertNull(resolveOpenDestination(operation(TransferOperationType.NEARBY_SEND, "file:/target")))
        assertNull(resolveOpenDestination(operation(TransferOperationType.DELETE, "file:/target")))
        assertNull(resolveOpenDestination(operation(TransferOperationType.COPY, "unknown:/target")))
    }

    @Test
    fun `open destination requires a resolved local destination`() {
        val resolvedPath = Paths.get("/storage/emulated/0/Download")

        assertFalse(
            shouldOfferOpenDestination(TransferOperationType.NEARBY_SEND, resolvedPath)
        )
        assertFalse(
            shouldOfferOpenDestination(TransferOperationType.DELETE, resolvedPath)
        )
        assertFalse(
            shouldOfferOpenDestination(TransferOperationType.NEARBY_RECEIVE, null)
        )
        assertTrue(
            shouldOfferOpenDestination(TransferOperationType.NEARBY_RECEIVE, resolvedPath)
        )
    }

    @Test
    fun `paused and completed transfers never expose stale live metrics`() {
        val paused = buildTransferProgressUi(
            TransferOperationState.PAUSED,
            totalItems = 7,
            completedItems = 2,
            skippedItems = 0,
            failedItems = 0,
            hasCurrentItem = true
        )
        val completed = buildTransferProgressUi(
            TransferOperationState.COMPLETED,
            totalItems = 1,
            completedItems = 1,
            skippedItems = 0,
            failedItems = 0,
            hasCurrentItem = false
        )

        assertFalse(paused.showLiveMetrics)
        assertNull(paused.activeItemNumber)
        assertFalse(paused.forceCompleteProgress)
        assertFalse(completed.showLiveMetrics)
        assertTrue(completed.forceCompleteProgress)
    }

    @Test
    fun `running transfer exposes the current item after completed results`() {
        val running = buildTransferProgressUi(
            TransferOperationState.RUNNING,
            totalItems = 4,
            completedItems = 1,
            skippedItems = 1,
            failedItems = 0,
            hasCurrentItem = true
        )

        assertEquals(2L, running.processedItems)
        assertEquals(3L, running.activeItemNumber)
        assertTrue(running.showLiveMetrics)
    }

    @Test
    fun `local uri is decoded and presented from internal storage`() {
        assertEquals(
            "Internal storage / Documents / Travel / Dubai July 2022",
            formatLocalTransferUri(
                "file:/storage/emulated/0/Documents/Travel/Dubai%20July%202022",
                "Internal storage"
            )
        )
        assertNull(
            formatLocalTransferUri("rclone://box/Travel", "Internal storage")
        )
    }

    @Test
    fun `rclone uri uses configured account name and retains its path`() {
        val formatted = formatRcloneTransferUri("rclone://wfbbf9cb/Backups/Phone") {
            if (it == "wfbbf9cb") "Google Drive" else null
        }

        assertEquals("Google Drive / Backups/Phone", formatted)
        assertNull(formatRcloneTransferUri("smb://server/share") { "unused" })
    }

    private fun operation(type: TransferOperationType, destinationUri: String) =
        TransferOperationRecord(
            id = "id", type = type, state = TransferOperationState.COMPLETED,
            destinationUri = destinationUri, pathSchemaVersion = 1, queuePosition = 0,
            createdAtMillis = 0, startedAtMillis = 0, updatedAtMillis = 0,
            completedAtMillis = 0, totalItems = 0, completedItems = 0, failedItems = 0,
            skippedItems = 0, totalBytes = 0, transferredBytes = 0, currentItem = "",
            lastErrorCategory = "", lastErrorMessage = "", requiresUserAction = false,
            recoveryReason = ""
        )
}
