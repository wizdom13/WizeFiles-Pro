package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class ApkmImportRequest(
    val inputApkm: File,
    val outputApks: File,
    val stagingDirectory: File,
    val minSdkVersion: Int? = null
)

data class ApkmImportResult(
    val outputApks: File,
    val sourceVerification: AndroidSplitSetVerificationReport,
    val outputVerification: AndroidSplitSetVerificationReport
)

/** Converts a verified APKM into the documented WizeFiles open APKS layout without re-signing. */
class ApkmImportBackend(
    private val inspector: AndroidPackageContainerInspector = AndroidPackageContainerInspector(),
    private val apkBackend: ApkSigningBackend = ApksigApkSigningBackend(),
    private val identityReader: AndroidApkIdentityReader
) {
    fun importApkm(request: ApkmImportRequest): ApkmImportResult {
        validate(request)
        prepareEmptyDirectory(request.stagingDirectory)
        try {
            val inventory = inspector.inspect(request.inputApkm, AndroidPackageContainerHint.APKM)
            val verifier = AndroidSplitSetVerifier(inspector, apkBackend, identityReader)
            val sourceReport = verifier.verify(
                request.inputApkm,
                File(request.stagingDirectory, "source-verification"),
                AndroidPackageContainerHint.APKM,
                request.minSdkVersion
            )
            require(sourceReport.verified) { "APKM split set is not completely verified" }
            val metadata = metadata(inventory, sourceReport)
            rebuild(request.inputApkm, request.outputApks, metadata)

            val outputInventory = inspector.inspect(
                request.outputApks,
                AndroidPackageContainerHint.APKS
            )
            require(outputInventory.format == AndroidPackageContainerFormat.WIZEFILES_APKS) {
                "Imported archive is not an open WizeFiles APKS"
            }
            val inputApks = inventory.apkEntries.associate { it.name to it.sha256 }
            val outputApks = outputInventory.apkEntries.associate { it.name to it.sha256 }
            require(inputApks == outputApks) { "APKM import changed an APK payload" }

            val outputReport = verifier.verify(
                request.outputApks,
                File(request.stagingDirectory, "output-verification"),
                AndroidPackageContainerHint.APKS,
                request.minSdkVersion
            )
            require(outputReport.verified &&
                outputReport.packageName == sourceReport.packageName &&
                outputReport.versionCode == sourceReport.versionCode &&
                outputReport.signerCertificateSha256 == sourceReport.signerCertificateSha256 &&
                outputReport.apks.map { it.entryName }.toSet() ==
                    sourceReport.apks.map { it.entryName }.toSet()) {
                "Imported APKS does not match the verified APKM split set"
            }
            return ApkmImportResult(request.outputApks, sourceReport, outputReport)
        } catch (exception: Exception) {
            request.outputApks.delete()
            if (exception is ApkmImportException) throw exception
            throw ApkmImportException("Unable to import APKM", exception)
        } finally {
            request.stagingDirectory.deleteRecursively()
        }
    }

    private fun validate(request: ApkmImportRequest) {
        require(request.inputApkm.isFile && request.inputApkm.canRead()) {
            "Input APKM is not a readable regular file"
        }
        require(request.inputApkm.canonicalFile != request.outputApks.canonicalFile) {
            "The original APKM cannot be overwritten"
        }
        require(!request.outputApks.exists()) { "Output APKS already exists" }
        require(request.outputApks.parentFile?.isDirectory == true) {
            "Output APKS directory does not exist"
        }
        require(request.minSdkVersion == null || request.minSdkVersion > 0) {
            "Minimum SDK version must be positive"
        }
        val staging = request.stagingDirectory.canonicalFile
        require(staging != request.inputApkm.canonicalFile &&
            staging != request.outputApks.canonicalFile &&
            !request.inputApkm.canonicalFile.toPath().startsWith(staging.toPath()) &&
            !request.outputApks.canonicalFile.toPath().startsWith(staging.toPath())) {
            "APKM import staging cannot contain the input or output"
        }
    }

    private fun metadata(
        inventory: AndroidPackageContainerInventory,
        report: AndroidSplitSetVerificationReport
    ): ByteArray {
        val packageName = requireNotNull(report.packageName)
        val versionCode = requireNotNull(report.versionCode)
        val certificate = report.signerCertificateSha256.firstOrNull()
            ?: throw ApkmImportException("APKM signer certificate is unavailable")
        val apks = JSONArray()
        inventory.apkEntries.forEach { item ->
            apks.put(JSONObject().put("file", item.name).put("sha256", item.sha256))
        }
        return JSONObject()
            .put("formatVersion", 1)
            .put("packageName", packageName)
            .put("versionCode", versionCode)
            .put("signerCertificateSha256", certificate)
            .put("sourceFormat", "APKM")
            .put("apks", apks)
            .toString(2)
            .toByteArray(Charsets.UTF_8)
    }

    private fun rebuild(input: File, output: File, metadata: ByteArray) {
        val temporary = File(output.parentFile, ".${output.name}.${UUID.randomUUID()}.tmp")
        try {
            ZipFile(input).use { source ->
                ZipOutputStream(temporary.outputStream().buffered()).use { target ->
                    source.entries().asSequence().forEach { original ->
                        val entry = ZipEntry(original.name).apply {
                            comment = original.comment
                            extra = original.extra
                            time = original.time
                            method = original.method
                            if (original.method == ZipEntry.STORED) {
                                size = original.size
                                compressedSize = original.compressedSize
                                crc = original.crc
                            }
                        }
                        target.putNextEntry(entry)
                        if (!original.isDirectory) {
                            source.getInputStream(original).use { it.copyTo(target) }
                        }
                        target.closeEntry()
                    }
                    target.putNextEntry(ZipEntry("metadata.json"))
                    target.write(metadata)
                    target.closeEntry()
                }
            }
            if (!temporary.renameTo(output)) throw IOException("Unable to publish imported APKS")
        } finally {
            temporary.delete()
        }
    }

    private fun prepareEmptyDirectory(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IOException("Unable to clear APKM import staging")
        }
        if (!directory.mkdirs()) throw IOException("Unable to create APKM import staging")
    }
}

class ApkmImportException(message: String, cause: Throwable? = null) : IOException(message, cause)
