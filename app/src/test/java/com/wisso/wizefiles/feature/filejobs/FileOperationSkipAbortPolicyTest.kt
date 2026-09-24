package com.wisso.wizefiles.feature.filejobs

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class FileOperationSkipAbortPolicyTest {

    @Test
    fun optionalMetadataFailureCanContinueWithCleanupDecision() {
        val decision = FileOperationStatePolicy.decide(baseContext(metadataRequired = false).copy(allowSkip = true))
        assertEquals(FileOperationFailureDecision.Skip, decision)
    }

    @Test
    fun requiredMetadataFailureRollsBackAndAbortsWhenPartialOutputExists() {
        val decision = FileOperationStatePolicy.decide(
            baseContext(metadataRequired = true).copy(hasPartialOutput = true, retryCount = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS)
        )
        assertEquals(FileOperationFailureDecision.RollbackAndAbort, decision)
    }

    @Test
    fun unsupportedProviderFailureAborts() {
        val decision = FileOperationStatePolicy.decide(
            baseContext().copy(exception = IOException("unsupported"), allowSkip = false, userCancelled = true)
        )
        assertEquals(FileOperationFailureDecision.Abort, decision)
    }

    private fun baseContext(metadataRequired: Boolean = false) = FileOperationFailureContext(
        type = FileOperationType.METADATA,
        sourcePath = "/source",
        targetPath = "/target",
        phase = FileOperationPhase.FINALIZE,
        exception = IOException("metadata"),
        retryCount = 0,
        maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
        hasPartialOutput = false,
        userCancelled = false,
        metadataRequired = metadataRequired,
        allowSkip = false
    )
}
