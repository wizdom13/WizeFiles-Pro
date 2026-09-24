package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCenterActionDispatcherTest {
    private val events = mutableListOf<String>()
    private val dispatcher = TransferCenterActionDispatcher(
        pauseFileOperation = { events += "file-pause:$it" },
        resumeFileOperation = { events += "file-resume:$it" },
        cancelFileOperation = { events += "file-cancel:$it" },
        pauseNearby = { events += "nearby-pause:$it" },
        cancelNearby = { events += "nearby-cancel:$it" },
        openNearbyRecovery = { events += "nearby-recovery:$it" },
        openSigningRecovery = { type, id -> events += "signing-recovery:$type:$id" },
        openResult = { events += "open:${it.id}" },
        openDetails = { events += "details:$it" }
    )

    @Test fun `normal pause resume and retry route to file operation effects`() {
        executePrimary(TransferOperationType.COPY, TransferOperationState.RUNNING)
        executePrimary(TransferOperationType.COPY, TransferOperationState.PAUSED)
        executePrimary(TransferOperationType.COPY, TransferOperationState.FAILED)
        assertEquals(listOf("file-pause:id", "file-resume:id", "file-resume:id"), events)
    }

    @Test fun `completed result requires both semantic and concrete availability`() {
        val completed = operation(TransferOperationType.COPY, TransferOperationState.COMPLETED)
        val hidden = resolveTransferCenterPrimaryAction(completed, canOpenResult = false)
        assertEquals(TransferCenterPrimaryAction.NONE, hidden)
        assertFalse(dispatcher.primary(completed, hidden))
        assertTrue(events.isEmpty())

        val open = resolveTransferCenterPrimaryAction(completed, canOpenResult = true)
        assertTrue(dispatcher.primary(completed, open))
        assertEquals(listOf("open:id"), events)
    }

    @Test fun `Nearby pause and recovery use Nearby effects while failed exposes no action`() {
        executePrimary(TransferOperationType.NEARBY_SEND, TransferOperationState.RUNNING)
        executePrimary(TransferOperationType.NEARBY_SEND, TransferOperationState.PAUSED)
        executePrimary(TransferOperationType.NEARBY_RECEIVE, TransferOperationState.RECOVERABLE)
        val failed = operation(TransferOperationType.NEARBY_SEND, TransferOperationState.FAILED)
        val failedAction = resolveTransferCenterPrimaryAction(failed, true)
        assertEquals(TransferCenterPrimaryAction.NONE, failedAction)
        assertFalse(dispatcher.primary(failed, failedAction))
        assertEquals(
            listOf("nearby-pause:id", "nearby-recovery:id", "nearby-recovery:id"),
            events
        )
    }

    @Test fun `all signing recovery types retain their dedicated type routing`() {
        listOf(
            TransferOperationType.APK_SIGN,
            TransferOperationType.AAB_SIGN,
            TransferOperationType.APKS_SIGN,
            TransferOperationType.XAPK_SIGN
        ).forEach { type ->
            executePrimary(type, TransferOperationState.WAITING_FOR_USER)
            executePrimary(type, TransferOperationState.FAILED)
        }
        assertEquals(8, events.size)
        assertTrue(events.all { it.startsWith("signing-recovery:") })
    }

    @Test fun `active secondary cancels through correct service and terminal opens details`() {
        dispatcher.secondary(operation(TransferOperationType.COPY, TransferOperationState.PAUSED))
        dispatcher.secondary(operation(TransferOperationType.NEARBY_SEND, TransferOperationState.RUNNING))
        dispatcher.secondary(operation(TransferOperationType.COPY, TransferOperationState.COMPLETED))
        assertEquals(listOf("file-cancel:id", "nearby-cancel:id", "details:id"), events)
    }

    @Test fun `every presented primary action has a defined execution route`() {
        TransferOperationType.entries.forEach { type ->
            TransferOperationState.entries.forEach { state ->
                val operation = operation(type, state)
                val action = resolveTransferCenterPrimaryAction(operation, canOpenResult = true)
                if (action != TransferCenterPrimaryAction.NONE) {
                    assertTrue("No execution route for $type/$state/$action", dispatcher.primary(operation, action))
                }
            }
        }
    }

    private fun executePrimary(type: TransferOperationType, state: TransferOperationState) {
        val operation = operation(type, state)
        val action = resolveTransferCenterPrimaryAction(operation, canOpenResult = true)
        assertTrue("$type/$state must execute", dispatcher.primary(operation, action))
    }

    private fun operation(type: TransferOperationType, state: TransferOperationState) =
        TransferOperationRecord(
            id = "id", type = type, state = state, destinationUri = "file:/target",
            pathSchemaVersion = 1, queuePosition = 0, createdAtMillis = 0,
            startedAtMillis = 0, updatedAtMillis = 0, completedAtMillis = 0,
            totalItems = 0, completedItems = 0, failedItems = 0, skippedItems = 0,
            totalBytes = 0, transferredBytes = 0, currentItem = "",
            lastErrorCategory = "", lastErrorMessage = "", requiresUserAction = false,
            recoveryReason = ""
        )
}
