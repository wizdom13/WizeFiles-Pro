// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationRetryPolicySourceTest {

    @Test
    fun retryPolicyExtractedToDedicatedFile() {
        val source = readSource("FileOperationErrorPolicy.kt")
        assertTrue(source.contains("runWithRetryPolicy"))
        assertTrue(source.contains("resolveRetrySkipOrCancel"))
    }

    @Test
    fun executorUsesRetryPolicyHelpersForSimpleOperations() {
        val source = readSource("FileTransferOperationJobs.kt") +
            readSource("FileOpenRenameJobs.kt") +
            readSource("FileMetadataWriteCryptoJobs.kt")
        assertTrue(source.contains("runWithRetryPolicy("))
        assertTrue(source.contains("runWithRetryPolicyResult("))
    }

    private fun readSource(fileName: String): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName"),
            File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException(fileName)
        return file.readText()
    }
}
