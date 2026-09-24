// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProviderScannerSourceTest {
    @Test
    fun `scanner routes FTP SFTP SMB and SAF through provider traversal`() {
        val source = sourceFile(
            "feature/filejobs/FileOperationScanner.kt"
        ).readText()

        assertTrue(source.contains("source.isDocumentPath"))
        assertTrue(source.contains("source.isFtpPath"))
        assertTrue(source.contains("source.isSftpPath"))
        assertTrue(source.contains("source.isSmbPath"))
        assertTrue(source.contains("ProviderTreeTraverser.walkPreOrder("))
        assertTrue(source.contains("        source,"))
        assertTrue(source.contains("resolveRetryOrCancel("))
        assertTrue(source.contains("scanInfo.restore(snapshot)"))
    }

    @Test
    fun `copy preflights every writable remote provider before planning`() {
        val source = sourceFile(
            "feature/filejobs/CopyFileOperationJob.kt"
        ).readText()

        assertTrue(source.contains("preflightRemoteWrite(targetDirectory)"))
        assertTrue(source.contains("isDocumentPath || isFtpPath || isSftpPath || isSmbPath"))
        assertTrue(source.contains(".wizefiles-write-test-"))
        assertTrue(
            source.indexOf("preflightRemoteWrite(targetDirectory)") <
                source.indexOf("planRcloneUploads(uploadPlans)")
        )
    }

    @Test
    fun `remote delete scan carries delete operation semantics`() {
        val source = sourceFile(
            "feature/filejobs/FileTransferOperationJobs.kt"
        ).readText()

        assertTrue(source.contains("R.plurals.file_job_delete_scan_notification_title_format,\n                    FileOperationType.DELETE"))
    }

    @Test
    fun `provider traversal does not require a local app path`() {
        val source = sourceFile(
            "storage/provider/ProviderTreeTraverser.kt"
        ).readText()

        assertTrue(source.contains("Files.readAttributes("))
        assertTrue(source.contains("Files.newDirectoryStream(path)"))
        assertFalse(source.contains("LocalAppPath"))
        assertFalse(source.contains("Unsupported provider path"))
    }

    private fun sourceFile(relativePath: String): File = listOf(
        File("src/main/java/com/wisso/wizefiles/$relativePath"),
        File("app/src/main/java/com/wisso/wizefiles/$relativePath")
    ).firstOrNull { it.exists() } ?: error("Missing source file: $relativePath")
}
