package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
class ApkmImportBackendTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val identityReader = AndroidApkIdentityReader { _, entryName ->
        AndroidApkIdentity(
            "com.example.app",
            42,
            if (entryName == "base.apk") null else "config.en"
        )
    }

    @Test
    fun `imports verified APKM without changing APK bytes`() {
        val key = signingKey(21)
        val base = signedApk("base-signed.apk", key)
        val split = signedApk("split-signed.apk", key)
        val input = apkm(base, split)
        val output = File(temporaryFolder.root, "imported.apks")

        val result = ApkmImportBackend(identityReader = identityReader).importApkm(
            ApkmImportRequest(input, output, File(temporaryFolder.root, "work"), 21)
        )

        assertTrue(result.sourceVerification.verified)
        assertTrue(result.outputVerification.verified)
        assertEquals(AndroidPackageContainerFormat.WIZEFILES_APKS, result.outputVerification.format)
        assertArrayEquals(base, entry(output, "base.apk"))
        assertArrayEquals(split, entry(output, "split_config.en.apk"))
        assertTrue(ZipFile(output).use { it.getEntry("metadata.json") != null })
        assertArrayEquals(entry(input, "info.json"), entry(output, "info.json"))
    }

    @Test
    fun `rejects APKM whose split uses another certificate`() {
        val input = apkm(
            signedApk("first.apk", signingKey(31)),
            signedApk("second.apk", signingKey(32))
        )
        val output = File(temporaryFolder.root, "rejected.apks")

        assertThrows(ApkmImportException::class.java) {
            ApkmImportBackend(identityReader = identityReader).importApkm(
                ApkmImportRequest(input, output, File(temporaryFolder.root, "reject-work"), 21)
            )
        }
        assertTrue(!output.exists())
    }

    private fun signedApk(name: String, key: ApkSigningKeyMaterial): ByteArray {
        val output = File(temporaryFolder.root, name)
        ApksigApkSigningBackend().sign(ApkSigningRequest(
            resource("unsigned.apk"),
            output,
            schemes = ApkSignatureSelection.of(
                ApkSignatureScheme.V1,
                ApkSignatureScheme.V2
            ),
            keyMaterial = key,
            minSdkVersion = 21
        ))
        return output.readBytes()
    }

    private fun apkm(base: ByteArray, split: ByteArray): File =
        File(temporaryFolder.root, "input-${base.size}-${split.first()}.apkm").also { file ->
            val info = JSONObject()
                .put("package_name", "com.example.app")
                .put("version_code", 42)
                .toString()
                .toByteArray()
            ZipOutputStream(file.outputStream()).use { zip ->
                listOf(
                    "base.apk" to base,
                    "split_config.en.apk" to split,
                    "info.json" to info
                ).forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }

    private fun entry(file: File, name: String): ByteArray = ZipFile(file).use { zip ->
        zip.getInputStream(requireNotNull(zip.getEntry(name))).use { it.readBytes() }
    }

    private fun signingKey(serial: Long): ApkSigningKeyMaterial {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=WizeFiles APKM Test $serial,O=Adonis Spices,C=LB")
        val now = Instant.now()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(serial),
            Date.from(now.minus(1, ChronoUnit.MINUTES)),
            Date.from(now.plus(1, ChronoUnit.DAYS)),
            subject,
            pair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider())
            .build(pair.private)
        val certificate: X509Certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(builder.build(signer))
        return ApkSigningKeyMaterial(pair.private, listOf(certificate))
    }

    private fun resource(name: String): File = File(
        requireNotNull(javaClass.classLoader?.getResource("apk-signing/$name")).toURI()
    )
}
