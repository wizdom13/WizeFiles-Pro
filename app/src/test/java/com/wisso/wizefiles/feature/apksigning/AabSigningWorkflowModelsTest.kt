// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AabSigningWorkflowModelsTest {
    @Test
    fun `key store spec has one credential source`() {
        val spec = AabSigningWorkflowSpec(
            sourceUri = "file:///input.aab",
            outputUri = "file:///output.aab",
            signingKeyUri = "file:///upload.p12"
        )
        assertEquals(listOf("file:///upload.p12"), spec.credentialUris())
    }

    @Test
    fun `raw PKCS8 requires X509 certificate`() {
        assertThrows(IllegalArgumentException::class.java) {
            AabSigningWorkflowSpec(
                sourceUri = "file:///input.aab",
                outputUri = "file:///output.aab",
                signingKeyUri = "file:///upload.pk8",
                keySource = AabSigningKeySource.PKCS8_CERTIFICATE
            )
        }
    }

    @Test
    fun `raw material persists both credential sources`() {
        val spec = AabSigningWorkflowSpec(
            sourceUri = "file:///input.aab",
            outputUri = "file:///output.aab",
            signingKeyUri = "file:///upload.pk8",
            certificateUri = "file:///upload.pem",
            keySource = AabSigningKeySource.PKCS8_CERTIFICATE
        )
        assertEquals(
            listOf("file:///upload.pk8", "file:///upload.pem"),
            spec.credentialUris()
        )
    }
}
