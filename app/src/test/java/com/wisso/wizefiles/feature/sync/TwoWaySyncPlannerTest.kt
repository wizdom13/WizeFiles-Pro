// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoWaySyncPlannerTest {
    @Test
    fun changeOnSourceCopiesToDestination() {
        val old = file("note.txt", 1, 1, "file:///a")
        val changed = file("note.txt", 2, 2, "file:///a")
        val other = file("note.txt", 1, 1, "file:///b")
        val plan = planner().plan(
            "run", sequenceOf(changed), sequenceOf(other), sequenceOf(old), sequenceOf(other),
            "file:///a", "file:///b"
        )
        assertEquals(SyncSide.DESTINATION, plan.actions.single().direction)
        assertEquals(SyncActionType.UPDATE, plan.actions.single().type)
        assertEquals(other.fingerprint(), plan.actions.single().targetFingerprint)
    }

    @Test
    fun simultaneousChangesBecomeDurableConflictWhenAskIsSelected() {
        val baselineA = file("note.txt", 1, 1, "file:///a")
        val baselineB = file("note.txt", 1, 1, "file:///b")
        val plan = planner(SyncConflictPolicy.ASK).plan(
            "run",
            sequenceOf(file("note.txt", 2, 2, "file:///a")),
            sequenceOf(file("note.txt", 3, 3, "file:///b")),
            sequenceOf(baselineA), sequenceOf(baselineB), "file:///a", "file:///b"
        )
        assertEquals(SyncActionType.CONFLICT, plan.actions.single().type)
        assertEquals(SyncActionState.BLOCKED, plan.actions.single().state)
    }

    @Test
    fun sourceDeletionPropagatesOnlyWhenEnabled() {
        val previousA = file("gone.txt", 1, 1, "file:///a")
        val currentB = file("gone.txt", 1, 1, "file:///b")
        val enabled = planner(propagate = true).plan(
            "run", emptySequence(), sequenceOf(currentB), sequenceOf(previousA),
            sequenceOf(currentB), "file:///a", "file:///b"
        )
        assertEquals(SyncActionType.DELETE, enabled.actions.single().type)

        val disabled = planner(propagate = false).plan(
            "run", emptySequence(), sequenceOf(currentB), sequenceOf(previousA),
            sequenceOf(currentB), "file:///a", "file:///b"
        )
        assertEquals(SyncActionType.SKIP, disabled.actions.single().type)
    }

    @Test
    fun failedScanProducesNoTwoWayActions() {
        val plan = planner().plan(
            "run", emptySequence(), emptySequence(), emptySequence(), emptySequence(),
            "file:///a", "file:///b", listOf("authentication expired")
        )
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun deletionVersusModificationAlwaysRequiresAttention() {
        val oldA = file("note.txt", 1, 1, "file:///a")
        val oldB = file("note.txt", 1, 1, "file:///b")
        val changedB = file("note.txt", 2, 2, "file:///b")
        val plan = planner(SyncConflictPolicy.PREFER_SOURCE).plan(
            "run",
            currentSource = emptySequence(),
            currentDestination = sequenceOf(changedB),
            baselineSource = sequenceOf(oldA),
            baselineDestination = sequenceOf(oldB),
            sourceRootUri = "file:///a",
            destinationRootUri = "file:///b"
        )

        assertEquals(SyncActionType.CONFLICT, plan.actions.single().type)
        assertEquals(SyncActionState.BLOCKED, plan.actions.single().state)
        assertEquals("DELETE_MODIFY_CONFLICT", plan.actions.single().comparisonReason)
    }

    private fun planner(
        policy: SyncConflictPolicy = SyncConflictPolicy.KEEP_BOTH,
        propagate: Boolean = true
    ) = TwoWaySyncPlanner(
        SyncComparisonPolicy.SIZE_AND_MODIFIED_TIME,
        policy,
        propagate,
        nowMillis = { 0 }
    )

    private fun file(path: String, size: Long, modified: Long, root: String) = SyncFileEntry(
        relativePath = path,
        uri = "$root/$path",
        isDirectory = false,
        sizeBytes = size,
        modifiedAtMillis = modified,
        modifiedPrecisionMillis = 0
    )
}
