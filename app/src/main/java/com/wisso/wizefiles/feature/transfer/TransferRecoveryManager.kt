package com.wisso.wizefiles.feature.transfer

import com.wisso.wizefiles.feature.filejobs.CopyFileOperationJob
import com.wisso.wizefiles.feature.filejobs.DeleteFileOperationJob
import com.wisso.wizefiles.feature.filejobs.DeleteOperationStore
import com.wisso.wizefiles.feature.filejobs.FileOperationJob
import com.wisso.wizefiles.feature.filejobs.MoveFileOperationJob
import com.wisso.wizefiles.feature.filejobs.ArchiveModifyFileOperationJob
import com.wisso.wizefiles.feature.filejobs.ApkSigningFileOperationJob
import com.wisso.wizefiles.feature.filejobs.AabSigningFileOperationJob
import com.wisso.wizefiles.feature.filejobs.ApksSigningFileOperationJob
import com.wisso.wizefiles.feature.filejobs.XapkSigningFileOperationJob
import com.wisso.wizefiles.feature.filejobs.ApkmImportFileOperationJob
import com.wisso.wizefiles.feature.apksigning.ApkSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecretRegistry
import com.wisso.wizefiles.feature.apksigning.AabSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.ApksSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.XapkSigningOperationStore
import com.wisso.wizefiles.feature.apksigning.ApkmImportOperationStore
import com.wisso.wizefiles.provider.archive.editor.ArchiveMutationStore
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.nio.file.Path
import com.wisso.wizefiles.provider.common.exists

internal object TransferRecoveryManager {
    fun recoverProcessLoss(
        excludedOperationIds: Set<String> = emptySet(),
        start: (FileOperationJob) -> Unit
    ): Int {
        var recovered = 0
        val restorableStates = setOf(
            TransferOperationState.QUEUED,
            TransferOperationState.RECOVERABLE
        )
        TransferDatabase.operations(restorableStates).forEach { operation ->
            if (operation.id in excludedOperationIds) return@forEach
            if (TransferDatabase.syncRunForTransfer(operation.id) != null) return@forEach
            if (completeRecoveredMoveIfAlreadyFinalized(operation)) {
                recovered++
                return@forEach
            }
            val job = createJob(operation) ?: run {
                val hasRecoverableSigningMetadata =
                    (operation.type == TransferOperationType.APK_SIGN &&
                        ApkSigningOperationStore.load(operation.id) != null) ||
                        (operation.type == TransferOperationType.AAB_SIGN &&
                            AabSigningOperationStore.load(operation.id) != null) ||
                        (operation.type == TransferOperationType.APKS_SIGN &&
                            ApksSigningOperationStore.load(operation.id) != null) ||
                        (operation.type == TransferOperationType.XAPK_SIGN &&
                            XapkSigningOperationStore.load(operation.id) != null)
                if (hasRecoverableSigningMetadata &&
                    !ApkSigningSecretRegistry.has(operation.id)) {
                    TransferDatabase.transition(
                        operation.id,
                        TransferOperationState.WAITING_FOR_USER,
                        reason = "SIGNING_SECRET_REQUIRED",
                        requiresUserAction = true
                    )
                    recovered++
                    return@forEach
                }
                if (operation.state == TransferOperationState.RECOVERABLE) {
                    TransferDatabase.transition(
                        operation.id,
                        TransferOperationState.PAUSED,
                        reason = "PATH_UNAVAILABLE",
                        requiresUserAction = true
                    )
                }
                return@forEach
            }
            if (operation.state == TransferOperationState.RECOVERABLE) {
                TransferDatabase.transition(operation.id, TransferOperationState.QUEUED)
            }
            start(job)
            recovered++
        }
        return recovered
    }

    fun createJob(operation: TransferOperationRecord): FileOperationJob? {
        val sourceUris = TransferDatabase.sourceUris(operation.id)
        val sources = sourceUris.mapPathsOrNull() ?: return null
        val destination = operation.destinationUri.toPathOrNull() ?: return null
        return when (operation.type) {
            TransferOperationType.COPY,
            TransferOperationType.EXTRACT -> CopyFileOperationJob(
                sources,
                destination,
                operation.id
            )
            TransferOperationType.MOVE -> MoveFileOperationJob(sources, destination, operation.id)
            TransferOperationType.DELETE ->
                DeleteOperationStore.load(operation.id)?.let { options ->
                    DeleteFileOperationJob(sources, options, operation.id)
                }
            TransferOperationType.ARCHIVE_MODIFY ->
                ArchiveMutationStore.load(operation.id)?.let(::ArchiveModifyFileOperationJob)
            TransferOperationType.APK_SIGN ->
                if (ApkSigningOperationStore.load(operation.id) != null &&
                    ApkSigningSecretRegistry.has(operation.id)) {
                    ApkSigningFileOperationJob(operation.id)
                } else {
                    null
                }
            TransferOperationType.AAB_SIGN ->
                if (AabSigningOperationStore.load(operation.id) != null &&
                    ApkSigningSecretRegistry.has(operation.id)) {
                    AabSigningFileOperationJob(operation.id)
                } else {
                    null
                }
            TransferOperationType.APKS_SIGN ->
                if (ApksSigningOperationStore.load(operation.id) != null &&
                    ApkSigningSecretRegistry.has(operation.id)) {
                    ApksSigningFileOperationJob(operation.id)
                } else {
                    null
                }
            TransferOperationType.XAPK_SIGN ->
                if (XapkSigningOperationStore.load(operation.id) != null &&
                    ApkSigningSecretRegistry.has(operation.id)) {
                    XapkSigningFileOperationJob(operation.id)
                } else {
                    null
                }
            TransferOperationType.APKM_IMPORT ->
                if (ApkmImportOperationStore.load(operation.id) != null) {
                    ApkmImportFileOperationJob(operation.id)
                } else {
                    null
                }
            TransferOperationType.BACKUP,
            TransferOperationType.MIRROR,
            TransferOperationType.TWO_WAY_SYNC,
            TransferOperationType.MOVE_BACKUP,
            TransferOperationType.NEARBY_SEND,
            TransferOperationType.NEARBY_RECEIVE -> null
        }
    }

    private fun List<String>.mapPathsOrNull(): List<Path>? {
        val paths = mapNotNull { it.toPathOrNull() }
        return paths.takeIf { it.size == size }
    }

    private fun completeRecoveredMoveIfAlreadyFinalized(
        operation: TransferOperationRecord
    ): Boolean {
        val isMove = operation.type == TransferOperationType.MOVE ||
            operation.type == TransferOperationType.MOVE_BACKUP
        if (!isMove || operation.state != TransferOperationState.RECOVERABLE) {
            return false
        }
        val sourcePaths = TransferDatabase.sourceUris(operation.id).mapPathsOrNull() ?: return false
        if (sourcePaths.isEmpty() || sourcePaths.any { it.exists() }) return false
        val copiedSources = TransferDatabase.items(operation.id)
            .filter { it.state == TransferItemState.COPIED }
            .map(TransferItemRecord::sourceUri)
            .toSet()
        if (copiedSources.isEmpty()) return false
        TransferDatabase.transition(operation.id, TransferOperationState.QUEUED)
        TransferDatabase.transition(operation.id, TransferOperationState.RUNNING)
        TransferDatabase.transition(operation.id, TransferOperationState.COMPLETED)
        return true
    }

    private fun String.toPathOrNull(): Path? =
        toAppPathOrNull()?.toLegacyPathOrNull()
}
