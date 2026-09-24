// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.editor

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveMutationPlannerTest {
    @Test
    fun `folder rename remaps every descendant`() {
        val plan = ArchiveMutationPlanner.plan(
            existing = listOf(
                ArchiveNamespaceEntry("old", true),
                ArchiveNamespaceEntry("old/a.txt", false, 3),
                ArchiveNamespaceEntry("old/sub", true),
                ArchiveNamespaceEntry("old/sub/b.txt", false, 4)
            ),
            mutations = listOf(
                ArchiveMutation(ArchiveMutationType.RENAME, "old", "new")
            )
        )
        assertEquals(
            setOf("new", "new/a.txt", "new/sub", "new/sub/b.txt"),
            plan.finalPaths
        )
    }

    @Test
    fun `folder delete removes descendants only`() {
        val plan = ArchiveMutationPlanner.plan(
            existing = listOf(
                ArchiveNamespaceEntry("one", true),
                ArchiveNamespaceEntry("one/a", false),
                ArchiveNamespaceEntry("two", true),
                ArchiveNamespaceEntry("two/a", false)
            ),
            mutations = listOf(ArchiveMutation(ArchiveMutationType.DELETE, "one"))
        )
        assertFalse("one" in plan.finalPaths)
        assertFalse("one/a" in plan.finalPaths)
        assertTrue("two/a" in plan.finalPaths)
    }

    @Test
    fun `keep both produces deterministic sibling name`() {
        val plan = ArchiveMutationPlanner.plan(
            existing = listOf(ArchiveNamespaceEntry("photo.jpg", false)),
            mutations = emptyList(),
            additions = listOf(
                PlannedArchiveEntry(null, "photo.jpg", false, "file:///photo.jpg")
            ),
            conflictPolicy = ArchiveConflictPolicy.KEEP_BOTH
        )
        assertTrue("photo (2).jpg" in plan.finalPaths)
    }

    @Test
    fun `keep both remaps a complete folder tree consistently`() {
        val plan = ArchiveMutationPlanner.plan(
            existing = listOf(ArchiveNamespaceEntry("photos", true)),
            mutations = emptyList(),
            additions = listOf(
                PlannedArchiveEntry(null, "photos", true, "file:///photos"),
                PlannedArchiveEntry(null, "photos/one.jpg", false, "file:///photos/one.jpg")
            ),
            conflictPolicy = ArchiveConflictPolicy.KEEP_BOTH
        )
        assertTrue("photos (2)" in plan.finalPaths)
        assertTrue("photos (2)/one.jpg" in plan.finalPaths)
        assertFalse("photos/one.jpg" in plan.finalPaths)
    }

    @Test(expected = IOException::class)
    fun `rename never replaces occupied target`() {
        ArchiveMutationPlanner.plan(
            existing = listOf(
                ArchiveNamespaceEntry("one", false),
                ArchiveNamespaceEntry("two", false)
            ),
            mutations = listOf(ArchiveMutation(ArchiveMutationType.RENAME, "one", "two"))
        )
    }

    @Test(expected = IOException::class)
    fun `traversal is rejected`() {
        ArchiveMutationPlanner.normalize("../escape", false)
    }

    @Test(expected = IOException::class)
    fun `case insensitive duplicates are rejected`() {
        ArchiveMutationPlanner.plan(
            existing = listOf(
                ArchiveNamespaceEntry("A.txt", false),
                ArchiveNamespaceEntry("a.TXT", false)
            ),
            mutations = emptyList()
        )
    }
}
