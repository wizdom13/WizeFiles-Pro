// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkSigningUiPolicyTest {
    @Test
    fun `default embedded selection is valid`() {
        assertNull(
            validateApkSigningUiSelection(
                setOf(ApkSignatureScheme.V1, ApkSignatureScheme.V2, ApkSignatureScheme.V3),
                ""
            )
        )
    }

    @Test
    fun `detached v4 requires v2 or v3`() {
        assertEquals(
            ApkSigningUiValidationError.V4_REQUIRES_V2_OR_V3,
            validateApkSigningUiSelection(
                setOf(ApkSignatureScheme.V1, ApkSignatureScheme.V4),
                "21"
            )
        )
    }

    @Test
    fun `selection requires an embedded scheme and positive optional minimum sdk`() {
        assertEquals(
            ApkSigningUiValidationError.EMBEDDED_SCHEME_REQUIRED,
            validateApkSigningUiSelection(emptySet(), "")
        )
        assertEquals(
            ApkSigningUiValidationError.INVALID_MINIMUM_SDK,
            validateApkSigningUiSelection(setOf(ApkSignatureScheme.V2), "0")
        )
    }

    @Test
    fun `signed output name is deterministic`() {
        assertEquals("client-signed.apk", signedApkFileName("client.apk"))
        assertEquals("client.APK-signed.apk", signedApkFileName("client.APK"))
    }
}
