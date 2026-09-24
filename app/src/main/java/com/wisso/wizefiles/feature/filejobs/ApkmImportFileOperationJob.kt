// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.feature.apksigning.AndroidPackageArchiveIdentityReader
import com.wisso.wizefiles.feature.apksigning.ApkmImportBackend
import com.wisso.wizefiles.feature.apksigning.ApkmImportOperationStore
import com.wisso.wizefiles.feature.apksigning.ApkmImportRequest
import com.wisso.wizefiles.feature.apksigning.ApkSigningOutputConflictPolicy
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

internal class ApkmImportFileOperationJob(operationId: String) : FileOperationJob(operationId) {
    override fun run() {
        val operationId = requireNotNull(transferId)
        val spec = ApkmImportOperationStore.load(operationId)
            ?: throw IOException("APKM import operation metadata is unavailable")
        val source = spec.sourceUri.toPath()
        val target = chooseTarget(spec.outputUri.toPath(), spec.conflictPolicy)
        val sourceSize = runCatching { source.size() }.getOrDefault(0L).coerceAtLeast(0)
        beginTransferExecution(totalItems = 1, totalBytes = sourceSize)
        val tracker = TransferItemTracker.begin(operationId, source, target)
        if (tracker?.isAlreadyFinished == true) return
        val staging = File(application.cacheDir, "apkm-import/$operationId")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create private APKM import staging directory" }
        var transferred = 0L
        try {
            val localSource = File(staging, "input.apkm")
            copyToPrivateFile(source, localSource)
            val localOutput = File(staging, "imported.apks")
            ApkmImportBackend(
                identityReader = AndroidPackageArchiveIdentityReader(application)
            ).importApkm(ApkmImportRequest(
                localSource,
                localOutput,
                File(staging, "import-work")
            ))
            TransferRepository.updatePlanSummary(operationId, 1, localOutput.length())
            copyNewFile(localOutput, target) { count ->
                transferred += count
                tracker?.addBytes(count, transferred)
            }
            tracker?.complete(target, transferred)
            ApkmImportOperationStore.delete(operationId)
            FileOperationService.notifyFileListRefresh()
        } catch (exception: Exception) {
            tracker?.fail(exception)
            throw exception
        } finally {
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
