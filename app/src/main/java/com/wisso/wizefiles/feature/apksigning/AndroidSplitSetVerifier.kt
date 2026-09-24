// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import android.content.Context
import com.android.apksig.apk.ApkUtils
import com.android.apksig.internal.apk.AndroidBinXmlParser
import com.android.apksig.util.DataSources
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

data class AndroidApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val splitName: String?
)

fun interface AndroidApkIdentityReader {
    fun read(apk: File, entryName: String): AndroidApkIdentity
}

/**
 * Reads identity directly from the APK's signed binary manifest. Android's PackageManager treats
 * configuration APKs as incomplete packages and may return null when they are parsed separately.
 */
class AndroidPackageArchiveIdentityReader() : AndroidApkIdentityReader {
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun read(apk: File, entryName: String): AndroidApkIdentity {
        val manifest = try {
            RandomAccessFile(apk, "r").use { file ->
                ApkUtils.getAndroidManifest(DataSources.asDataSource(file))
            }
        } catch (exception: Exception) {
            throw IOException("Unable to read APK binary manifest", exception)
        }
        return try {
            val packageName = ApkUtils.getPackageNameFromBinaryAndroidManifest(
                manifest.duplicate()
            )?.takeIf(String::isNotBlank)
                ?: throw IOException("APK package name is missing")
            val versionCode = ApkUtils.getLongVersionCodeFromBinaryAndroidManifest(
                manifest.duplicate()
            )
            AndroidApkIdentity(
                packageName = packageName,
                versionCode = versionCode,
                splitName = splitNameFromBinaryManifest(manifest.duplicate())
            )
        } catch (exception: IOException) {
            throw exception
        } catch (exception: Exception) {
            throw IOException("Unable to read APK package metadata", exception)
        }
    }

    private fun splitNameFromBinaryManifest(manifest: java.nio.ByteBuffer): String? {
        val parser = AndroidBinXmlParser(manifest)
        while (parser.next() != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
            if (parser.eventType != AndroidBinXmlParser.EVENT_START_ELEMENT ||
                parser.depth != 1 || parser.name != MANIFEST_ELEMENT) {
                continue
            }
            for (index in 0 until parser.attributeCount) {
                if (parser.getAttributeNamespace(index).isEmpty() &&
                    parser.getAttributeName(index) == SPLIT_ATTRIBUTE) {
                    return parser.getAttributeStringValue(index).takeIf(String::isNotBlank)
                }
            }
            return null
        }
        throw IOException("APK manifest element is missing")
    }

    private companion object {
        const val MANIFEST_ELEMENT = "manifest"
        const val SPLIT_ATTRIBUTE = "split"
    }
}

data class AndroidVerifiedApk(
    val entryName: String,
    val identity: AndroidApkIdentity,
    val certificateSha256: List<String>,
    val schemes: Set<ApkSignatureScheme>
)

data class AndroidSplitSetVerificationReport(
    val format: AndroidPackageContainerFormat,
    val verified: Boolean,
    val packageName: String?,
    val versionCode: Long?,
    val signerCertificateSha256: List<String>,
    val apks: List<AndroidVerifiedApk>,
    val errors: List<String>
)

class AndroidSplitSetVerifier(
    private val inspector: AndroidPackageContainerInspector = AndroidPackageContainerInspector(),
    private val backend: ApkSigningBackend = ApksigApkSigningBackend(),
    private val identityReader: AndroidApkIdentityReader
) {
    fun verify(
        container: File,
        stagingDirectory: File,
        hint: AndroidPackageContainerHint? = AndroidPackageContainerInspector.hintFromName(
            container.name
        ),
        minSdkVersion: Int? = null
    ): AndroidSplitSetVerificationReport {
        require(minSdkVersion == null || minSdkVersion > 0) {
            "Minimum SDK version must be positive"
        }
        val inventory = inspector.inspect(container, hint)
        require(inventory.format != AndroidPackageContainerFormat.AAB) {
            "AAB is not a split APK set"
        }
        if (stagingDirectory.exists() && !stagingDirectory.deleteRecursively()) {
            throw IOException("Unable to clear split verification staging")
        }
        if (!stagingDirectory.mkdirs()) {
            throw IOException("Unable to create split verification staging")
        }

        val errors = mutableListOf<String>()
        val verifiedApks = mutableListOf<AndroidVerifiedApk>()
        try {
            ZipFile(container).use { zip ->
                inventory.apkEntries.forEachIndexed { index, item ->
                    val zipEntry = zip.getEntry(item.name) ?: throw IOException(
                        "APK entry disappeared during verification"
                    )
                    val local = File(stagingDirectory, "%04d.apk".format(Locale.ROOT, index))
                    zip.getInputStream(zipEntry).buffered().use { input ->
                        local.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (local.length() != item.sizeBytes) {
                        throw IOException("APK entry changed during verification")
                    }
                    val signature = runCatching {
                        backend.verify(ApkVerificationRequest(
                            local,
                            minSdkVersion = minSdkVersion
                        ))
                    }.getOrElse { exception ->
                        errors += "${item.name}: ${exception.message ?: "signature verification failed"}"
                        return@forEachIndexed
                    }
                    if (!signature.verified) {
                        errors += "${item.name}: APK signature is not verified"
                        return@forEachIndexed
                    }
                    val identity = runCatching {
                        identityReader.read(local, item.name)
                    }.getOrElse { exception ->
                        errors += "${item.name}: ${exception.message ?: "package metadata is invalid"}"
                        return@forEachIndexed
                    }
                    verifiedApks += AndroidVerifiedApk(
                        entryName = item.name,
                        identity = identity,
                        certificateSha256 = normalizeCertificateDigests(
                            signature.signerCertificateSha256
                        ),
                        schemes = signature.verifiedSchemes
                    )
                }
            }

            val expectedIdentity = verifiedApks.firstOrNull()?.identity
            val expectedCertificates = verifiedApks.firstOrNull()?.certificateSha256.orEmpty()
            verifiedApks.forEach { apk ->
                if (expectedIdentity != null && (
                        apk.identity.packageName != expectedIdentity.packageName ||
                            apk.identity.versionCode != expectedIdentity.versionCode
                        )) {
                    errors += "${apk.entryName}: package or version does not match the split set"
                }
                if (apk.certificateSha256 != expectedCertificates) {
                    errors += "${apk.entryName}: signing certificate does not match the split set"
                }
            }
            val baseCount = verifiedApks.count { it.identity.splitName == null }
            if (baseCount != 1) errors += "Split set must contain exactly one base APK"
            if (verifiedApks.size != inventory.apkEntries.size) {
                errors += "Not every APK in the container passed verification"
            }
            inventory.packageNameHint?.let { hinted ->
                if (expectedIdentity != null && hinted != expectedIdentity.packageName) {
                    errors += "Container package metadata does not match the APKs"
                }
            }
            inventory.versionCodeHint?.let { hinted ->
                if (expectedIdentity != null && hinted != expectedIdentity.versionCode) {
                    errors += "Container version metadata does not match the APKs"
                }
            }
            return AndroidSplitSetVerificationReport(
                format = inventory.format,
                verified = errors.isEmpty(),
                packageName = expectedIdentity?.packageName,
                versionCode = expectedIdentity?.versionCode,
                signerCertificateSha256 = expectedCertificates,
                apks = verifiedApks,
                errors = errors.distinct()
            )
        } finally {
            stagingDirectory.deleteRecursively()
        }
    }

    companion object {
        fun privateStagingRoot(parent: File): File =
            File(parent, "android-container-verify/${UUID.randomUUID()}")
    }
}

internal fun normalizeCertificateDigests(digests: List<String>): List<String> = digests
    .map { it.uppercase(Locale.ROOT) }
    .distinct()
    .sorted()
