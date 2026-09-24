// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.feature.apksigning.ApkSignatureScheme
import com.wisso.wizefiles.feature.apksigning.ApkSigningKeyStoreService
import com.wisso.wizefiles.feature.apksigning.ApkSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.ApkSigningOutputConflictPolicy
import com.wisso.wizefiles.feature.apksigning.ApkSigningRequest
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecretRegistry
import com.wisso.wizefiles.feature.apksigning.ApksigApkSigningBackend
import com.wisso.wizefiles.feature.apksigning.copyNewFile
import com.wisso.wizefiles.feature.apksigning.copyToPrivateFile
import com.wisso.wizefiles.feature.transfer.TransferItemTracker
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.provider.common.deleteIfExists
import com.wisso.wizefiles.provider.common.exists
import com.wisso.wizefiles.provider.common.size
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.FileAlreadyExistsException

internal class ApkSigningFileOperationJob(
    operationId: String
) : FileOperationJob(operationId) {
    override fun run() {
        val operationId = requireNotNull(transferId)
        val spec = ApkSigningOperationStore.load(operationId)
            ?: throw IOException("APK signing operation metadata is unavailable")
        val secrets = ApkSigningSecretRegistry.take(operationId)
            ?: throw ApkSigningSecretRequiredException()
        val source = spec.sourceUri.toPath()
        val requestedTarget = spec.outputUri.toPath()
        val keyStore = spec.keyStoreUri.toPath()
        val target = chooseTarget(
            requestedTarget,
            spec.conflictPolicy,
            ApkSignatureScheme.V4 in spec.schemes
        )
        val detachedTarget = if (ApkSignatureScheme.V4 in spec.schemes) {
            target.resolveSibling("${target.fileName}.idsig")
        } else {
            null
        }
        val sourceSize = runCatching { source.size() }.getOrDefault(0L).coerceAtLeast(0)
        beginTransferExecution(totalItems = 1, totalBytes = sourceSize)
        val tracker = TransferItemTracker.begin(operationId, source, target)
        if (tracker?.isAlreadyFinished == true) {
            secrets.close()
            return
        }
        val staging = File(application.cacheDir, "apk-signing/$operationId")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create private APK signing staging directory" }
        var transferred = 0L
        try {
            secrets.use {
                val localSource = File(staging, "input.apk")
                val localKeyStore = File(staging, "signing-key-store")
                copyToPrivateFile(source, localSource)
                copyToPrivateFile(keyStore, localKeyStore)
                val keyMaterial = ApkSigningKeyStoreService().load(
                    localKeyStore,
                    spec.keyStoreFormat,
                    spec.keyAlias,
                    secrets
                )
                val localOutput = File(staging, "signed.apk")
                val localDetached = detachedTarget?.let { File(staging, "signed.apk.idsig") }
                ApksigApkSigningBackend().sign(
                    ApkSigningRequest(
                        inputApk = localSource,
                        outputApk = localOutput,
                        v4SignatureOutput = localDetached,
                        schemes = spec.signatureSelection(),
                        keyMaterial = keyMaterial,
                        minSdkVersion = spec.minSdkVersion
                    )
                )
                TransferRepository.updatePlanSummary(operationId, 1, localOutput.length())
                if (localDetached != null) {
                    copyNewFile(localDetached, requireNotNull(detachedTarget))
                    try {
                        copyNewFile(localOutput, target) { count ->
                            transferred += count
                            tracker?.addBytes(count, transferred)
                        }
                    } catch (exception: Exception) {
                        runCatching { detachedTarget.deleteIfExists() }
                        throw exception
                    }
                } else {
                    copyNewFile(localOutput, target) { count ->
                        transferred += count
                        tracker?.addBytes(count, transferred)
                    }
                }
            }
            tracker?.complete(target, transferred)
            ApkSigningOperationStore.delete(operationId)
            FileOperationService.notifyFileListRefresh()
        } catch (exception: Exception) {
            tracker?.fail(exception)
            throw exception
        } finally {
            ApkSigningSecretRegistry.clear(operationId)
            staging.deleteRecursively()
        }
    }

    private fun chooseTarget(
        requested: Path,
        policy: ApkSigningOutputConflictPolicy,
        hasDetachedV4: Boolean
    ): Path {
        fun isAvailable(candidate: Path): Boolean =
            !candidate.exists() && (!hasDetachedV4 ||
                !candidate.resolveSibling("${candidate.fileName}.idsig").exists())
        if (isAvailable(requested)) return requested
        if (policy == ApkSigningOutputConflictPolicy.FAIL) {
            throw FileAlreadyExistsException(requested.toString())
        }
        val name = requested.fileName.toString()
        val extensionIndex = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, extensionIndex)
        val extension = name.substring(extensionIndex)
        for (index in 1..9999) {
            val candidate = requested.resolveSibling("$base ($index)$extension")
            if (isAvailable(candidate)) return candidate
        }
        throw FileAlreadyExistsException(requested.toString())
    }

    private fun String.toPath(): Path = toAppPathOrNull()?.toLegacyPathOrNull()
        ?: throw IOException("Provider path is unavailable")
}

internal class ApkSigningSecretRequiredException : IOException(
    "APK signing password must be entered again"
)
