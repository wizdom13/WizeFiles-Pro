// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.app.setGlobalApplicationForTests
import com.wisso.wizefiles.feature.transfer.TransferDatabase
import com.wisso.wizefiles.feature.transfer.TransferOperationSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncRecoveryManagerTest {
    @Before
    fun setUp() {
        setGlobalApplicationForTests(ApplicationProvider.getApplicationContext())
        TransferDatabase.clearForTests()
    }

    @After
    fun tearDown() = TransferDatabase.clearForTests()

    @Test
    fun detachedRunningWorkerBecomesPausedAndRecoverable() {
        val profile = SyncProfile(
            name = "Backup",
            sourceUri = "file:///source",
            destinationUri = "file:///destination",
            mode = SyncMode.UPDATE_DESTINATION
        )
        SyncRepository.saveProfile(profile)
        var run = SyncRepository.createRun(
            SyncRun(profileId = profile.id, trigger = SyncRunTrigger.SCHEDULED, baselineBefore = 1)
        )
        run = SyncRepository.markRunPreviewReady(run.id, SyncPlanSummary())
        run = SyncRepository.transitionRun(run.id, SyncRunState.APPROVED)
        run = SyncRepository.transitionRun(run.id, SyncRunState.QUEUED)
        val operation = TransferRepository.enqueue(
            TransferOperationSpec(
                type = TransferOperationType.COPY,
                sourceUris = listOf(profile.sourceUri),
                destinationUri = profile.destinationUri
            )
        )
        TransferRepository.transition(operation.id, TransferOperationState.RUNNING)
        SyncRepository.transitionRun(run.id, SyncRunState.RUNNING, transferOperationId = operation.id)

        assertEquals(1, SyncRecoveryManager.reconcileDetachedRun(profile.id))
        assertEquals(SyncRunState.PAUSED, SyncRepository.run(run.id)?.state)
        assertEquals(TransferOperationState.RECOVERABLE, TransferRepository.operation(operation.id)?.state)
    }
}
