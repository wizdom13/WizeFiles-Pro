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
import org.json.JSONArray
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
class XapkArchiveSigningBackendTest {
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
    fun `re-signs every APK while preserving manifest and expansion bytes`() {
        val input = xapk(includeUnlistedApk = false)
        val originalManifest = entry(input, "manifest.json")
        val originalExpansion = entry(input, OBB)
        val output = File(temporaryFolder.root, "signed.xapk")

        val result = XapkArchiveSigningBackend(identityReader = identityReader).sign(
            XapkSigningRequest(
                input,
                output,
                File(temporaryFolder.root, "work"),
                ApkSignatureSelection.of(
                    ApkSignatureScheme.V1,
                    ApkSignatureScheme.V2,
                    ApkSignatureScheme.V3
                ),
                signingKey(),
                minSdkVersion = 21
            )
        )

        assertTrue(result.verification.verified)
        assertEquals(2, result.verification.apks.size)
        assertArrayEquals(originalManifest, entry(output, "manifest.json"))
        assertArrayEquals(originalExpansion, entry(output, OBB))
    }

    @Test
    fun `rejects an APK that is not listed by the XAPK manifest`() {
        val input = xapk(includeUnlistedApk = true)
        val output = File(temporaryFolder.root, "rejected.xapk")

        assertThrows(IllegalArgumentException::class.java) {
            XapkArchiveSigningBackend(identityReader = identityReader).sign(
                XapkSigningRequest(
                    input,
                    output,
                    File(temporaryFolder.root, "reject-work"),
                    ApkSignatureSelection.of(ApkSignatureScheme.V2),
                    signingKey()
                )
            )
        }
        assertTrue(!output.exists())
    }

    private fun xapk(includeUnlistedApk: Boolean): File {
        val apk = resource("unsigned.apk").readBytes()
        val splitApks = JSONArray()
            .put(JSONObject().put("file", "base.apk").put("id", "base"))
            .put(JSONObject().put("file", "split_config.en.apk").put("id", "config.en"))
        val manifest = JSONObject()
            .put("xapk_version", "2")
            .put("package_name", "com.example.app")
            .put("version_code", 42)
            .put("split_apks", splitApks)
            .put("expansions", JSONArray().put(JSONObject().put("file", OBB)))
            .toString()
            .toByteArray()
        val entries = mutableListOf(
            "base.apk" to apk,
            "split_config.en.apk" to apk,
            "manifest.json" to manifest,
            OBB to byteArrayOf(9, 8, 7, 6)
        )
        if (includeUnlistedApk) entries.add(2, "split_config.fr.apk" to apk)
        return File(temporaryFolder.root, if (includeUnlistedApk) {
            "unlisted.xapk"
        } else {
            "valid.xapk"
        }).also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun entry(file: File, name: String): ByteArray = ZipFile(file).use { zip ->
        zip.getInputStream(requireNotNull(zip.getEntry(name))).use { it.readBytes() }
    }

    private fun signingKey(): ApkSigningKeyMaterial {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=WizeFiles XAPK Test,O=Adonis Spices,C=LB")
        val now = Instant.now()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(11),
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

    private companion object {
        const val OBB = "Android/obb/com.example.app/main.42.com.example.app.obb"
    }
}
