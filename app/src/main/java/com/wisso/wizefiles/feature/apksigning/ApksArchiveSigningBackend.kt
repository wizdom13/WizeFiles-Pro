// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class ApksSigningRequest(
    val inputApks: File,
    val outputApks: File,
    val stagingDirectory: File,
    val schemes: ApkSignatureSelection,
    val keyMaterial: ApkSigningKeyMaterial,
    val minSdkVersion: Int? = null
)

data class ApksSigningResult(
    val outputApks: File,
    val verification: AndroidSplitSetVerificationReport
)

/** Re-signs every APK in an existing APKS without regenerating bundletool targeting metadata. */
class ApksArchiveSigningBackend(
    private val inspector: AndroidPackageContainerInspector = AndroidPackageContainerInspector(),
    private val apkBackend: ApkSigningBackend = ApksigApkSigningBackend(),
    private val identityReader: AndroidApkIdentityReader,
    private val tocParser: BundletoolTocParser = BundletoolTocParser()
) {
    fun sign(request: ApksSigningRequest): ApksSigningResult {
        validate(request)
        val inventory = inspector.inspect(request.inputApks, AndroidPackageContainerHint.APKS)
        val tocBytes = if (inventory.format == AndroidPackageContainerFormat.BUNDLETOOL_APKS) {
            readBundletoolToc(request.inputApks, inventory)
        } else {
            null
        }
        prepareEmptyDirectory(request.stagingDirectory)
        val signed = linkedMapOf<String, File>()
        try {
            ZipFile(request.inputApks).use { source ->
                inventory.apkEntries.forEachIndexed { index, item ->
                    val entry = source.getEntry(item.name)
                        ?: throw ApksSigningException("An APK disappeared during signing")
                    val input = File(request.stagingDirectory, "%04d-input.apk".format(
                        Locale.ROOT,
                        index
                    ))
                    val output = File(request.stagingDirectory, "%04d-signed.apk".format(
                        Locale.ROOT,
                        index
                    ))
                    source.getInputStream(entry).buffered().use { stream ->
                        input.outputStream().buffered().use { output -> stream.copyTo(output) }
                    }
                    if (input.length() != item.sizeBytes || sha256(input) != item.sha256.uppercase(
                            Locale.ROOT
                        )) {
                        throw ApksSigningException("An APK changed while the APKS was being signed")
                    }
                    apkBackend.sign(
                        ApkSigningRequest(
                            inputApk = input,
                            outputApk = output,
                            schemes = request.schemes,
                            keyMaterial = request.keyMaterial,
                            minSdkVersion = request.minSdkVersion
                        )
                    )
                    signed[item.name] = output
                    input.delete()
                }
                validateSignedSet(signed, inventory, tocBytes, request)
                val replacementMetadata = if (
                    inventory.format == AndroidPackageContainerFormat.WIZEFILES_APKS
                ) {
                    wizeFilesMetadata(signed, request.minSdkVersion)
                } else {
                    null
                }
                rebuildArchive(source, request.outputApks, signed, replacementMetadata)
            }

            if (tocBytes != null) {
                ZipFile(request.outputApks).use { output ->
                    val entry = output.getEntry(BUNDLETOOL_TOC)
                        ?: throw ApksSigningException("Signed APKS lost toc.pb")
                    val rebuiltToc = output.getInputStream(entry).use { it.readBytes() }
                    if (!rebuiltToc.contentEquals(tocBytes)) {
                        throw ApksSigningException("Signed APKS changed bundletool targeting metadata")
                    }
                }
            }

            val verification = AndroidSplitSetVerifier(
                inspector = inspector,
                backend = apkBackend,
                identityReader = identityReader
            ).verify(
                request.outputApks,
                File(request.stagingDirectory, "final-verification"),
                AndroidPackageContainerHint.APKS,
                request.minSdkVersion
            )
            val expectedCertificate = sha256(request.keyMaterial.certificates.first().encoded)
            val missingSchemes = verification.apks.filter {
                !it.schemes.containsAll(request.schemes.schemes)
            }
            if (!verification.verified || expectedCertificate !in
                verification.signerCertificateSha256 || missingSchemes.isNotEmpty()) {
                throw ApksSigningException("Signed APKS failed complete split-set verification")
            }
            return ApksSigningResult(request.outputApks, verification)
        } catch (exception: Exception) {
            request.outputApks.delete()
            if (exception is ApksSigningException) throw exception
            throw ApksSigningException("Unable to sign APKS", exception)
        } finally {
            request.stagingDirectory.deleteRecursively()
        }
    }

    fun verify(
        apks: File,
        stagingDirectory: File
    ): AndroidSplitSetVerificationReport = AndroidSplitSetVerifier(
        inspector = inspector,
        backend = apkBackend,
        identityReader = identityReader
    ).verify(apks, stagingDirectory, AndroidPackageContainerHint.APKS)

    private fun validate(request: ApksSigningRequest) {
        require(request.inputApks.isFile && request.inputApks.canRead()) {
            "Input APKS is not a readable regular file"
        }
        require(request.inputApks.canonicalFile != request.outputApks.canonicalFile) {
            "The original APKS cannot be overwritten"
        }
        require(!request.outputApks.exists()) { "Output APKS already exists" }
        require(request.outputApks.parentFile?.isDirectory == true) {
            "Output APKS directory does not exist"
        }
        require(ApkSignatureScheme.V4 !in request.schemes.schemes) {
            "APKS signing does not support detached v4 signatures"
        }
        require(request.minSdkVersion == null || request.minSdkVersion > 0) {
            "Minimum SDK version must be positive"
        }
        val staging = request.stagingDirectory.canonicalFile
        require(staging != request.inputApks.canonicalFile && staging != request.outputApks.canonicalFile) {
            "APKS staging must be separate from the input and output"
        }
        require(!request.inputApks.canonicalFile.toPath().startsWith(staging.toPath()) &&
            !request.outputApks.canonicalFile.toPath().startsWith(staging.toPath())) {
            "APKS staging cannot contain the input or output"
        }
    }

    private fun readBundletoolToc(
        input: File,
        inventory: AndroidPackageContainerInventory
    ): ByteArray = ZipFile(input).use { zip ->
        val entry = zip.getEntry(BUNDLETOOL_TOC)
            ?: throw ApksSigningException("Bundletool APKS has no toc.pb")
        require(entry.size in 1..MAXIMUM_TOC_BYTES) { "APKS toc.pb is too large" }
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        require(bytes.size.toLong() == entry.size) { "APKS toc.pb is truncated" }
        val toc = tocParser.parse(bytes)
        val entryNames = inventory.entries.mapTo(linkedSetOf()) { it.name }
        require(entryNames.containsAll(toc.describedPaths)) {
            "APKS toc.pb references missing package files"
        }
        val archiveApks = inventory.apkEntries.mapTo(linkedSetOf()) { it.name }
        require(toc.apkPaths == archiveApks) {
            "APKS APK inventory does not exactly match toc.pb"
        }
        bytes
    }

    private fun validateSignedSet(
        signed: Map<String, File>,
        inventory: AndroidPackageContainerInventory,
        tocBytes: ByteArray?,
        request: ApksSigningRequest
    ) {
        val identities = signed.map { (name, apk) -> identityReader.read(apk, name) }
        val expected = identities.firstOrNull()
            ?: throw ApksSigningException("APKS contains no APKs")
        require(identities.all {
            it.packageName == expected.packageName && it.versionCode == expected.versionCode
        }) { "APKS contains APKs from different packages or versions" }
        require(identities.count { it.splitName == null } == 1) {
            "APKS must contain exactly one base APK"
        }
        inventory.packageNameHint?.let {
            require(it == expected.packageName) { "APKS package metadata does not match its APKs" }
        }
        inventory.versionCodeHint?.let {
            require(it == expected.versionCode) { "APKS version metadata does not match its APKs" }
        }
        tocBytes?.let {
            require(tocParser.parse(it).packageName == expected.packageName) {
                "APKS toc.pb package name does not match its APKs"
            }
        }
        val expectedCertificate = sha256(request.keyMaterial.certificates.first().encoded)
        signed.forEach { (_, apk) ->
            val verification = apkBackend.verify(ApkVerificationRequest(
                apk,
                minSdkVersion = request.minSdkVersion
            ))
            require(verification.verified && expectedCertificate in
                verification.signerCertificateSha256 &&
                verification.verifiedSchemes.containsAll(request.schemes.schemes)) {
                "Every APK must use the requested schemes and one signing certificate"
            }
        }
    }

    private fun wizeFilesMetadata(
        signed: Map<String, File>,
        minSdkVersion: Int?
    ): ByteArray {
        val identities = signed.map { (name, file) -> identityReader.read(file, name) }
        val first = identities.first()
        val certificate = apkBackend.verify(ApkVerificationRequest(
            signed.values.first(),
            minSdkVersion = minSdkVersion
        ))
            .signerCertificateSha256.first()
        val apks = JSONArray()
        signed.forEach { (name, file) ->
            apks.put(JSONObject().put("file", name).put("sha256", sha256(file)))
        }
        return JSONObject()
            .put("formatVersion", 1)
            .put("packageName", first.packageName)
            .put("versionCode", first.versionCode)
            .put("signerCertificateSha256", certificate)
            .put("apks", apks)
            .toString(2)
            .toByteArray(Charsets.UTF_8)
    }

    private fun rebuildArchive(
        source: ZipFile,
        output: File,
        signed: Map<String, File>,
        replacementMetadata: ByteArray?
    ) {
        val temporary = File(output.parentFile, ".${output.name}.${UUID.randomUUID()}.tmp")
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { target ->
                source.entries().asSequence().forEach { original ->
                    val replacementFile = signed[original.name]
                    val replacementBytes = if (
                        replacementMetadata != null && original.name.equals("metadata.json", true)
                    ) replacementMetadata else null
                    val replacement = replacementFile != null || replacementBytes != null
                    val copied = copiedEntry(original, replacement)
                    if (copied.method == ZipEntry.STORED && replacementFile != null) {
                        copied.size = replacementFile.length()
                        copied.compressedSize = replacementFile.length()
                        copied.crc = crc32(replacementFile)
                    } else if (copied.method == ZipEntry.STORED && replacementBytes != null) {
                        copied.size = replacementBytes.size.toLong()
                        copied.compressedSize = replacementBytes.size.toLong()
                        copied.crc = CRC32().apply { update(replacementBytes) }.value
                    }
                    target.putNextEntry(copied)
                    when {
                        replacementFile != null -> replacementFile.inputStream().buffered().use {
                            it.copyTo(target)
                        }
                        replacementBytes != null -> target.write(replacementBytes)
                        !original.isDirectory -> source.getInputStream(original).use { it.copyTo(target) }
                    }
                    target.closeEntry()
                }
            }
            if (!temporary.renameTo(output)) throw IOException("Unable to publish signed APKS")
        } finally {
            temporary.delete()
        }
    }

    private fun copiedEntry(original: ZipEntry, replacement: Boolean) = ZipEntry(original.name).apply {
        comment = original.comment
        extra = original.extra
        time = original.time
        method = original.method
        if (!replacement && original.method == ZipEntry.STORED) {
            size = original.size
            compressedSize = original.compressedSize
            crc = original.crc
        }
    }

    private fun prepareEmptyDirectory(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IOException("Unable to clear APKS signing staging")
        }
        if (!directory.mkdirs()) throw IOException("Unable to create APKS signing staging")
    }

    private fun sha256(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun crc32(file: File): Long = file.inputStream().buffered().use { input ->
        val crc = CRC32()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            crc.update(buffer, 0, count)
        }
        crc.value
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF)
    }

    private companion object {
        const val BUNDLETOOL_TOC = "toc.pb"
        const val MAXIMUM_TOC_BYTES = 8L * 1024 * 1024
    }
}

class ApksSigningException(message: String, cause: Throwable? = null) : IOException(message, cause)
