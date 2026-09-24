// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogCancellationSourceTest {
    @Test
    fun `error dialog dismissal requests transfer cancellation`() {
        val policy = sourceFile("FileOperationErrorPolicy.kt").readText()

        assertTrue(policy.contains("FileOperationErrorAction.CANCELED,\n        FileOperationErrorAction.NEUTRAL -> {\n            requestCancellation()"))
        assertFalse(policy.contains("FileOperationErrorAction.CANCELED -> {\n            onSkip()"))
    }

    @Test
    fun `conflict dialog dismissal requests transfer cancellation`() {
        val policy = sourceFile("FileOperationConflictPolicy.kt").readText()

        assertTrue(policy.contains("FileOperationConflictAction.CANCELED,\n        FileOperationConflictAction.CANCEL -> {\n            requestCancellation()"))
        assertFalse(policy.contains("FileOperationConflictAction.CANCELED -> FileOperationTargetConflictResolution.Skip"))
    }

    @Test
    fun `handled permission failures are warnings rather than unexpected errors`() {
        val policy = sourceFile("FileOperationErrorPolicy.kt").readText()

        assertTrue(policy.contains("FileOperationFailureCategory.PERMISSION"))
        assertTrue(policy.contains("AppLog.w("))
        assertTrue(policy.contains("Permission failure is handled"))
    }

    @Test
    fun `permission decisions are deduplicated within one operation`() {
        val job = sourceFile("FileOperationJob.kt").readText()
        val policy = sourceFile("FileOperationErrorPolicy.kt").readText()
        val transfer = sourceFile("FileOperationTransferEngine.kt").readText()

        assertTrue(job.contains("permissionDecisionFingerprints"))
        assertTrue(job.contains("claimPermissionErrorDecision"))
        assertTrue(policy.contains("!claimPermissionErrorDecision(path, exception)"))
        assertTrue(transfer.contains("!claimPermissionErrorDecision(resolvedTarget, e)"))
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/wisso/wizefiles/feature/filejobs/$name"),
        File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/$name")
    ).firstOrNull { it.exists() } ?: error("Missing source file: $name")
}
