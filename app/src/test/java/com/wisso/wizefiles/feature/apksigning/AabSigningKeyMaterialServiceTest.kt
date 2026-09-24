// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AabSigningKeyMaterialServiceTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `loads matching PKCS8 and X509 material`() {
        val material = material("source.p12", "source")
        val key = File(temporary.root, "upload.pk8").apply {
            writeBytes(material.privateKey.encoded)
        }
        val certificate = File(temporary.root, "upload.cer").apply {
            writeBytes(material.certificates.first().encoded)
        }

        val loaded = ApkSigningSecrets(CharArray(0)).use { secrets ->
            AabSigningKeyMaterialService().load(
                key,
                certificate,
                AabSigningKeySource.PKCS8_CERTIFICATE,
                ApkKeyStoreFormat.PKCS12,
                "",
                secrets
            )
        }

        assertEquals(material.privateKey.algorithm, loaded.privateKey.algorithm)
        assertEquals(material.certificates.first(), loaded.certificates.first())
    }

    @Test
    fun `rejects certificate that does not match private key`() {
        val first = material("first.p12", "first")
        val second = material("second.p12", "second")
        val key = File(temporary.root, "first.pk8").apply { writeBytes(first.privateKey.encoded) }
        val certificate = File(temporary.root, "second.cer").apply {
            writeBytes(second.certificates.first().encoded)
        }

        assertThrows(IllegalArgumentException::class.java) {
            ApkSigningSecrets(CharArray(0)).use { secrets ->
                AabSigningKeyMaterialService().load(
                    key,
                    certificate,
                    AabSigningKeySource.PKCS8_CERTIFICATE,
                    ApkKeyStoreFormat.PKCS12,
                    "",
                    secrets
                )
            }
        }
    }

    private fun material(fileName: String, alias: String): ApkSigningKeyMaterial {
        val store = File(temporary.root, fileName)
        ApkSigningSecrets("changeit".toCharArray()).use { secrets ->
            ApkSigningKeyStoreService().generatePkcs12(
                store,
                ApkSigningKeyGenerationRequest(alias, "CN=$alias", 10, 2048),
                secrets
            )
        }
        return ApkSigningSecrets("changeit".toCharArray()).use { secrets ->
            ApkSigningKeyStoreService().load(
                store,
                ApkKeyStoreFormat.PKCS12,
                alias,
                secrets
            )
        }
    }
}
