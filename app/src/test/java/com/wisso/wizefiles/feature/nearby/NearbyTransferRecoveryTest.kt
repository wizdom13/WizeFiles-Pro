// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.content.Context
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.app.setGlobalApplicationForTests
import com.wisso.wizefiles.feature.transfer.TransferDatabase
import com.wisso.wizefiles.feature.transfer.TransferOperationSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.storage.NearbyDurableOperationState
import com.wisso.wizefiles.storage.NearbyPayloadCheckpoint
import com.wisso.wizefiles.storage.NearbyPayloadDirection
import com.wisso.wizefiles.storage.NearbyPayloadState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NearbyTransferRecoveryTest {
    private lateinit var store: NearbySessionStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setGlobalApplicationForTests(context)
        TransferDatabase.clearForTests()
        context.getSharedPreferences("nearby_transfer_sessions_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = NearbySessionStore(context)
    }

    @After
    fun tearDown() = TransferDatabase.clearForTests()

    @Test
    fun `durable operation state is authoritative and checkpoints never recreate transport`() {
        insert("active")
        insert("completed")
        TransferDatabase.transition("completed", TransferOperationState.PLANNING)
        TransferDatabase.transition("completed", TransferOperationState.RUNNING)
        TransferDatabase.transition("completed", TransferOperationState.COMPLETED)
        insert("cancelled")
        TransferDatabase.transition("cancelled", TransferOperationState.CANCELLED)

        val recovery = NearbyTransferRecovery(store)
        assertRecovery(recovery, "active", NearbyDurableOperationState.ACTIVE)
        assertRecovery(recovery, "completed", NearbyDurableOperationState.TERMINAL)
        assertRecovery(recovery, "cancelled", NearbyDurableOperationState.CANCELLED)
        assertRecovery(recovery, "missing", NearbyDurableOperationState.MISSING)

        assertEquals(TransferOperationState.COMPLETED, TransferDatabase.operation("completed")?.state)
        assertEquals(TransferOperationState.CANCELLED, TransferDatabase.operation("cancelled")?.state)
        assertNull(TransferDatabase.operation("missing"))
    }

    private fun assertRecovery(
        recovery: NearbyTransferRecovery,
        operationId: String,
        expectedState: NearbyDurableOperationState
    ) {
        val restored = recovery.reconcilePayloads(snapshot(operationId), nowMillis = 2_000, maxPendingIncoming = 2)
        assertEquals(expectedState, restored.durableState)
        assertNull(restored.ledger.activePayloadId)
        assertTrue(restored.ledger.checkpoints().isEmpty())
    }

    private fun insert(id: String) {
        TransferDatabase.insertOperation(
            TransferOperationSpec(
                id = id,
                type = TransferOperationType.NEARBY_SEND,
                sourceUris = listOf("file:///source"),
                destinationUri = "nearby://peer"
            )
        )
    }

    private fun snapshot(operationId: String) = NearbySessionSnapshot(
        operationId = operationId,
        sessionId = "session",
        role = NearbyRole.SEND,
        peerName = "peer",
        destinationUri = "nearby://peer",
        conflictPolicy = NearbyConflictPolicy.KEEP_BOTH,
        payloadCheckpoints = listOf(
            NearbyPayloadCheckpoint(7, NearbyPayloadDirection.OUTGOING, NearbyPayloadState.ACTIVE)
        ),
        updatedAtMillis = 1_000
    )
}
