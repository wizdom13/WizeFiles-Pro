package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONArray
import org.json.JSONObject
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
class ApksArchiveSigningBackendTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val identityReader = AndroidApkIdentityReader { _, entryName ->
        AndroidApkIdentity(
            packageName = "com.example.app",
            versionCode = 42,
            splitName = if (entryName == "base.apk") null else "config.en"
        )
    }

    @Test
    fun `re-signs every WizeFiles APK and rebuilds checksum metadata`() {
        val input = wizeFilesContainer()
        val original = input.readBytes()
        val output = File(temporaryFolder.root, "signed.apks")
        val backend = ApksArchiveSigningBackend(identityReader = identityReader)

        val result = backend.sign(
            ApksSigningRequest(
                inputApks = input,
                outputApks = output,
                stagingDirectory = File(temporaryFolder.root, "work"),
                schemes = ApkSignatureSelection.of(
                    ApkSignatureScheme.V1,
                    ApkSignatureScheme.V2,
                    ApkSignatureScheme.V3
                ),
                keyMaterial = signingKey(),
                minSdkVersion = 21
            )
        )

        assertTrue(result.verification.verified)
        assertEquals(2, result.verification.apks.size)
        assertTrue(input.readBytes().contentEquals(original))
        val inspected = AndroidPackageContainerInspector().inspect(
            output,
            AndroidPackageContainerHint.APKS
        )
        assertEquals(AndroidPackageContainerFormat.WIZEFILES_APKS, inspected.format)
    }

    @Test
    fun `bundletool APKS rejects an extra APK not described by toc`() {
        val input = bundletoolContainer(includeUnlistedApk = true)
        val output = File(temporaryFolder.root, "rejected.apks")
        val backend = ApksArchiveSigningBackend(identityReader = identityReader)

        assertThrows(IllegalArgumentException::class.java) {
            backend.sign(
                ApksSigningRequest(
                    input,
                    output,
                    File(temporaryFolder.root, "rejected-work"),
                    ApkSignatureSelection.of(ApkSignatureScheme.V2),
                    signingKey()
                )
            )
        }
        assertTrue(!output.exists())
    }

    private fun wizeFilesContainer(): File {
        val apk = resource("unsigned.apk").readBytes()
        val metadata = JSONObject()
            .put("formatVersion", 1)
            .put("packageName", "com.example.app")
            .put("versionCode", 42)
            .put("apks", JSONArray().apply {
                put(JSONObject().put("file", "base.apk").put("sha256", sha256(apk)))
                put(JSONObject().put("file", "split_config.en.apk").put("sha256", sha256(apk)))
            })
        return zip(
            "wizefiles.apks",
            listOf(
                "base.apk" to apk,
                "split_config.en.apk" to apk,
                "metadata.json" to metadata.toString().toByteArray()
            )
        )
    }

    private fun bundletoolContainer(includeUnlistedApk: Boolean): File {
        val apk = resource("unsigned.apk").readBytes()
        val entries = mutableListOf(
            "splits/base-master.apk" to apk,
            "toc.pb" to toc("splits/base-master.apk")
        )
        if (includeUnlistedApk) entries.add(1, "splits/config.en.apk" to apk)
        return zip("bundletool.apks", entries)
    }

    private fun toc(path: String): ByteArray {
        fun field(number: Int, value: ByteArray): ByteArray {
            require(value.size < 128)
            return byteArrayOf((number shl 3 or 2).toByte(), value.size.toByte()) + value
        }
        val description = field(2, path.toByteArray())
        val apkSet = field(2, description)
        val variant = field(2, apkSet)
        return field(1, variant) + field(4, "com.example.app".toByteArray())
    }

    private fun zip(name: String, entries: List<Pair<String, ByteArray>>): File =
        File(temporaryFolder.root, name).also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (entryName, bytes) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }

    private fun signingKey(): ApkSigningKeyMaterial {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=WizeFiles APKS Test,O=Adonis Spices,C=LB")
        val now = Instant.now()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.TEN,
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF)
        }
}
