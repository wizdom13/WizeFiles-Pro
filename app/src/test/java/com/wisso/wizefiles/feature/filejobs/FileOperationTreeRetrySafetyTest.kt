// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.InterruptedIOException
import com.wisso.wizefiles.provider.common.InvalidFileNameException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationTreeRetrySafetyTest {

    @Test
    fun retryStopsAtMaxAttempts() {
        val decision = FileOperationStatePolicy.decide(
            FileOperationFailureContext(
                type = FileOperationType.DELETE,
                sourcePath = "/path",
                targetPath = null,
                phase = FileOperationPhase.EXECUTE,
                exception = java.io.EOFException("transient"),
                retryCount = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                hasPartialOutput = false,
                userCancelled = false,
                allowSkip = true
            )
        )
        assertEquals(FileOperationFailureDecision.Abort, decision)
    }

    @Test
    fun cancellationAndValidationAreNotRetried() {
        val cancellation = FileOperationStatePolicy.decide(
            FileOperationFailureContext(
                type = FileOperationType.COPY,
                sourcePath = "/source",
                targetPath = "/target",
                phase = FileOperationPhase.EXECUTE,
                exception = InterruptedIOException(),
                retryCount = 0,
                maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                hasPartialOutput = false,
                userCancelled = false,
                allowSkip = true
            )
        )
        val validation = FileOperationStatePolicy.decide(
            FileOperationFailureContext(
                type = FileOperationType.COPY,
                sourcePath = "/source",
                targetPath = "/target",
                phase = FileOperationPhase.EXECUTE,
                exception = InvalidFileNameException("invalid"),
                retryCount = 0,
                maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                hasPartialOutput = false,
                userCancelled = false,
                allowSkip = true
            )
        )
        assertEquals(FileOperationFailureDecision.Abort, cancellation)
        assertEquals(FileOperationFailureDecision.Abort, validation)
    }

    @Test
    fun treeWalkRetryUsesLoopsNotRecursiveReentry() {
        val transferSource = readSource("FileOperationTransferEngine.kt")
        val metadataSource = readSource("FileOperationMetadataService.kt")
        val executorSource = readSource("FileTransferOperationJobs.kt")

        assertTrue(transferSource.contains("while (true)"))
        assertTrue(metadataSource.contains("while (true)"))
        assertTrue(executorSource.contains("while (true)"))
        assertFalse(transferSource.contains("visitFile(file, Files.readAttributes"))
        assertFalse(executorSource.contains("visitFile(file, Files.readAttributes"))
        assertFalse(metadataSource.contains("FileVisitResult.TERMINATE"))
    }

    private fun readSource(fileName: String): String = listOf(
        java.io.File("src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName"),
        java.io.File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName")
    ).first { it.exists() }.readText()
}
