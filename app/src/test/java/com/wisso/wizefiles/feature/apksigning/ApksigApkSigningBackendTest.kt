package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApksigApkSigningBackendTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val backend = ApksigApkSigningBackend()

    @Test
    fun `sign creates verified embedded schemes and detached v4 output`() {
        val input = resource("unsigned.apk")
        val output = File(temporaryFolder.root, "signed.apk")
        val idsig = File(temporaryFolder.root, "signed.apk.idsig")
        val schemes = ApkSignatureSelection.of(
            ApkSignatureScheme.V1,
            ApkSignatureScheme.V2,
            ApkSignatureScheme.V3,
            ApkSignatureScheme.V4
        )

        val result = backend.sign(
            ApkSigningRequest(
                input,
                output,
                idsig,
                schemes,
                signingKey(),
                minSdkVersion = 21
            )
        )

        assertTrue(output.isFile)
        assertTrue(idsig.isFile)
        assertTrue(result.verification.verified)
        assertEquals(schemes.schemes, result.verification.verifiedSchemes)
        assertEquals(1, result.verification.signerCertificateSha256.size)
    }

    @Test
    fun `sign refuses to overwrite the original or an existing output`() {
        val input = resource("unsigned.apk")
        val schemes = ApkSignatureSelection.of(ApkSignatureScheme.V2)
        val key = signingKey()

        assertThrows(IllegalArgumentException::class.java) {
            backend.sign(ApkSigningRequest(input, input, schemes = schemes, keyMaterial = key))
        }

        val existing = temporaryFolder.newFile("existing.apk")
        assertThrows(IllegalArgumentException::class.java) {
            backend.sign(ApkSigningRequest(input, existing, schemes = schemes, keyMaterial = key))
        }
        assertTrue(existing.isFile)
    }

    @Test
    fun `private key is redacted from diagnostics`() {
        val text = signingKey().toString()

        assertTrue("[REDACTED]" in text)
        assertFalse("sun.security" in text)
    }

    private fun signingKey(): ApkSigningKeyMaterial {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=WizeFiles Test,OU=Testing,O=Adonis Spices,C=LB")
        val now = Instant.now()
        val certificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.ONE,
            Date.from(now.minus(1, ChronoUnit.MINUTES)),
            Date.from(now.plus(1, ChronoUnit.DAYS)),
            subject,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider())
            .build(keyPair.private)
        val certificate: X509Certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(certificateBuilder.build(signer))
        certificate.verify(keyPair.public)
        return ApkSigningKeyMaterial(keyPair.private, listOf(certificate))
    }

    private fun resource(name: String): File = File(
        requireNotNull(javaClass.classLoader?.getResource("apk-signing/$name"))
            .toURI()
    )
}
