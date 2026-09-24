package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UpdateDestinationPlannerTest {
    @Test
    fun copiesNewUpdatesChangedAndPreservesDestinationOnlyFiles() {
        val source = sequenceOf(
            file("same.txt", 10, 100),
            file("changed.txt", 20, 200),
            file("new.txt", 30, 300)
        )
        val destination = sequenceOf(
            file("same.txt", 10, 100, root = "rclone://drive/backup"),
            file("changed.txt", 19, 100, root = "rclone://drive/backup"),
            file("destination-only.txt", 99, 999, root = "rclone://drive/backup")
        )

        val plan = UpdateDestinationPlanner(SyncComparisonPolicy.SIZE_AND_MODIFIED_TIME).plan(
            runId = "run",
            sourceEntries = source,
            destinationEntries = destination,
            destinationRootUri = "rclone://drive/backup"
        )

        assertEquals(listOf(SyncActionType.SKIP, SyncActionType.UPDATE, SyncActionType.COPY), plan.actions.map { it.type })
        assertEquals(1L, plan.summary.copiesToDestination)
        assertEquals(1L, plan.summary.updates)
        assertEquals(50L, plan.summary.transferBytes)
        assertFalse(plan.actions.any { it.relativePath == "destination-only.txt" })
    }

    @Test
    fun symlinksAndInternalPartFilesAreSkippedByDefault() {
        val plan = UpdateDestinationPlanner(SyncComparisonPolicy.SMART).plan(
            runId = "run",
            sourceEntries = sequenceOf(
                file("link", 0, 0).copy(isSymlink = true),
                file(".wizefiles-part-run-item", 10, 1)
            ),
            destinationEntries = emptySequence(),
            destinationRootUri = "file:///backup"
        )
        assertEquals(0, plan.actions.size)
        assertEquals(2L, plan.summary.unchangedOrSkipped)
    }

    @Test
    fun internalVersionDirectoriesRemainExcludedWhenHiddenFilesAreIncluded() {
        val plan = UpdateDestinationPlanner(
            SyncComparisonPolicy.SMART,
            SyncFilterRules(includeHidden = true)
        ).plan(
            runId = "run",
            sourceEntries = sequenceOf(file(".wizefiles-versions/run/old.txt", 10, 1)),
            destinationEntries = emptySequence(),
            destinationRootUri = "file:///backup"
        )

        assertEquals(0, plan.actions.size)
        assertEquals(1L, plan.summary.unchangedOrSkipped)
    }

    @Test
    fun scanErrorsDisableDestructiveExecution() {
        val plan = UpdateDestinationPlanner(SyncComparisonPolicy.SMART).plan(
            runId = "run",
            sourceEntries = emptySequence(),
            destinationEntries = emptySequence(),
            destinationRootUri = "file:///backup",
            scanErrors = listOf("source unavailable")
        )
        assertFalse(plan.canExecuteDestructiveActions)
    }

    private fun file(
        path: String,
        size: Long,
        modified: Long,
        root: String = "file:///source"
    ) = SyncFileEntry(
        relativePath = path,
        uri = "$root/$path",
        isDirectory = false,
        sizeBytes = size,
        modifiedAtMillis = modified,
        modifiedPrecisionMillis = 1
    )
}
