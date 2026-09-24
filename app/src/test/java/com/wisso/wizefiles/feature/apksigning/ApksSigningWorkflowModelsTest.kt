package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApksSigningWorkflowModelsTest {
    @Test
    fun `defaults to embedded v1 v2 and v3 schemes`() {
        val spec = spec()

        assertEquals(
            setOf(ApkSignatureScheme.V1, ApkSignatureScheme.V2, ApkSignatureScheme.V3),
            spec.schemes
        )
    }

    @Test
    fun `detached v4 is prohibited for APKS`() {
        assertThrows(IllegalArgumentException::class.java) {
            spec(schemes = setOf(ApkSignatureScheme.V2, ApkSignatureScheme.V4))
        }
    }

    @Test
    fun `raw PKCS8 persists both credential sources`() {
        val spec = spec(
            certificateUri = "file:///certificate.pem",
            keySource = AabSigningKeySource.PKCS8_CERTIFICATE
        )

        assertEquals(
            listOf("file:///signing.pk8", "file:///certificate.pem"),
            spec.credentialUris()
        )
    }

    private fun spec(
        schemes: Set<ApkSignatureScheme> = setOf(
            ApkSignatureScheme.V1,
            ApkSignatureScheme.V2,
            ApkSignatureScheme.V3
        ),
        certificateUri: String = "",
        keySource: AabSigningKeySource = AabSigningKeySource.KEY_STORE
    ) = ApksSigningWorkflowSpec(
        sourceUri = "file:///input.apks",
        outputUri = "file:///output.apks",
        signingKeyUri = "file:///signing.pk8",
        certificateUri = certificateUri,
        keySource = keySource,
        schemes = schemes
    )
}
