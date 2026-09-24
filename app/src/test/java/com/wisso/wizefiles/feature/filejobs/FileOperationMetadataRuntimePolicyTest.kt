// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationMetadataRuntimePolicyTest {

    @Test
    fun optionalMetadataFailureAfterContentSuccessUsesCleanupAndContinue() {
        val decision = FileOperationStatePolicy.decide(
            context(metadataRequired = false, metadataOptional = true, hasPartialOutput = true)
        )
        assertEquals(FileOperationFailureDecision.CleanupAndContinue, decision)
    }

    @Test
    fun requiredMetadataFailureWithPartialOutputRollsBackAfterRetryExhaustion() {
        val decision = FileOperationStatePolicy.decide(
            context(
                metadataRequired = true,
                retryCount = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                hasPartialOutput = true
            )
        )
        assertEquals(FileOperationFailureDecision.RollbackAndAbort, decision)
    }

    @Test
    fun runtimeMetadataCallsitesPassExplicitRequiredOptionalFlags() {
        val metadataSource = readSource("FileOperationMetadataService.kt")
        val executorSource = readSource("FileMetadataWriteCryptoJobs.kt")
        val jobSource = readSource("FileOperationJob.kt")
        assertTrue(metadataSource.contains("metadataRequired = false"))
        assertTrue(metadataSource.contains("metadataRequired = metadataRequired"))
        assertTrue(metadataSource.contains("metadataOptional = !metadataRequired"))
        assertTrue(metadataSource.contains("recordMetadataWarning"))
        assertTrue(executorSource.contains("reportMetadataWarnings(transferInfo)"))
        assertTrue(jobSource.contains("file_job_completion_with_metadata_warning"))
    }

    private fun context(
        metadataRequired: Boolean,
        metadataOptional: Boolean = false,
        retryCount: Int = 0,
        hasPartialOutput: Boolean
    ) = FileOperationFailureContext(
        type = FileOperationType.METADATA,
        sourcePath = "/source",
        targetPath = "/target",
        phase = FileOperationPhase.FINALIZE,
        exception = IOException("metadata failure"),
        retryCount = retryCount,
        maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
        hasPartialOutput = hasPartialOutput,
        userCancelled = false,
        metadataRequired = metadataRequired,
        metadataOptional = metadataOptional,
        allowSkip = true
    )

    private fun readSource(fileName: String): String = listOf(
        java.io.File("src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName"),
        java.io.File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName")
    ).first { it.exists() }.readText()
}
