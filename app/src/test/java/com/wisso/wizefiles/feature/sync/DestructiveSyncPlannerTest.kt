// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveSyncPlannerTest {
    @Test
    fun mirrorProtectsBeforeDeletingDestinationOnlyFile() {
        val plan = MirrorPlanner(
            SyncComparisonPolicy.SIZE_ONLY,
            protection = VersionProtectionPolicy(enabled = true, versionsRootUri = "file:///versions")
        ).plan(
            "run",
            sourceEntries = emptySequence(),
            destinationEntries = sequenceOf(file("old.txt", 5)),
            destinationRootUri = "file:///backup"
        )
        assertEquals(listOf(SyncActionType.PROTECT, SyncActionType.DELETE), plan.actions.map { it.type })
        assertEquals(1L, plan.summary.protectedItems)
        assertEquals(1L, plan.summary.deletions)
    }

    @Test
    fun incompleteScanNeverPlansMirrorDeletion() {
        val plan = MirrorPlanner(
            SyncComparisonPolicy.SMART,
            protection = VersionProtectionPolicy(enabled = true, versionsRootUri = "file:///versions")
        ).plan(
            "run",
            sourceEntries = emptySequence(),
            destinationEntries = sequenceOf(file("keep.txt", 5)),
            destinationRootUri = "file:///backup",
            scanErrors = listOf("source unavailable")
        )
        assertFalse(plan.actions.any { it.type == SyncActionType.DELETE })
    }

    @Test
    fun mirrorProtectsExistingVersionBeforeUpdating() {
        val plan = MirrorPlanner(
            SyncComparisonPolicy.SIZE_ONLY,
            protection = VersionProtectionPolicy(enabled = true, versionsRootUri = "file:///versions")
        ).plan(
            "run",
            sourceEntries = sequenceOf(file("report.pdf", 20, "file:///source")),
            destinationEntries = sequenceOf(file("report.pdf", 10, "file:///backup")),
            destinationRootUri = "file:///backup"
        )

        assertEquals(
            listOf(SyncActionType.PROTECT, SyncActionType.UPDATE),
            plan.actions.map(SyncAction::type)
        )
        assertEquals(1L, plan.summary.protectedItems)
        assertEquals(10L, plan.summary.protectedBytes)
    }

    @Test
    fun automaticRunBlocksLargeDeletionRatio() {
        val decision = DestructiveSyncSafety.evaluate(
            deleteCount = 20,
            destinationItemCount = 100,
            deleteBytes = 1_000,
            scanErrors = emptyList(),
            storageIdentityChanged = false
        )
        assertFalse(decision.allowed)
        assertEquals("DELETION_RATIO_LIMIT", decision.reason)
    }

    @Test
    fun moveLeavesConflictingSourceUntouched() {
        val plan = MoveSourcePlanner(SyncComparisonPolicy.SIZE_ONLY).plan(
            "run",
            sourceEntries = sequenceOf(file("report.pdf", 10, "file:///source")),
            destinationEntries = sequenceOf(file("report.pdf", 20, "file:///target")),
            destinationRootUri = "file:///target"
        )
        assertEquals(SyncActionType.CONFLICT, plan.actions.single().type)
        assertEquals(SyncActionState.BLOCKED, plan.actions.single().state)
        assertTrue(plan.actions.none { it.type == SyncActionType.DELETE })
    }

    @Test
    fun moveSkipsDirectoryEntries() {
        val directory = SyncFileEntry(
            relativePath = "photos",
            uri = "file:///source/photos",
            isDirectory = true,
            sizeBytes = 0,
            modifiedAtMillis = 1
        )
        val plan = MoveSourcePlanner(SyncComparisonPolicy.SIZE_ONLY).plan(
            "run",
            sourceEntries = sequenceOf(directory),
            destinationEntries = emptySequence(),
            destinationRootUri = "file:///target"
        )

        assertEquals(SyncActionType.SKIP, plan.actions.single().type)
        assertEquals(SyncActionState.SKIPPED, plan.actions.single().state)
        assertEquals(0L, plan.summary.moves)
    }

    @Test
    fun retentionKeepsOnlyNewestConfiguredVersions() {
        val versions = listOf(
            RetainedVersion("report.pdf", 100, 10, "run1/report.pdf"),
            RetainedVersion("report.pdf", 200, 10, "run2/report.pdf"),
            RetainedVersion("report.pdf", 300, 10, "run3/report.pdf")
        )
        val remove = VersionRetentionPruner.selectForDeletion(
            versions,
            VersionProtectionPolicy(enabled = true, retentionDays = 365, versionsPerFile = 2),
            nowMillis = 400
        )
        assertEquals(listOf("run1/report.pdf"), remove.map(RetainedVersion::storagePath))
    }

    private fun file(path: String, size: Long, root: String = "file:///backup") = SyncFileEntry(
        relativePath = path,
        uri = "$root/$path",
        isDirectory = false,
        sizeBytes = size,
        modifiedAtMillis = 1
    )
}
