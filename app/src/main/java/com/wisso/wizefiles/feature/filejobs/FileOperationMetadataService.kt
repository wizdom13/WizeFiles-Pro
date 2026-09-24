// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.R
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixPrincipal
import com.wisso.wizefiles.provider.common.PosixUser
import com.wisso.wizefiles.provider.common.newDirectoryStream
import com.wisso.wizefiles.provider.common.readAttributes
import com.wisso.wizefiles.provider.common.restoreSeLinuxContext
import com.wisso.wizefiles.provider.common.setGroup
import com.wisso.wizefiles.provider.common.setMode
import com.wisso.wizefiles.provider.common.setOwner
import com.wisso.wizefiles.provider.common.setSeLinuxContext
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.provider.common.toModeString
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.storage.StorageFacade
import com.wisso.wizefiles.storage.local.LocalFileNode
import com.wisso.wizefiles.storage.local.LocalTreeTraverser
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

private val metadataStorageFacade = StorageFacade()

internal class FileOperationMetadataService(
    private val job: FileOperationJob,
    private val transferInfo: TransferInfo,
    private val actionAllInfo: FileOperationActionAllInfo
) {
    @Throws(IOException::class)
    fun restoreSeLinuxContext(path: Path, recursive: Boolean) {
        walkMetadataTree(path, recursive) { file, attributes ->
            applyMetadataMutation(
                path = file,
                metadataRequired = false,
                isSkipAll = { actionAllInfo.skipRestoreSeLinuxContextError },
                markSkipAll = { actionAllInfo.skipRestoreSeLinuxContextError = true },
                dialogSpec = { exception ->
                    FileOperationErrorDialogSpec(
                        title = job.getString(R.string.file_job_restore_selinux_context_error_title),
                        message = job.getString(
                            R.string.file_job_restore_selinux_context_error_message_format,
                            job.getFileName(file),
                            exception.toString()
                        ),
                        readOnlyFileStore = job.getReadOnlyFileStore(file, exception),
                        showAll = true,
                        positiveButtonText = job.getString(R.string.retry),
                        negativeButtonText = job.getString(R.string.skip),
                        neutralButtonText = job.getString(android.R.string.cancel)
                    )
                },
                notify = { postRestoreSeLinuxContextNotification(file) }
            ) {
                val options = if (attributes.isSymbolicLink) arrayOf(LinkOption.NOFOLLOW_LINKS) else emptyArray<LinkOption>()
                file.restoreSeLinuxContext(*options)
            }
        }
    }

    @Throws(IOException::class)
    fun setGroup(path: Path, group: PosixGroup, recursive: Boolean) {
        walkMetadataTree(path, recursive) { file, attributes ->
            applyMetadataMutation(
                path = file,
                isSkipAll = { actionAllInfo.skipSetGroupError },
                markSkipAll = { actionAllInfo.skipSetGroupError = true },
                dialogSpec = { exception ->
                    FileOperationErrorDialogSpec(
                        title = job.getString(R.string.file_job_set_group_error_title_format, job.getFileName(file)),
                        message = job.getString(
                            R.string.file_job_set_group_error_message_format,
                            getPrincipalName(group),
                            exception.toString()
                        ),
                        readOnlyFileStore = job.getReadOnlyFileStore(file, exception),
                        showAll = true,
                        positiveButtonText = job.getString(R.string.retry),
                        negativeButtonText = job.getString(R.string.skip),
                        neutralButtonText = job.getString(android.R.string.cancel)
                    )
                },
                notify = { postSetGroupNotification(file) }
            ) {
                val options = if (attributes.isSymbolicLink) arrayOf(LinkOption.NOFOLLOW_LINKS) else emptyArray<LinkOption>()
                file.setGroup(group, *options)
            }
        }
    }

    @Throws(IOException::class)
    fun setMode(
        path: Path,
        recursive: Boolean,
        modeForPath: (Path, BasicFileAttributes) -> Set<PosixFileModeBit>?
    ) {
        walkMetadataTree(path, recursive) { file, attributes ->
            val mode = modeForPath(file, attributes)
            if (mode == null) {
                transferInfo.skipFileIgnoringSize()
                return@walkMetadataTree
            }
            applyMetadataMutation(
                path = file,
                isSkipAll = { actionAllInfo.skipSetModeError },
                markSkipAll = { actionAllInfo.skipSetModeError = true },
                dialogSpec = { exception ->
                    FileOperationErrorDialogSpec(
                        title = job.getString(R.string.file_job_set_mode_error_title_format, job.getFileName(file)),
                        message = job.getString(
                            R.string.file_job_set_mode_error_message_format,
                            mode.toModeString(),
                            exception.toString()
                        ),
                        readOnlyFileStore = job.getReadOnlyFileStore(file, exception),
                        showAll = true,
                        positiveButtonText = job.getString(R.string.retry),
                        negativeButtonText = job.getString(R.string.skip),
                        neutralButtonText = job.getString(android.R.string.cancel)
                    )
                },
                notify = { postSetModeNotification(file) }
            ) {
                file.setMode(mode)
            }
        }
    }

    @Throws(IOException::class)
    fun setOwner(path: Path, owner: PosixUser, recursive: Boolean) {
        walkMetadataTree(path, recursive) { file, attributes ->
            applyMetadataMutation(
                path = file,
                isSkipAll = { actionAllInfo.skipSetOwnerError },
                markSkipAll = { actionAllInfo.skipSetOwnerError = true },
                dialogSpec = { exception ->
                    FileOperationErrorDialogSpec(
                        title = job.getString(R.string.file_job_set_owner_error_title_format, job.getFileName(file)),
                        message = job.getString(
                            R.string.file_job_set_owner_error_message_format,
                            getPrincipalName(owner),
                            exception.toString()
                        ),
                        readOnlyFileStore = job.getReadOnlyFileStore(file, exception),
                        showAll = true,
                        positiveButtonText = job.getString(R.string.retry),
                        negativeButtonText = job.getString(R.string.skip),
                        neutralButtonText = job.getString(android.R.string.cancel)
                    )
                },
                notify = { postSetOwnerNotification(file) }
            ) {
                val options = if (attributes.isSymbolicLink) arrayOf(LinkOption.NOFOLLOW_LINKS) else emptyArray<LinkOption>()
                file.setOwner(owner, *options)
            }
        }
    }

    @Throws(IOException::class)
    fun setSeLinuxContext(path: Path, seLinuxContext: String, recursive: Boolean) {
        walkMetadataTree(path, recursive) { file, attributes ->
            applyMetadataMutation(
                path = file,
                isSkipAll = { actionAllInfo.skipSetSeLinuxContextError },
                markSkipAll = { actionAllInfo.skipSetSeLinuxContextError = true },
                dialogSpec = { exception ->
                    FileOperationErrorDialogSpec(
                        title = job.getString(
                            R.string.file_job_set_selinux_context_error_title_format,
                            job.getFileName(file)
                        ),
                        message = job.getString(
                            R.string.file_job_set_selinux_context_error_message_format,
                            seLinuxContext,
                            exception.toString()
                        ),
                        readOnlyFileStore = job.getReadOnlyFileStore(file, exception),
                        showAll = true,
                        positiveButtonText = job.getString(R.string.retry),
                        negativeButtonText = job.getString(R.string.skip),
                        neutralButtonText = job.getString(android.R.string.cancel)
                    )
                },
                notify = { postSetSeLinuxContextNotification(file) }
            ) {
                val options = if (attributes.isSymbolicLink) arrayOf(LinkOption.NOFOLLOW_LINKS) else emptyArray<LinkOption>()
                file.setSeLinuxContext(seLinuxContext.toByteString(), *options)
            }
        }
    }

    @Throws(IOException::class)
    private inline fun walkMetadataTree(
        start: Path,
        recursive: Boolean,
        crossinline visit: (Path, BasicFileAttributes) -> Unit
    ) {
        job.walkFileTreeForMetadata(start, recursive, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult = visitFile(directory, attributes)

            @Throws(IOException::class)
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                visit(file, attributes)
                job.throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
                var retryCount = 0
                var currentException = exception!!
                while (true) {
                    val decision = job.resolveTreeFailure(
                        operationType = FileOperationType.METADATA,
                        phase = FileOperationPhase.EXECUTE,
                        path = file,
                        exception = currentException,
                        allowSkip = false,
                        retryCount = retryCount,
                        metadataRequired = true,
                        dialogSpec = FileOperationErrorDialogSpec(
                            title = job.getString(R.string.file_job_set_mode_error_title_format, job.getFileName(file)),
                            message = currentException.toString(),
                            readOnlyFileStore = job.getReadOnlyFileStore(file, currentException),
                            showAll = true,
                            positiveButtonText = job.getString(R.string.retry),
                            negativeButtonText = job.getString(R.string.skip),
                            neutralButtonText = job.getString(android.R.string.cancel)
                        ),
                        onSkip = { transferInfo.skipFileIgnoringSize() }
                    )
                    if (decision != FileOperationRetryDecision.RETRY) {
                        return FileVisitResult.CONTINUE
                    }
                    if (retryCount >= FileOperationStatePolicy.MAX_RETRY_ATTEMPTS) {
                        throw IOException(currentException)
                    }
                    ++retryCount
                    try {
                        val attributes = file.readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                        visit(file, attributes)
                        job.throwIfInterrupted()
                        return FileVisitResult.CONTINUE
                    } catch (e: IOException) {
                        currentException = e
                    }
                }
            }

            @Throws(IOException::class)
            override fun postVisitDirectory(
                directory: Path,
                exception: IOException?
            ): FileVisitResult {
                if (exception == null) {
                    return FileVisitResult.CONTINUE
                }
                var retryCount = 0
                var currentException = exception!!
                while (true) {
                    val decision = job.resolveTreeFailure(
                        operationType = FileOperationType.METADATA,
                        phase = FileOperationPhase.FINALIZE,
                        path = directory,
                        exception = currentException,
                        allowSkip = false,
                        retryCount = retryCount,
                        metadataRequired = true,
                        dialogSpec = FileOperationErrorDialogSpec(
                            title = job.getString(R.string.file_job_set_mode_error_title_format, job.getFileName(directory)),
                            message = currentException.toString(),
                            readOnlyFileStore = job.getReadOnlyFileStore(directory, currentException),
                            showAll = true,
                            positiveButtonText = job.getString(R.string.retry),
                            negativeButtonText = job.getString(R.string.skip),
                            neutralButtonText = job.getString(android.R.string.cancel)
                        ),
                        onSkip = { transferInfo.skipFileIgnoringSize() }
                    )
                    if (decision != FileOperationRetryDecision.RETRY) {
                        return FileVisitResult.CONTINUE
                    }
                    if (retryCount >= FileOperationStatePolicy.MAX_RETRY_ATTEMPTS) {
                        throw IOException(currentException)
                    }
                    ++retryCount
                    // The tree walker cannot safely re-enter postVisitDirectory callbacks.
                    // We loop through policy retries and then abort via the bounded retry policy.
                    currentException = IOException(
                        "Directory finalization retry requires retry-aware walker",
                        currentException
                    )
                }
            }
        })
    }

    @Throws(IOException::class)
    private inline fun applyMetadataMutation(
        path: Path,
        metadataRequired: Boolean = true,
        crossinline isSkipAll: () -> Boolean,
        crossinline markSkipAll: () -> Unit,
        crossinline dialogSpec: (IOException) -> FileOperationErrorDialogSpec,
        crossinline notify: () -> Unit,
        crossinline mutate: () -> Unit
    ) {
        job.runWithRetryPolicy(
            block = {
                mutate()
                transferInfo.incrementTransferredFileCount()
                notify()
            },
            onIOException = { exception, _ ->
                if (isSkipAll()) {
                    transferInfo.skipFileIgnoringSize()
                    notify()
                    return@runWithRetryPolicy FileOperationRetryDecision.COMPLETE
                }
                job.resolveTreeFailure(
                    operationType = FileOperationType.METADATA,
                    phase = FileOperationPhase.FINALIZE,
                    path = path,
                    exception = exception,
                    allowSkip = !metadataRequired,
                    hasPartialOutput = true,
                    metadataRequired = metadataRequired,
                    metadataOptional = !metadataRequired,
                    onMetadataWarning = {
                        transferInfo.recordMetadataWarning(path, exception)
                        },
                    dialogSpec = dialogSpec(exception),
                    onSkip = {
                        transferInfo.skipFileIgnoringSize()
                        notify()
                    },
                    onSkipAll = { markSkipAll() }
                )
            }
        )
    }

    private fun postRestoreSeLinuxContextNotification(currentPath: Path) {
        job.postTransferCountNotification(
            transferInfo,
            currentPath,
            R.string.file_job_restore_selinux_context_notification_title_one_format,
            R.plurals.file_job_restore_selinux_context_notification_title_multiple_format
        )
    }

    private fun postSetGroupNotification(currentPath: Path) {
        job.postTransferCountNotification(
            transferInfo,
            currentPath,
            R.string.file_job_set_group_notification_title_one_format,
            R.plurals.file_job_set_group_notification_title_multiple_format
        )
    }

    private fun postSetModeNotification(currentPath: Path) {
        job.postTransferCountNotification(
            transferInfo,
            currentPath,
            R.string.file_job_set_mode_notification_title_one_format,
            R.plurals.file_job_set_mode_notification_title_multiple_format
        )
    }

    private fun postSetOwnerNotification(currentPath: Path) {
        job.postTransferCountNotification(
            transferInfo,
            currentPath,
            R.string.file_job_set_owner_notification_title_one_format,
            R.plurals.file_job_set_owner_notification_title_multiple_format
        )
    }

    private fun postSetSeLinuxContextNotification(currentPath: Path) {
        job.postTransferCountNotification(
            transferInfo,
            currentPath,
            R.string.file_job_set_selinux_context_notification_title_one_format,
            R.plurals.file_job_set_selinux_context_notification_title_multiple_format
        )
    }

    private fun getPrincipalName(principal: PosixPrincipal): String =
        principal.name ?: principal.id.toString()
}

// The attributes for start path prefers following links, but falls back to not following.
// FileVisitResult returned from visitor may be ignored and always considered CONTINUE.
@Throws(IOException::class)
internal fun FileOperationJob.walkFileTreeForMetadata(
    start: Path,
    recursive: Boolean,
    visitor: FileVisitor<in Path>
): Path {
    val attributes = try {
        start.readAttributes(BasicFileAttributes::class.java)
    } catch (ignored: IOException) {
        try {
            start.readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (e: IOException) {
            visitor.visitFileFailed(start, e)
            return start
        }
    }
    if (!recursive || !attributes.isDirectory) {
        visitor.visitFile(start, attributes)
        return start
    }
    if (start.isLinuxPath) {
        val startFile = start.toFile()
        visitor.preVisitDirectory(start, attributes)
        LocalTreeTraverser.walkPreOrderWithPost(
            LocalFileNode(startFile),
            onDirectory = { node ->
                if (node.file == startFile) {
                    return@walkPreOrderWithPost true
                }
                val nodePath = Paths.get(node.file.path)
                val nodeAttributes = metadataStorageFacade.statLocal(node)?.toBasicFileAttributes() ?: run {
                    visitor.visitFileFailed(nodePath, IOException("Failed to stat path: ${node.file.path}"))
                    return@walkPreOrderWithPost false
                }
                visitor.preVisitDirectory(nodePath, nodeAttributes)
                true
            },
            onFile = { node ->
                val nodePath = Paths.get(node.file.path)
                val nodeAttributes = metadataStorageFacade.statLocal(node)?.toBasicFileAttributes() ?: run {
                    visitor.visitFileFailed(nodePath, IOException("Failed to stat path: ${node.file.path}"))
                    return@walkPreOrderWithPost
                }
                visitor.visitFile(nodePath, nodeAttributes)
            },
            onDirectoryPost = { node ->
                if (node.file != startFile) {
                    visitor.postVisitDirectory(Paths.get(node.file.path), null)
                }
            }
        )
        visitor.postVisitDirectory(start, null)
        return start
    }
    val directoryStream = try {
        start.newDirectoryStream()
    } catch (e: IOException) {
        visitor.visitFileFailed(start, e)
        return start
    }
    directoryStream.use {
        visitor.preVisitDirectory(start, attributes)
        try {
            directoryStream.forEach { Files.walkFileTree(it, visitor) }
        } catch (e: DirectoryIteratorException) {
            visitor.postVisitDirectory(start, e.cause)
            return start
        }
    }
    visitor.postVisitDirectory(start, null)
    return start
}

private fun com.wisso.wizefiles.storage.FileMetadata.toBasicFileAttributes(): BasicFileAttributes =
    object : BasicFileAttributes {
        private val modifiedTime = FileTime.fromMillis(lastModifiedEpochMillis ?: 0L)

        override fun lastModifiedTime(): FileTime = modifiedTime

        override fun lastAccessTime(): FileTime = modifiedTime

        override fun creationTime(): FileTime = modifiedTime

        override fun isRegularFile(): Boolean = !isDirectory

        override fun isDirectory(): Boolean = isDirectory

        override fun isSymbolicLink(): Boolean = false

        override fun isOther(): Boolean = false

        override fun size(): Long = sizeBytes ?: 0L

        override fun fileKey(): Any? = null
    }
