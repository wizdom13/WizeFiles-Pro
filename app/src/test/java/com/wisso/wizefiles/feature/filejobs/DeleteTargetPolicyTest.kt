// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.provider.rclone.createRcloneRootPath
import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteTargetPolicyTest {
    @Test
    fun `ordinary local delete uses the WizeFiles recycle bin`() {
        val local = Paths.get("/storage/emulated/0/Documents/report.txt")

        assertTrue(
            DeleteTargetPolicy.shouldUseLocalRecycleBin(
                local,
                DeleteOptions(),
                recycleBinEnabled = true
            )
        )
    }

    @Test
    fun `ordinary cloud delete uses provider semantics instead of local recycle bin`() {
        val cloud = createRcloneRootPath("box").resolve("Documents/report.txt")

        assertFalse(
            DeleteTargetPolicy.shouldUseLocalRecycleBin(
                cloud,
                DeleteOptions(),
                recycleBinEnabled = true
            )
        )
        assertEquals(DeleteTargetMode.PROVIDER_MANAGED, DeleteOptionsSupport.targetMode(cloud))
    }

    @Test
    fun `permanent local delete bypasses the WizeFiles recycle bin`() {
        val local = Paths.get("/storage/emulated/0/Documents/report.txt")

        assertFalse(
            DeleteTargetPolicy.shouldUseLocalRecycleBin(
                local,
                DeleteOptions(permanentDelete = true),
                recycleBinEnabled = true
            )
        )
    }

    @Test
    fun `every non-cloud remote backend is permanent only`() {
        val permanentBackends = listOf(
            DeleteBackend.FTP,
            DeleteBackend.SFTP,
            DeleteBackend.SMB,
            DeleteBackend.SAF,
            DeleteBackend.ARCHIVE,
            DeleteBackend.OTHER_PROVIDER
        )

        permanentBackends.forEach { backend ->
            assertEquals(
                backend.name,
                DeleteTargetMode.PERMANENT_ONLY,
                DeleteOptionsSupport.targetModeForBackends(listOf(backend))
            )
        }
    }

    @Test
    fun `local cloud and mixed selections retain distinct safety modes`() {
        assertEquals(
            DeleteTargetMode.LOCAL_TRASH,
            DeleteOptionsSupport.targetModeForBackends(listOf(DeleteBackend.LOCAL))
        )
        assertEquals(
            DeleteTargetMode.PROVIDER_MANAGED,
            DeleteOptionsSupport.targetModeForBackends(listOf(DeleteBackend.RCLONE))
        )
        assertEquals(
            DeleteTargetMode.MIXED,
            DeleteOptionsSupport.targetModeForBackends(
                listOf(DeleteBackend.LOCAL, DeleteBackend.FTP)
            )
        )
        assertEquals(
            DeleteTargetMode.MIXED,
            DeleteOptionsSupport.targetModeForBackends(
                listOf(DeleteBackend.RCLONE, DeleteBackend.SAF)
            )
        )
    }

    @Test
    fun `delete job partitions local recycling from provider deletion`() {
        val source = listOf(
            File("src/main/java/com/wisso/wizefiles/feature/filejobs/FileTransferOperationJobs.kt"),
            File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileTransferOperationJobs.kt")
        ).firstOrNull { it.exists() }?.readText()
            ?: error("Missing FileTransferOperationJobs.kt")

        assertTrue(source.contains("pendingDeletes.partition { (path, _) ->"))
        assertTrue(source.contains("DeleteTargetPolicy.shouldUseLocalRecycleBin("))
        assertTrue(source.contains("recycle(pathsToRecycleLocally)"))
        assertTrue(source.contains("for ((path, tracker) in pathsToDeleteThroughProvider)"))
    }

    @Test
    fun `only local paths advertise the optional permanent delete control`() {
        val local = Paths.get("/storage/emulated/0/Documents/report.txt")
        val cloud = createRcloneRootPath("box").resolve("Documents/report.txt")

        assertTrue(DeleteOptionsSupport.supportsPermanentDelete(local))
        assertFalse(DeleteOptionsSupport.supportsPermanentDelete(cloud))
        assertFalse(DeleteOptionsSupport.supportsPermanentDelete(listOf(local, cloud)))
    }
}
