// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApkSigningWorkflowModelsTest {
    @Test
    fun `workflow defaults to embedded schemes and keep both`() {
        val spec = ApkSigningWorkflowSpec(
            sourceUri = "file:///input.apk",
            outputUri = "file:///input-signed.apk",
            keyStoreUri = "file:///key.p12"
        )

        assertEquals(
            setOf(ApkSignatureScheme.V1, ApkSignatureScheme.V2, ApkSignatureScheme.V3),
            spec.schemes
        )
        assertEquals(ApkSigningOutputConflictPolicy.KEEP_BOTH, spec.conflictPolicy)
        assertEquals(spec.schemes, spec.signatureSelection().schemes)
    }

    @Test
    fun `workflow cannot overwrite its input or request invalid v4`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApkSigningWorkflowSpec(
                sourceUri = "file:///same.apk",
                outputUri = "file:///same.apk",
                keyStoreUri = "file:///key.p12"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApkSigningWorkflowSpec(
                sourceUri = "file:///input.apk",
                outputUri = "file:///output.apk",
                keyStoreUri = "file:///key.p12",
                schemes = setOf(ApkSignatureScheme.V4)
            )
        }
    }
}
