// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationMetadataServiceSourceTest {

    @Test
    fun metadataServiceExtractedToDedicatedFile() {
        val source = readSource("FileOperationMetadataService.kt")
        assertTrue(source.contains("class FileOperationMetadataService"))
        assertTrue(source.contains("walkFileTreeForMetadata"))
        assertTrue(source.contains("applyMetadataMutation"))
        assertTrue(source.contains("fun restoreSeLinuxContext("))
        assertTrue(source.contains("fun setGroup("))
        assertTrue(source.contains("fun setMode("))
        assertTrue(source.contains("fun setOwner("))
        assertTrue(source.contains("fun setSeLinuxContext("))
    }

    @Test
    fun executorDelegatesMetadataJobsToMetadataService() {
        val source = readSource("FileMetadataWriteCryptoJobs.kt")
        assertTrue(source.contains("FileOperationMetadataService(this, transferInfo, actionAllInfo)"))
        assertTrue(source.contains(".restoreSeLinuxContext(path, recursive)"))
        assertTrue(source.contains(".setGroup(path, group, recursive)"))
        assertTrue(source.contains(".setMode(path, recursive)"))
        assertTrue(source.contains(".setOwner(path, owner, recursive)"))
        assertTrue(source.contains(".setSeLinuxContext(path, seLinuxContext, recursive)"))
        assertFalse(source.contains("private fun FileOperationJob.setGroup("))
        assertFalse(source.contains("private fun FileOperationJob.setOwner("))
        assertFalse(source.contains("private fun FileOperationJob.setSeLinuxContext("))
        assertFalse(source.contains("private fun FileOperationJob.restoreSeLinuxContext("))
    }

    private fun readSource(fileName: String): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName"),
            File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException(fileName)
        return file.readText()
    }
}
