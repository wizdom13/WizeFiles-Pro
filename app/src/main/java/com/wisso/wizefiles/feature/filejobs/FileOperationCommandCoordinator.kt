// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import android.content.Context
import java.nio.file.Path
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.pro.ensureProAccess
import com.wisso.wizefiles.feature.transfer.TransferOperationSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.editor.ArchiveConflictPolicy
import com.wisso.wizefiles.provider.archive.editor.ArchiveMutation
import com.wisso.wizefiles.provider.archive.editor.ArchiveMutationSpec
import com.wisso.wizefiles.provider.archive.editor.ArchiveMutationStore
import com.wisso.wizefiles.provider.archive.editor.ArchiveMutationType
import com.wisso.wizefiles.provider.archive.editor.ArchiveRewriteEngine
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.storage.path.toUriString

internal object FileOperationCommandCoordinator {

    fun archive(
        sources: List<Path>,
        archiveFile: Path,
        format: Int,
        filter: Int,
        password: String?,
        context: Context
    ) {
        FileOperationService.enqueue(ArchiveFileOperationJob(sources, archiveFile, format, filter, password), context)
    }

    fun copy(
        sources: List<Path>,
        targetDirectory: Path,
        context: Context,
        archiveConflictPolicy: ArchiveConflictPolicy = ArchiveConflictPolicy.FAIL
    ) {
        if (targetDirectory.isArchivePath) {
            archivePaste(sources, targetDirectory, false, archiveConflictPolicy, context)
            return
        }
        val operation = TransferRepository.enqueue(
            TransferOperationSpec(
                type = if (sources.all { it.isArchivePath }) {
                    TransferOperationType.EXTRACT
                } else {
                    TransferOperationType.COPY
                },
                sourceUris = sources.map { it.toAppPath().toUriString() },
                destinationUri = targetDirectory.toAppPath().toUriString()
            )
        )
        FileOperationService.enqueue(CopyFileOperationJob(sources, targetDirectory, operation.id), context)
    }

    fun create(path: Path, createDirectory: Boolean, context: Context) {
        if (createDirectory && path.parent?.isArchivePath == true) {
            archiveCreateDirectory(path, context)
            return
        }
        FileOperationService.enqueue(CreateFileOperationJob(path, createDirectory), context)
    }

    fun delete(
        paths: List<Path>,
        context: Context,
        options: DeleteOptions = DeleteOptions()
    ): String? {
        if (paths.isEmpty()) return null
        if (paths.all { it.isArchivePath } &&
            paths.map { it.archiveFile }.distinct().size == 1) {
            archiveDelete(paths, context)
            return null
        }
        val operationSpec = TransferOperationSpec(
            type = TransferOperationType.DELETE,
            sourceUris = paths.map { it.toAppPath().toUriString() },
            destinationUri = (paths.first().parent ?: paths.first()).toAppPath().toUriString()
        )
        DeleteOperationStore.save(operationSpec.id, options)
        val operation = try {
            TransferRepository.enqueue(operationSpec)
        } catch (throwable: Throwable) {
            DeleteOperationStore.delete(operationSpec.id)
            throw throwable
        }
        FileOperationService.enqueue(
            DeleteFileOperationJob(paths, options, operation.id),
            context
        )
        return operation.id
    }

    fun move(
        sources: List<Path>,
        targetDirectory: Path,
        context: Context,
        archiveConflictPolicy: ArchiveConflictPolicy = ArchiveConflictPolicy.FAIL
    ) {
        if (targetDirectory.isArchivePath) {
            archivePaste(sources, targetDirectory, true, archiveConflictPolicy, context)
            return
        }
        val operation = TransferRepository.enqueue(
            TransferOperationSpec(
                type = TransferOperationType.MOVE,
                sourceUris = sources.map { it.toAppPath().toUriString() },
                destinationUri = targetDirectory.toAppPath().toUriString()
            )
        )
        FileOperationService.enqueue(MoveFileOperationJob(sources, targetDirectory, operation.id), context)
    }

    fun installApk(file: Path, context: Context) {
        FileOperationService.enqueue(InstallApkJob(file), context)
    }

    fun open(file: Path, mimeType: MimeType, withChooser: Boolean, context: Context) {
        FileOperationService.enqueue(OpenFileOperationJob(file, mimeType, withChooser), context)
    }

    fun openInternalViewer(file: Path, mimeType: MimeType, context: Context) {
        FileOperationService.enqueue(OpenInternalViewerFileOperationJob(file, mimeType), context)
    }

    fun rename(path: Path, newName: String, context: Context) {
        if (path.isArchivePath) {
            val parent = path.parent ?: return
            archiveMutate(
                archivePath = path,
                mutations = listOf(
                    ArchiveMutation(
                        ArchiveMutationType.RENAME,
                        archiveEntryName(path),
                        archiveEntryName(parent.resolve(newName))
                    )
                ),
                context = context
            )
            return
        }
        FileOperationService.enqueue(RenameFileOperationJob(path, newName), context)
    }

    private fun archivePaste(
        sources: List<Path>,
        targetDirectory: Path,
        cut: Boolean,
        conflictPolicy: ArchiveConflictPolicy,
        context: Context
    ) {
        val parent = archiveEntryName(targetDirectory)
        val mutations = sources.map { source ->
            val name = source.fileName?.toString()
                ?: throw IllegalArgumentException("Paste source has no file name")
            ArchiveMutation(
                type = ArchiveMutationType.ADD,
                path = listOf(parent, name).filter(String::isNotEmpty).joinToString("/"),
                sourceUri = source.toAppPath().toUriString(),
                deleteSourceAfterCommit = cut
            )
        }
        archiveMutate(targetDirectory, mutations, conflictPolicy, context)
    }

    private fun archiveDelete(paths: List<Path>, context: Context) {
        archiveMutate(
            archivePath = paths.first(),
            mutations = paths.map {
                ArchiveMutation(ArchiveMutationType.DELETE, archiveEntryName(it))
            },
            context = context
        )
    }

    private fun archiveCreateDirectory(path: Path, context: Context) {
        archiveMutate(
            archivePath = requireNotNull(path.parent),
            mutations = listOf(
                ArchiveMutation(ArchiveMutationType.CREATE_DIRECTORY, archiveEntryName(path))
            ),
            context = context
        )
    }

    private fun archiveMutate(
        archivePath: Path,
        mutations: List<ArchiveMutation>,
        conflictPolicy: ArchiveConflictPolicy = ArchiveConflictPolicy.FAIL,
        context: Context
    ) {
        if (!context.ensureProAccess(ProFeature.ADVANCED_ARCHIVE_OPERATIONS)) return
        val archive = archivePath.archiveFile
        val legacyArchive = archive.toLegacyPathOrNull()
            ?: throw IllegalArgumentException("Archive source is unavailable")
        val initial = ArchiveMutationSpec(
            archiveUri = archive.toUriString(),
            mutations = mutations,
            conflictPolicy = conflictPolicy,
            originalFingerprint = ArchiveRewriteEngine.fingerprint(legacyArchive)
        )
        ArchiveMutationStore.save(initial)
        TransferRepository.enqueue(
            TransferOperationSpec(
                id = initial.operationId,
                type = TransferOperationType.ARCHIVE_MODIFY,
                sourceUris = mutations.mapNotNull { it.sourceUri.takeIf(String::isNotEmpty) }
                    .ifEmpty { listOf(initial.archiveUri) },
                destinationUri = initial.archiveUri
            )
        )
        FileOperationService.enqueue(ArchiveModifyFileOperationJob(initial), context)
    }

    private fun archiveEntryName(path: Path): String =
        path.toString().replace('\\', '/').trim('/')

    fun batchRename(operations: List<BatchRenameOperation>, context: Context) {
        FileOperationService.enqueue(BatchRenameFileOperationJob(operations), context)
    }

    fun encrypt(paths: List<Path>, password: CharArray, algorithmId: Int, kdfId: Int, context: Context) {
        FileOperationService.enqueue(EncryptFileOperationJob(paths, password, algorithmId, kdfId), context)
    }

    fun decrypt(paths: List<Path>, password: CharArray, context: Context) {
        FileOperationService.enqueue(DecryptFileOperationJob(paths, password), context)
    }

    fun restoreSeLinuxContext(path: Path, recursive: Boolean, context: Context) {
        FileOperationService.enqueue(RestoreFileSeLinuxContextJob(path, recursive), context)
    }

    fun save(source: Path, target: Path, context: Context) {
        FileOperationService.enqueue(SaveFileOperationJob(source, target), context)
    }

    fun setGroup(path: Path, group: PosixGroup, recursive: Boolean, context: Context) {
        FileOperationService.enqueue(SetFileGroupJob(path, group, recursive), context)
    }

    fun setMode(
        path: Path,
        mode: Set<PosixFileModeBit>,
        recursive: Boolean,
        uppercaseX: Boolean,
        context: Context
    ) {
        FileOperationService.enqueue(SetFileModeJob(path, mode, recursive, uppercaseX), context)
    }

    fun setOwner(path: Path, owner: PosixUser, recursive: Boolean, context: Context) {
        FileOperationService.enqueue(SetFileOwnerJob(path, owner, recursive), context)
    }

    fun setSeLinuxContext(
        path: Path,
        seLinuxContext: String,
        recursive: Boolean,
        context: Context
    ) {
        FileOperationService.enqueue(SetFileSeLinuxContextJob(path, seLinuxContext, recursive), context)
    }

    fun write(
        file: Path,
        content: ByteArray,
        context: Context,
        listener: ((Boolean) -> Unit)?
    ) {
        FileOperationService.enqueue(WriteFileOperationJob(file, content, listener), context)
    }

}
