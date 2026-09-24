package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidSplitSetVerifierTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `requires matching package version and certificate across all APKs`() {
        val valid = container(byteArrayOf(1), byteArrayOf(2))
        val verifier = AndroidSplitSetVerifier(
            backend = FakeBackend(),
            identityReader = AndroidApkIdentityReader { apk, _ ->
                val marker = apk.readBytes().single()
                AndroidApkIdentity(
                    packageName = "com.example.app",
                    versionCode = 42,
                    splitName = if (marker.toInt() == 1) null else "config.en"
                )
            }
        )

        val report = verifier.verify(valid, temporaryFolder.newFolder("valid-staging"))

        assertTrue(report.verified)
        assertTrue(report.errors.isEmpty())
        assertTrue(report.apks.size == 2)
    }

    @Test
    fun `certificate digest comparison is case insensitive`() {
        val verifier = AndroidSplitSetVerifier(
            backend = FakeBackend(lowercaseMarker = 2),
            identityReader = AndroidApkIdentityReader { apk, _ ->
                AndroidApkIdentity(
                    "com.example.app",
                    42,
                    if (apk.readBytes().single().toInt() == 1) null else "config.en"
                )
            }
        )

        val report = verifier.verify(
            container(byteArrayOf(1), byteArrayOf(2)),
            temporaryFolder.newFolder("case-staging")
        )

        assertTrue(report.verified)
        assertEquals(listOf("CERTIFICATE"), report.signerCertificateSha256)
    }

    @Test
    fun `rejects one differently signed split`() {
        val verifier = AndroidSplitSetVerifier(
            backend = FakeBackend(mismatchMarker = 2),
            identityReader = AndroidApkIdentityReader { apk, _ ->
                AndroidApkIdentity(
                    "com.example.app",
                    42,
                    if (apk.readBytes().single().toInt() == 1) null else "config.en"
                )
            }
        )

        val report = verifier.verify(
            container(byteArrayOf(1), byteArrayOf(2)),
            temporaryFolder.newFolder("mismatch-staging")
        )

        assertFalse(report.verified)
        assertTrue(report.errors.any { "certificate" in it })
    }

    private fun container(base: ByteArray, split: ByteArray): File {
        val metadata = JSONObject()
            .put("formatVersion", 1)
            .put("packageName", "com.example.app")
            .put("versionCode", 42)
            .put("apks", JSONArray().apply {
                put(JSONObject().put("file", "base.apk").put("sha256", sha256(base)))
                put(JSONObject().put("file", "split_config.en.apk").put("sha256", sha256(split)))
            })
        return temporaryFolder.newFile("set.apks").also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                listOf(
                    "base.apk" to base,
                    "split_config.en.apk" to split,
                    "metadata.json" to metadata.toString().toByteArray()
                ).forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private class FakeBackend(
        private val mismatchMarker: Int? = null,
        private val lowercaseMarker: Int? = null
    ) : ApkSigningBackend {
        override fun sign(request: ApkSigningRequest): ApkSigningResult = error("Not used")

        override fun verify(request: ApkVerificationRequest): ApkVerificationReport {
            val marker = request.apk.readBytes().single().toInt()
            return ApkVerificationReport(
                verified = true,
                verifiedSchemes = setOf(ApkSignatureScheme.V2),
                signerCertificateSha256 = listOf(when (marker) {
                    mismatchMarker -> "DIFFERENT"
                    lowercaseMarker -> "certificate"
                    else -> "CERTIFICATE"
                }),
                errors = emptyList(),
                warnings = emptyList()
            )
        }
    }
}
