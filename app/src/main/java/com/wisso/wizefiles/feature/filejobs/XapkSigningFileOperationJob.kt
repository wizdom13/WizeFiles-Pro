package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.feature.apksigning.AabSigningKeyMaterialService
import com.wisso.wizefiles.feature.apksigning.AndroidPackageArchiveIdentityReader
import com.wisso.wizefiles.feature.apksigning.ApkSigningOutputConflictPolicy
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecretRegistry
import com.wisso.wizefiles.feature.apksigning.XapkArchiveSigningBackend
import com.wisso.wizefiles.feature.apksigning.XapkSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.XapkSigningRequest
import com.wisso.wizefiles.feature.apksigning.copyNewFile
import com.wisso.wizefiles.feature.apksigning.copyToPrivateFile
import com.wisso.wizefiles.feature.transfer.TransferItemTracker
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.provider.common.exists
import com.wisso.wizefiles.provider.common.size
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path

internal class XapkSigningFileOperationJob(operationId: String) : FileOperationJob(operationId) {
    override fun run() {
        val operationId = requireNotNull(transferId)
        val spec = XapkSigningOperationStore.load(operationId)
            ?: throw IOException("XAPK signing operation metadata is unavailable")
        val secrets = ApkSigningSecretRegistry.take(operationId)
            ?: throw XapkSigningSecretRequiredException()
        val source = spec.sourceUri.toPath()
        val requestedTarget = spec.outputUri.toPath()
        val signingKey = spec.signingKeyUri.toPath()
        val certificate = spec.certificateUri.takeIf(String::isNotBlank)?.toPath()
        val target = chooseTarget(requestedTarget, spec.conflictPolicy)
        val sourceSize = runCatching { source.size() }.getOrDefault(0L).coerceAtLeast(0)
        beginTransferExecution(totalItems = 1, totalBytes = sourceSize)
        val tracker = TransferItemTracker.begin(operationId, source, target)
        if (tracker?.isAlreadyFinished == true) {
            secrets.close()
            return
        }
        val staging = File(application.cacheDir, "xapk-signing/$operationId")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create private XAPK signing staging directory" }
        var transferred = 0L
        try {
            secrets.use {
                val localSource = File(staging, "input.xapk")
                val localSigningKey = File(staging, "signing-key")
                val localCertificate = certificate?.let { File(staging, "certificate-chain") }
                copyToPrivateFile(source, localSource)
                copyToPrivateFile(signingKey, localSigningKey)
                if (certificate != null && localCertificate != null) {
                    copyToPrivateFile(certificate, localCertificate)
                }
                val keyMaterial = AabSigningKeyMaterialService().load(
                    localSigningKey,
                    localCertificate,
                    spec.keySource,
                    spec.keyStoreFormat,
                    spec.keyAlias,
                    secrets
                )
                val localOutput = File(staging, "signed.xapk")
                XapkArchiveSigningBackend(
                    identityReader = AndroidPackageArchiveIdentityReader(application)
                ).sign(XapkSigningRequest(
                    inputXapk = localSource,
                    outputXapk = localOutput,
                    stagingDirectory = File(staging, "archive-work"),
                    schemes = spec.signatureSelection(),
                    keyMaterial = keyMaterial
                ))
                TransferRepository.updatePlanSummary(operationId, 1, localOutput.length())
                copyNewFile(localOutput, target) { count ->
                    transferred += count
                    tracker?.addBytes(count, transferred)
                }
            }
            tracker?.complete(target, transferred)
            XapkSigningOperationStore.delete(operationId)
            FileOperationService.notifyFileListRefresh()
        } catch (exception: Exception) {
            tracker?.fail(exception)
            throw exception
        } finally {
            ApkSigningSecretRegistry.clear(operationId)
            staging.deleteRecursively()
        }
    }

    private fun chooseTarget(requested: Path, policy: ApkSigningOutputConflictPolicy): Path {
        if (!requested.exists()) return requested
        if (policy == ApkSigningOutputConflictPolicy.FAIL) {
            throw FileAlreadyExistsException(requested.toString())
        }
        val name = requested.fileName.toString()
        val extensionIndex = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, extensionIndex)
        val extension = name.substring(extensionIndex)
        for (index in 1..9999) {
            val candidate = requested.resolveSibling("$base ($index)$extension")
            if (!candidate.exists()) return candidate
        }
        throw FileAlreadyExistsException(requested.toString())
    }

    private fun String.toPath(): Path = toAppPathOrNull()?.toLegacyPathOrNull()
        ?: throw IOException("Provider path is unavailable")
}

internal class XapkSigningSecretRequiredException : IOException(
    "XAPK signing password must be entered again"
)
