// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.OperationCancellation
import com.wisso.wizefiles.storage.OperationCancelledException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyTransferPlannerIntegrationTest {
    @Test
    fun `resolved URI entries map to neutral requests before persistence`() {
        val entries = listOf(
            entry("one", "folder", directory = true),
            entry("two", "folder/report.txt", directory = false)
        )

        val plan = NearbyTransferPlanner.planResolvedEntries(entries)
        val requests = plan.requests.map { it as FileOperationRequest.Copy }

        assertEquals(entries.map { it.relativePath }, requests.map { it.source.path })
        assertEquals(listOf("pending/one", "pending/two"), requests.map { it.target.path })
        assertEquals(setOf(MetadataAttribute.MODIFIED_TIME), plan.metadataIntent)
        assertTrue(plan.resumeRequired)
    }

    @Test
    fun `different adapter URI schemes produce equivalent neutral planning`() {
        val local = NearbyTransferPlanner.planResolvedEntries(
            listOf(entry("same", "file.txt", false, "file:///storage/file.txt"))
        )
        val remote = NearbyTransferPlanner.planResolvedEntries(
            listOf(entry("same", "file.txt", false, "sftp://server/file.txt"))
        )

        assertEquals(local.conflictPolicy, remote.conflictPolicy)
        assertEquals(local.metadataIntent, remote.metadataIntent)
        assertEquals(local.resumeRequired, remote.resumeRequired)
        assertEquals("file", local.requests.single().source.backendId)
        assertEquals("sftp:server", remote.requests.single().source.backendId)
        assertEquals(
            (local.requests.single() as FileOperationRequest.Copy).target.path,
            (remote.requests.single() as FileOperationRequest.Copy).target.path
        )
    }

    @Test
    fun `neutral planning stops before persisting requests when cancelled`() {
        val entries = listOf(
            entry("one", "one.txt", directory = false),
            entry("two", "two.txt", directory = false)
        )
        var checks = 0

        assertThrows(OperationCancelledException::class.java) {
            NearbyTransferPlanner.planResolvedEntries(
                entries,
                OperationCancellation { ++checks >= 2 }
            )
        }

        assertTrue(checks >= 2)
    }

    @Test
    fun `neutral planning rejects cancellation before the first request`() {
        assertThrows(OperationCancelledException::class.java) {
            NearbyTransferPlanner.planResolvedEntries(
                listOf(entry("one", "one.txt", directory = false)),
                OperationCancellation { true }
            )
        }
    }

    private fun entry(
        id: String,
        relativePath: String,
        directory: Boolean,
        sourceUri: String = "content://provider/$relativePath"
    ) = NearbyManifestEntry(
        id = id,
        relativePath = relativePath,
        sourceUri = sourceUri,
        directory = directory,
        sizeBytes = if (directory) 0 else 7,
        modifiedMillis = 11,
        fingerprint = if (directory) "d:0:11" else "f:7:11"
    )
}
