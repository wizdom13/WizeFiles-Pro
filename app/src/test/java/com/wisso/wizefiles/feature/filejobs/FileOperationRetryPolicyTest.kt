// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.IOException
import java.io.InterruptedIOException
import com.wisso.wizefiles.provider.common.InvalidFileNameException
import org.junit.Assert.assertEquals
import org.junit.Test

class FileOperationRetryPolicyTest {

    @Test
    fun transientFailureRetriesUntilMaxAttempts() {
        val context = context(java.io.EOFException("network"), retryCount = 0)
        assertEquals(FileOperationFailureDecision.Retry, FileOperationStatePolicy.decide(context))
    }

    @Test
    fun retryExhaustionAborts() {
        val context = context(java.io.EOFException("network"), retryCount = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS)
        assertEquals(FileOperationFailureDecision.Abort, FileOperationStatePolicy.decide(context))
    }

    @Test
    fun cancellationIsNotRetried() {
        val context = context(InterruptedIOException(), retryCount = 0)
        assertEquals(FileOperationFailureDecision.Abort, FileOperationStatePolicy.decide(context))
    }

    @Test
    fun validationFailureIsNotRetried() {
        val context = context(InvalidFileNameException("bad"), retryCount = 0)
        assertEquals(FileOperationFailureDecision.Abort, FileOperationStatePolicy.decide(context))
    }

    private fun context(exception: IOException, retryCount: Int) = FileOperationFailureContext(
        type = FileOperationType.COPY,
        sourcePath = "/source",
        targetPath = "/target",
        phase = FileOperationPhase.EXECUTE,
        exception = exception,
        retryCount = retryCount,
        maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
        hasPartialOutput = false,
        userCancelled = false,
        allowSkip = false
    )
}
