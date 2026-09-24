package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XapkSigningWorkflowModelsTest {
    @Test
    fun `XAPK defaults to embedded v1 v2 and v3`() {
        val spec = XapkSigningWorkflowSpec(
            sourceUri = "file:///input.xapk",
            outputUri = "file:///output.xapk",
            signingKeyUri = "file:///key.p12"
        )
        assertEquals(
            setOf(ApkSignatureScheme.V1, ApkSignatureScheme.V2, ApkSignatureScheme.V3),
            spec.schemes
        )
    }

    @Test
    fun `XAPK rejects detached v4`() {
        assertThrows(IllegalArgumentException::class.java) {
            XapkSigningWorkflowSpec(
                sourceUri = "file:///input.xapk",
                outputUri = "file:///output.xapk",
                signingKeyUri = "file:///key.p12",
                schemes = setOf(ApkSignatureScheme.V2, ApkSignatureScheme.V4)
            )
        }
    }
}
