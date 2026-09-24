// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationTransferEngineSourceTest {

    @Test
    fun transferEnginesExtractedToDedicatedFile() {
        val source = readSource("FileOperationTransferEngine.kt") +
            readSource("CopyFileOperationTransferEngine.kt") +
            readSource("MoveFileOperationTransferEngine.kt")
        assertTrue(source.contains("class CopyFileOperationTransferEngine"))
        assertTrue(source.contains("class MoveFileOperationTransferEngine"))
        assertTrue(source.contains("selectTransferBackend"))
    }

    @Test
    fun executorDelegatesCopyAndMoveTraversalToTransferEngines() {
        val source = readSource("FileTransferOperationJobs.kt") +
            readSource("ArchiveFileOperationJob.kt") +
            readSource("CopyFileOperationJob.kt") +
            readSource("CreateFileOperationJob.kt") +
            readSource("MoveFileOperationJob.kt")
        assertTrue(source.contains("CopyFileOperationTransferEngine("))
        assertTrue(source.contains("MoveFileOperationTransferEngine("))
        assertTrue(source.contains("transferEngine.copyRecursively(source, target)"))
        assertTrue(source.contains("transferEngine.moveRecursively(source, target)"))
    }

    private fun readSource(fileName: String): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName"),
            File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException(fileName)
        return file.readText()
    }
}
