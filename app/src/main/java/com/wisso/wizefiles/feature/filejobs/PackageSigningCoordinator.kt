// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import android.content.Context
import androidx.annotation.MainThread
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.entitlement.ProFeatureAccess
import com.wisso.wizefiles.feature.apksigning.AabSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.AabSigningWorkflowSpec
import com.wisso.wizefiles.feature.apksigning.ApkmImportOperationStore
import com.wisso.wizefiles.feature.apksigning.ApksSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.ApksSigningWorkflowSpec
import com.wisso.wizefiles.feature.apksigning.ApkSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecretRegistry
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecrets
import com.wisso.wizefiles.feature.apksigning.ApkSigningWorkflowSpec
import com.wisso.wizefiles.feature.apksigning.ApkmImportWorkflowSpec
import com.wisso.wizefiles.feature.apksigning.XapkSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.XapkSigningWorkflowSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationRecord
import com.wisso.wizefiles.feature.transfer.TransferOperationSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferRepository

internal object PackageSigningCoordinator {

    fun signApk(
        spec: ApkSigningWorkflowSpec,
        secrets: ApkSigningSecrets,
        context: Context
    ): String {
        ProFeatureAccess.require(ProFeature.PACKAGE_SIGNING)
        ApkSigningOperationStore.save(spec)
        ApkSigningSecretRegistry.put(spec.operationId, secrets)
        try {
            TransferRepository.enqueue(
                TransferOperationSpec(
                    id = spec.operationId,
                    type = TransferOperationType.APK_SIGN,
                    sourceUris = listOf(spec.sourceUri, spec.keyStoreUri),
                    destinationUri = spec.outputUri
                )
            )
            FileOperationService.enqueue(ApkSigningFileOperationJob(spec.operationId), context)
            return spec.operationId
        } catch (exception: Exception) {
            ApkSigningSecretRegistry.clear(spec.operationId)
            ApkSigningOperationStore.delete(spec.operationId)
            throw exception
        }
    }

    @MainThread
    fun resumeApkSigning(
        operationId: String,
        secrets: ApkSigningSecrets,
        context: Context
    ): Boolean {
        val operation = TransferRepository.operation(operationId) ?: return false
        if (operation.type != TransferOperationType.APK_SIGN ||
            ApkSigningOperationStore.load(operationId) == null) {
            return false
        }
        ApkSigningSecretRegistry.put(operationId, secrets)
        return if (FileOperationService.resumeTransfer(operationId, context)) {
            true
        } else {
            ApkSigningSecretRegistry.clear(operationId)
            false
        }
    }

    fun signAab(
        spec: AabSigningWorkflowSpec,
        secrets: ApkSigningSecrets,
        context: Context
    ): String {
        ProFeatureAccess.require(ProFeature.PACKAGE_SIGNING)
        AabSigningOperationStore.save(spec)
        ApkSigningSecretRegistry.put(spec.operationId, secrets)
        try {
            TransferRepository.enqueue(
                TransferOperationSpec(
                    id = spec.operationId,
                    type = TransferOperationType.AAB_SIGN,
                    sourceUris = listOf(spec.sourceUri) + spec.credentialUris(),
                    destinationUri = spec.outputUri
                )
            )
            FileOperationService.enqueue(AabSigningFileOperationJob(spec.operationId), context)
            return spec.operationId
        } catch (exception: Exception) {
            ApkSigningSecretRegistry.clear(spec.operationId)
            AabSigningOperationStore.delete(spec.operationId)
            throw exception
        }
    }

    @MainThread
    fun resumeAabSigning(
        operationId: String,
        secrets: ApkSigningSecrets,
        context: Context
    ): Boolean {
        val operation = TransferRepository.operation(operationId) ?: return false
        if (operation.type != TransferOperationType.AAB_SIGN ||
            AabSigningOperationStore.load(operationId) == null) {
            return false
        }
        ApkSigningSecretRegistry.put(operationId, secrets)
        return if (FileOperationService.resumeTransfer(operationId, context)) {
            true
        } else {
            ApkSigningSecretRegistry.clear(operationId)
            false
        }
    }

    fun signApks(
        spec: ApksSigningWorkflowSpec,
        secrets: ApkSigningSecrets,
        context: Context
    ): String {
        ProFeatureAccess.require(ProFeature.PACKAGE_SIGNING)
        ApksSigningOperationStore.save(spec)
        ApkSigningSecretRegistry.put(spec.operationId, secrets)
        try {
            TransferRepository.enqueue(
                TransferOperationSpec(
                    id = spec.operationId,
                    type = TransferOperationType.APKS_SIGN,
                    sourceUris = listOf(spec.sourceUri) + spec.credentialUris(),
                    destinationUri = spec.outputUri
                )
            )
            FileOperationService.enqueue(ApksSigningFileOperationJob(spec.operationId), context)
            return spec.operationId
        } catch (exception: Exception) {
            ApkSigningSecretRegistry.clear(spec.operationId)
            ApksSigningOperationStore.delete(spec.operationId)
            throw exception
        }
    }

    @MainThread
    fun resumeApksSigning(
        operationId: String,
        secrets: ApkSigningSecrets,
        context: Context
    ): Boolean {
        val operation = TransferRepository.operation(operationId) ?: return false
        if (operation.type != TransferOperationType.APKS_SIGN ||
            ApksSigningOperationStore.load(operationId) == null) {
            return false
        }
        ApkSigningSecretRegistry.put(operationId, secrets)
        return if (FileOperationService.resumeTransfer(operationId, context)) {
            true
        } else {
            ApkSigningSecretRegistry.clear(operationId)
            false
        }
    }


    fun signXapk(
        spec: XapkSigningWorkflowSpec,
        secrets: ApkSigningSecrets,
        context: Context
    ): String {
        ProFeatureAccess.require(ProFeature.PACKAGE_SIGNING)
        XapkSigningOperationStore.save(spec)
        ApkSigningSecretRegistry.put(spec.operationId, secrets)
        try {
            TransferRepository.enqueue(
                TransferOperationSpec(
                    id = spec.operationId,
                    type = TransferOperationType.XAPK_SIGN,
                    sourceUris = listOf(spec.sourceUri) + spec.credentialUris(),
                    destinationUri = spec.outputUri
                )
            )
            FileOperationService.enqueue(XapkSigningFileOperationJob(spec.operationId), context)
            return spec.operationId
        } catch (exception: Exception) {
            ApkSigningSecretRegistry.clear(spec.operationId)
            XapkSigningOperationStore.delete(spec.operationId)
            throw exception
        }
    }

    @MainThread
    fun resumeXapkSigning(
        operationId: String,
        secrets: ApkSigningSecrets,
        context: Context
    ): Boolean {
        val operation = TransferRepository.operation(operationId) ?: return false
        if (operation.type != TransferOperationType.XAPK_SIGN ||
            XapkSigningOperationStore.load(operationId) == null) {
            return false
        }
        ApkSigningSecretRegistry.put(operationId, secrets)
        return if (FileOperationService.resumeTransfer(operationId, context)) {
            true
        } else {
            ApkSigningSecretRegistry.clear(operationId)
            false
        }
    }


    fun importApkm(
        spec: ApkmImportWorkflowSpec,
        context: Context
    ): String {
        ProFeatureAccess.require(ProFeature.PACKAGE_SIGNING)
        ApkmImportOperationStore.save(spec)
        try {
            TransferRepository.enqueue(
                TransferOperationSpec(
                    id = spec.operationId,
                    type = TransferOperationType.APKM_IMPORT,
                    sourceUris = listOf(spec.sourceUri),
                    destinationUri = spec.outputUri
                )
            )
            FileOperationService.enqueue(ApkmImportFileOperationJob(spec.operationId), context)
            return spec.operationId
        } catch (exception: Exception) {
            ApkmImportOperationStore.delete(spec.operationId)
            throw exception
        }
    }

    fun cleanup(operation: com.wisso.wizefiles.feature.transfer.TransferOperationRecord) {
        when (operation.type) {
            TransferOperationType.APK_SIGN -> {
                ApkSigningSecretRegistry.clear(operation.id)
                ApkSigningOperationStore.delete(operation.id)
            }
            TransferOperationType.AAB_SIGN -> {
                ApkSigningSecretRegistry.clear(operation.id)
                AabSigningOperationStore.delete(operation.id)
            }
            TransferOperationType.APKS_SIGN -> {
                ApkSigningSecretRegistry.clear(operation.id)
                ApksSigningOperationStore.delete(operation.id)
            }
            TransferOperationType.XAPK_SIGN -> {
                ApkSigningSecretRegistry.clear(operation.id)
                XapkSigningOperationStore.delete(operation.id)
            }
            TransferOperationType.APKM_IMPORT ->
                ApkmImportOperationStore.delete(operation.id)
            else -> Unit
        }
    }

}
