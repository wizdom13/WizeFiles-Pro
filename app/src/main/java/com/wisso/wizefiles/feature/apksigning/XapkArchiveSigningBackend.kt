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
import org.json.JSONObject

data class XapkSigningRequest(
    val inputXapk: File,
    val outputXapk: File,
    val stagingDirectory: File,
    val schemes: ApkSignatureSelection,
    val keyMaterial: ApkSigningKeyMaterial,
    val minSdkVersion: Int? = null
)

data class XapkSigningResult(
    val outputXapk: File,
    val verification: AndroidSplitSetVerificationReport
)

data class XapkManifestInventory(
    val packageName: String,
    val versionCode: Long?,
    val apkPaths: Set<String>
)

/** Re-signs an XAPK while preserving its manifest, expansion files, and other payloads. */
class XapkArchiveSigningBackend(
    private val inspector: AndroidPackageContainerInspector = AndroidPackageContainerInspector(),
    private val apkBackend: ApkSigningBackend = ApksigApkSigningBackend(),
    private val identityReader: AndroidApkIdentityReader
) {
    fun sign(request: XapkSigningRequest): XapkSigningResult {
        validate(request)
        val inventory = inspector.inspect(request.inputXapk, AndroidPackageContainerHint.XAPK)
        val manifestBytes = readManifest(request.inputXapk)
        val manifest = parseManifest(manifestBytes, inventory)
        prepareEmptyDirectory(request.stagingDirectory)
        val signed = linkedMapOf<String, File>()
        try {
            ZipFile(request.inputXapk).use { source ->
                inventory.apkEntries.forEachIndexed { index, item ->
                    val entry = source.getEntry(item.name)
                        ?: throw XapkSigningException("An APK disappeared during signing")
                    val input = File(request.stagingDirectory, "%04d-input.apk".format(
                        Locale.ROOT,
                        index
                    ))
                    val output = File(request.stagingDirectory, "%04d-signed.apk".format(
                        Locale.ROOT,
                        index
                    ))
                    source.getInputStream(entry).buffered().use { stream ->
                        input.outputStream().buffered().use { target -> stream.copyTo(target) }
                    }
                    require(input.length() == item.sizeBytes && sha256(input) ==
                        item.sha256.uppercase(Locale.ROOT)) {
                        "An APK changed while the XAPK was being signed"
                    }
                    apkBackend.sign(ApkSigningRequest(
                        inputApk = input,
                        outputApk = output,
                        schemes = request.schemes,
                        keyMaterial = request.keyMaterial,
                        minSdkVersion = request.minSdkVersion
                    ))
                    signed[item.name] = output
                    input.delete()
                }
                validateSignedSet(signed, inventory, manifest, request)
                rebuildArchive(source, request.outputXapk, signed)
            }

            ZipFile(request.outputXapk).use { output ->
                val rebuilt = output.getInputStream(
                    output.getEntry(MANIFEST) ?: throw XapkSigningException(
                        "Signed XAPK lost manifest.json"
                    )
                ).use { it.readBytes() }
                require(rebuilt.contentEquals(manifestBytes)) {
                    "Signed XAPK changed manifest.json"
                }
            }
            val rebuilt = inspector.inspect(request.outputXapk, AndroidPackageContainerHint.XAPK)
            val originalNonApks = inventory.entries.filterNot(AndroidPackageContainerEntry::isApk)
                .associate { it.name to it.sha256 }
            val rebuiltNonApks = rebuilt.entries.filterNot(AndroidPackageContainerEntry::isApk)
                .associate { it.name to it.sha256 }
            require(originalNonApks == rebuiltNonApks) {
                "Signed XAPK changed a non-APK payload"
            }

            val verification = AndroidSplitSetVerifier(
                inspector,
                apkBackend,
                identityReader
            ).verify(
                request.outputXapk,
                File(request.stagingDirectory, "final-verification"),
                AndroidPackageContainerHint.XAPK,
                request.minSdkVersion
            )
            val expectedCertificate = sha256(request.keyMaterial.certificates.first().encoded)
            require(verification.verified && expectedCertificate in
                verification.signerCertificateSha256 && verification.apks.all {
                    it.schemes.containsAll(request.schemes.schemes)
                }) { "Signed XAPK failed complete package-set verification" }
            return XapkSigningResult(request.outputXapk, verification)
        } catch (exception: Exception) {
            request.outputXapk.delete()
            if (exception is XapkSigningException) throw exception
            throw XapkSigningException("Unable to sign XAPK", exception)
        } finally {
            request.stagingDirectory.deleteRecursively()
        }
    }

    fun verify(xapk: File, stagingDirectory: File): AndroidSplitSetVerificationReport =
        AndroidSplitSetVerifier(inspector, apkBackend, identityReader).verify(
            xapk,
            stagingDirectory,
            AndroidPackageContainerHint.XAPK
        )

    private fun validate(request: XapkSigningRequest) {
        require(request.inputXapk.isFile && request.inputXapk.canRead()) {
            "Input XAPK is not a readable regular file"
        }
        require(request.inputXapk.canonicalFile != request.outputXapk.canonicalFile) {
            "The original XAPK cannot be overwritten"
        }
        require(!request.outputXapk.exists()) { "Output XAPK already exists" }
        require(request.outputXapk.parentFile?.isDirectory == true) {
            "Output XAPK directory does not exist"
        }
        require(ApkSignatureScheme.V4 !in request.schemes.schemes) {
            "XAPK signing does not support detached v4 signatures"
        }
        require(request.minSdkVersion == null || request.minSdkVersion > 0) {
            "Minimum SDK version must be positive"
        }
        val staging = request.stagingDirectory.canonicalFile
        require(staging != request.inputXapk.canonicalFile &&
            staging != request.outputXapk.canonicalFile &&
            !request.inputXapk.canonicalFile.toPath().startsWith(staging.toPath()) &&
            !request.outputXapk.canonicalFile.toPath().startsWith(staging.toPath())) {
            "XAPK staging cannot contain the input or output"
        }
    }

    private fun readManifest(input: File): ByteArray = ZipFile(input).use { zip ->
        val entry = zip.getEntry(MANIFEST)
            ?: throw XapkSigningException("XAPK must use a canonical manifest.json path")
        require(entry.size in 1..MAXIMUM_MANIFEST_BYTES) { "XAPK manifest.json is too large" }
        zip.getInputStream(entry).use { it.readBytes() }.also {
            require(it.size.toLong() == entry.size) { "XAPK manifest.json is truncated" }
        }
    }

    private fun parseManifest(
        bytes: ByteArray,
        inventory: AndroidPackageContainerInventory
    ): XapkManifestInventory {
        val json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }
            .getOrElse { throw XapkSigningException("XAPK manifest.json is invalid", it) }
        val packageName = listOf("package_name", "packageName")
            .firstNotNullOfOrNull { json.optString(it).takeIf(String::isNotBlank) }
            ?: throw XapkSigningException("XAPK manifest has no package name")
        val versionCode = listOf("version_code", "versionCode")
            .firstNotNullOfOrNull { key -> json.optLong(key).takeIf { it > 0 } }
        val archiveApks = inventory.apkEntries.mapTo(linkedSetOf()) { it.name }
        val array = json.optJSONArray("split_apks")
        val manifestApks = if (array == null) {
            require(archiveApks.size == 1) {
                "A multi-APK XAPK must list every APK in split_apks"
            }
            archiveApks
        } else {
            buildSet {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index)
                        ?: throw XapkSigningException("XAPK has an invalid split_apks record")
                    val path = item.optString("file")
                    require(path.isNotBlank() && add(path)) {
                        "XAPK split_apks paths must be non-empty and unique"
                    }
                }
            }
        }
        require(manifestApks == archiveApks) {
            "XAPK APK inventory does not exactly match manifest.json"
        }
        return XapkManifestInventory(packageName, versionCode, manifestApks)
    }

    private fun validateSignedSet(
        signed: Map<String, File>,
        inventory: AndroidPackageContainerInventory,
        manifest: XapkManifestInventory,
        request: XapkSigningRequest
    ) {
        val identities = signed.map { (name, apk) -> identityReader.read(apk, name) }
        val expected = identities.firstOrNull() ?: throw XapkSigningException("XAPK has no APKs")
        require(identities.all {
            it.packageName == expected.packageName && it.versionCode == expected.versionCode
        }) { "XAPK contains APKs from different packages or versions" }
        require(identities.count { it.splitName == null } == 1) {
            "XAPK must contain exactly one base APK"
        }
        require(expected.packageName == manifest.packageName &&
            inventory.packageNameHint == expected.packageName) {
            "XAPK package metadata does not match its APKs"
        }
        manifest.versionCode?.let {
            require(it == expected.versionCode) { "XAPK version metadata does not match its APKs" }
        }
        val expectedCertificate = sha256(request.keyMaterial.certificates.first().encoded)
        signed.values.forEach { apk ->
            val report = apkBackend.verify(ApkVerificationRequest(
                apk,
                minSdkVersion = request.minSdkVersion
            ))
            require(report.verified && expectedCertificate in report.signerCertificateSha256 &&
                report.verifiedSchemes.containsAll(request.schemes.schemes)) {
                "Every XAPK APK must use the requested schemes and one certificate"
            }
        }
    }

    private fun rebuildArchive(source: ZipFile, output: File, signed: Map<String, File>) {
        val temporary = File(output.parentFile, ".${output.name}.${UUID.randomUUID()}.tmp")
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { target ->
                source.entries().asSequence().forEach { original ->
                    val replacement = signed[original.name]
                    val entry = ZipEntry(original.name).apply {
                        comment = original.comment
                        extra = original.extra
                        time = original.time
                        method = original.method
                        if (original.method == ZipEntry.STORED) {
                            if (replacement == null) {
                                size = original.size
                                compressedSize = original.compressedSize
                                crc = original.crc
                            } else {
                                size = replacement.length()
                                compressedSize = replacement.length()
                                crc = crc32(replacement)
                            }
                        }
                    }
                    target.putNextEntry(entry)
                    when {
                        replacement != null -> replacement.inputStream().buffered().use {
                            it.copyTo(target)
                        }
                        !original.isDirectory -> source.getInputStream(original).use {
                            it.copyTo(target)
                        }
                    }
                    target.closeEntry()
                }
            }
            if (!temporary.renameTo(output)) throw IOException("Unable to publish signed XAPK")
        } finally {
            temporary.delete()
        }
    }

    private fun prepareEmptyDirectory(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IOException("Unable to clear XAPK signing staging")
        }
        if (!directory.mkdirs()) throw IOException("Unable to create XAPK signing staging")
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
        const val MANIFEST = "manifest.json"
        const val MAXIMUM_MANIFEST_BYTES = 2L * 1024 * 1024
    }
}

class XapkSigningException(message: String, cause: Throwable? = null) : IOException(message, cause)
