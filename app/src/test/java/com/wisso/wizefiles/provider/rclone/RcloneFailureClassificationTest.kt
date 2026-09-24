// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.rclone

import com.wisso.wizefiles.feature.filejobs.ProviderFailureBoundary
import com.wisso.wizefiles.storage.FileOperationFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneFailureClassificationTest {
    @Test fun `rclone status failures cross the shared provider boundary`() {
        val cases = listOf(
            RcloneException("op", 403, "denied") to FileOperationFailureKind.PERMISSION_REVOKED,
            RcloneException("op", 404, "not found") to FileOperationFailureKind.STALE_RESOURCE,
            RcloneException("op", 429, "busy") to FileOperationFailureKind.PROVIDER_UNAVAILABLE,
            RcloneException("op", 503, "offline") to FileOperationFailureKind.PROVIDER_UNAVAILABLE,
            RcloneException("op", 400, "bad request") to FileOperationFailureKind.PERMANENT
        )
        cases.forEach { (exception, kind) ->
            assertEquals(kind, ProviderFailureBoundary.map(exception).kind)
        }
    }

    @Test fun `rclone errors preserve bounded native response detail`() {
        val failure = RcloneException(
            "operations/copyfile",
            400,
            """{"error":"remote rejected the destination"}"""
        )
        assertTrue(failure.message.orEmpty().contains("remote rejected the destination"))
        assertTrue(failure.message.orEmpty().length <= 600)
    }
}
