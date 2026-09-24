package com.wisso.wizefiles.feature.apksigning

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApksigApkVerifierTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val backend = ApksigApkSigningBackend()

    @Test
    fun `golden fixtures expose their exact verified schemes`() {
        val fixtures = listOf(
            Fixture("v1.apk", 21, setOf(ApkSignatureScheme.V1)),
            Fixture("v2.apk", 24, setOf(ApkSignatureScheme.V2)),
            Fixture(
                "v3.apk",
                24,
                setOf(ApkSignatureScheme.V2, ApkSignatureScheme.V3)
            ),
            Fixture(
                "v4.apk",
                24,
                setOf(
                    ApkSignatureScheme.V2,
                    ApkSignatureScheme.V3,
                    ApkSignatureScheme.V4
                ),
                "v4.apk.idsig"
            )
        )

        val fingerprints = fixtures.map { fixture ->
            val report = backend.verify(
                ApkVerificationRequest(
                    resource(fixture.apk),
                    fixture.idsig?.let(::resource),
                    fixture.minSdk
                )
            )
            assertTrue(fixture.apk, report.verified)
            assertEquals(fixture.apk, fixture.schemes, report.verifiedSchemes)
            assertTrue(fixture.apk, report.errors.isEmpty())
            assertEquals(fixture.apk, 1, report.signerCertificateSha256.size)
            report.signerCertificateSha256.single()
        }
        assertEquals(1, fingerprints.distinct().size)
    }

    @Test
    fun `v4 is not reported without its detached idsig`() {
        val report = backend.verify(
            ApkVerificationRequest(resource("v4.apk"), minSdkVersion = 24)
        )

        assertTrue(report.verified)
        assertFalse(report.hasVerifiedScheme(ApkSignatureScheme.V4))
        assertEquals(
            setOf(ApkSignatureScheme.V2, ApkSignatureScheme.V3),
            report.verifiedSchemes
        )
    }

    @Test
    fun `tampering after signing fails verification`() {
        val bytes = resource("v3.apk").readBytes()
        val v3BlockId = byteArrayOf(0xC0.toByte(), 0x68, 0x53, 0xF0.toByte())
        val blockOffset = bytes.indexOf(v3BlockId)
        check(blockOffset >= 0)
        bytes[blockOffset + 24] = (bytes[blockOffset + 24].toInt() xor 0x01).toByte()
        val tampered = temporaryFolder.newFile("tampered.apk").apply { writeBytes(bytes) }

        val report = backend.verify(ApkVerificationRequest(tampered, minSdkVersion = 24))

        assertFalse(report.verified)
        assertFalse(report.hasVerifiedScheme(ApkSignatureScheme.V3))
    }

    private fun resource(name: String): File = File(
        requireNotNull(javaClass.classLoader?.getResource("apk-signing/$name"))
            .toURI()
    )

    private data class Fixture(
        val apk: String,
        val minSdk: Int,
        val schemes: Set<ApkSignatureScheme>,
        val idsig: String? = null
    )

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        outer@ for (index in 0..size - needle.size) {
            for (offset in needle.indices) {
                if (this[index + offset] != needle[offset]) continue@outer
            }
            return index
        }
        return -1
    }
}
