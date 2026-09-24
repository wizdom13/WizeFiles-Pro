package com.wisso.wizefiles.feature.apksigning

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkSigningKeyStoreServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val service = ApkSigningKeyStoreService()

    @Test
    fun `generated PKCS12 can be inspected and loaded for signing`() {
        val output = File(temporaryFolder.root, "release.p12")
        ApkSigningSecrets("store-pass".toCharArray(), "key-pass".toCharArray()).use { secrets ->
            val generated = service.generatePkcs12(
                output,
                ApkSigningKeyGenerationRequest(
                    alias = "release",
                    subjectDistinguishedName = "CN=WizeFiles Release,O=WizeFiles,C=LB",
                    validityYears = 2,
                    rsaKeySize = 2048
                ),
                secrets
            )
            assertEquals("release", generated.alias)
            assertTrue(generated.certificateSha256.length == 64)
        }

        ApkSigningSecrets("store-pass".toCharArray(), "key-pass".toCharArray()).use { secrets ->
            val aliases = service.aliases(output, ApkKeyStoreFormat.PKCS12, secrets)
            assertEquals(listOf("release"), aliases.map(ApkSigningKeyAlias::alias))
            val material = service.load(output, ApkKeyStoreFormat.PKCS12, "release", secrets)
            assertEquals("RSA", material.privateKey.algorithm)
            assertEquals(1, material.certificates.size)
        }
    }

    @Test
    fun `wrong key store password is rejected`() {
        val output = File(temporaryFolder.root, "release.p12")
        ApkSigningSecrets("correct".toCharArray()).use { secrets ->
            service.generatePkcs12(
                output,
                ApkSigningKeyGenerationRequest("release", "CN=WizeFiles Test"),
                secrets
            )
        }

        ApkSigningSecrets("wrong".toCharArray()).use { secrets ->
            assertThrows(Exception::class.java) {
                service.aliases(output, ApkKeyStoreFormat.PKCS12, secrets)
            }
        }
    }
}
