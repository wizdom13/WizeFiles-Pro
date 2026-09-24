package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JarAabSigningBackendTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `signs verifies and replaces previous upload signature`() {
        val input = createAab("input.aab")
        val material = signingMaterial("first.p12")
        val first = File(temporary.root, "first-signed.aab")
        val backend = JarAabSigningBackend()

        val result = backend.sign(AabSigningRequest(input, first, material))

        assertTrue(result.verification.verified)
        assertTrue(result.verification.signedEntryCount >= 3)
        JarFile(first, true).use { jar ->
            val entry = jar.getJarEntry("base/resources.pb")
            jar.getInputStream(entry).use { it.readBytes() }
            assertFalse(entry.certificates.isNullOrEmpty())
        }

        val second = File(temporary.root, "second-signed.aab")
        val secondResult = backend.sign(AabSigningRequest(first, second, signingMaterial("second.p12")))
        assertTrue(secondResult.verification.verified)
        ZipFile(second).use { zip ->
            assertEquals(1, zip.entries().asSequence().count { it.name.endsWith(".SF") })
            assertEquals(1, zip.entries().asSequence().count { it.name.endsWith(".RSA") })
        }
    }

    @Test
    fun `tampering is reported instead of accepted`() {
        val input = createAab("input.aab")
        val signed = File(temporary.root, "signed.aab")
        JarAabSigningBackend().sign(
            AabSigningRequest(input, signed, signingMaterial("signing.p12"))
        )
        val tampered = File(temporary.root, "tampered.aab")
        rewrite(signed, tampered, "base/resources.pb", "changed".toByteArray())

        val report = JarAabSigningBackend().verify(AabVerificationRequest(tampered))

        assertFalse(report.verified)
        assertTrue(report.errors.any { "digest" in it || "verifier" in it })
    }

    @Test
    fun `oversized JAR signature metadata is rejected before heap allocation`() {
        val input = File(temporary.root, "oversized-signature.aab")
        val oversized = ByteArray(4 * 1024 * 1024 + 1)
        java.util.Random(7).nextBytes(oversized)
        ZipOutputStream(input.outputStream().buffered()).use { zip ->
            zip.write("BundleConfig.pb", byteArrayOf(0x0A, 0x01, 0x01))
            zip.write("base/manifest/AndroidManifest.xml", byteArrayOf(0x0A, 0x02, 0x01))
            zip.write("META-INF/MANIFEST.MF", oversized)
        }

        val failure = assertThrows(AabSigningBackendException::class.java) {
            JarAabSigningBackend().verify(AabVerificationRequest(input))
        }

        assertTrue(failure.message.orEmpty().contains("signature metadata size"))
    }

    @Test
    fun `original and existing output are protected`() {
        val input = createAab("input.aab")
        val backend = JarAabSigningBackend()
        assertThrows(IllegalArgumentException::class.java) {
            backend.sign(AabSigningRequest(input, input, signingMaterial("one.p12")))
        }
        val existing = File(temporary.root, "existing.aab").apply { writeText("occupied") }
        assertThrows(IllegalArgumentException::class.java) {
            backend.sign(AabSigningRequest(input, existing, signingMaterial("two.p12")))
        }
    }

    private fun createAab(name: String): File = File(temporary.root, name).also { file ->
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.write("BundleConfig.pb", byteArrayOf(0x0A, 0x01, 0x01))
            zip.write("base/manifest/AndroidManifest.xml", byteArrayOf(0x0A, 0x02, 0x01))
            zip.write("base/resources.pb", "resources".toByteArray())
        }
    }

    private fun signingMaterial(name: String): ApkSigningKeyMaterial {
        val store = File(temporary.root, name)
        val request = ApkSigningKeyGenerationRequest("upload", "CN=Upload Key", 10, 2048)
        ApkSigningSecrets("changeit".toCharArray()).use { secrets ->
            ApkSigningKeyStoreService().generatePkcs12(store, request, secrets)
        }
        return ApkSigningSecrets("changeit".toCharArray()).use { secrets ->
            ApkSigningKeyStoreService().load(
                store,
                ApkKeyStoreFormat.PKCS12,
                "upload",
                secrets
            )
        }
    }

    private fun rewrite(source: File, target: File, changedName: String, bytes: ByteArray) {
        ZipFile(source).use { input ->
            ZipOutputStream(target.outputStream().buffered()).use { output ->
                input.entries().asSequence().forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) {
                        if (entry.name == changedName) output.write(bytes)
                        else input.getInputStream(entry).use { it.copyTo(output) }
                    }
                    output.closeEntry()
                }
            }
        }
    }

    private fun ZipOutputStream.write(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
