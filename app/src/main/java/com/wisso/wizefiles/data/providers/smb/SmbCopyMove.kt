// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb

import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.protocol.commons.EnumWithValue
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import com.wisso.wizefiles.provider.common.CopyOptions
import com.wisso.wizefiles.provider.smb.client.SmbClientException
import com.wisso.wizefiles.provider.smb.client.FileInformation
import com.wisso.wizefiles.storage.ProviderFailureSignal
import com.wisso.wizefiles.storage.ProviderJvmFailureClassifier
import com.wisso.wizefiles.util.enumSetOf
import com.wisso.wizefiles.util.hasBits
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.channels.ClosedByInterruptException

internal object SmbCopyMove {
    @Throws(IOException::class)
    fun copy(
        source: SmbPath,
        target: SmbPath,
        copyOptions: CopyOptions,
        operations: SmbMutationOperations = SmbMutationOperations.DEFAULT
    ) {
        throwIfInterrupted()
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val sourceInformation = try {
            operations.getPathInformation(source, copyOptions.noFollowLinks)
        } catch (e: SmbClientException) {
            throw e.toFileSystemException(source.toString())
        }
        sourceInformation as? FileInformation
            ?: throw FileSystemException(source.toString(), null, "Cannot copy shares")
        val targetInformation = try {
            operations.getPathInformation(target, true)
        } catch (e: SmbClientException) {
            val exception = e.toFileSystemException(target.toString())
            if (exception !is NoSuchFileException) {
                throw exception
            }
            // Ignored.
            null
        }
        if (targetInformation != null) {
            targetInformation as? FileInformation
                ?: throw FileSystemException(target.toString(), null, "Cannot copy shares")
            if (SmbFileKey(source, sourceInformation.fileId)
                == SmbFileKey(target, targetInformation.fileId)) {
                copyOptions.progressListener?.invoke(sourceInformation.endOfFile)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            // Symbolic links may not be supported so we cannot simply delete the target here.
        }
        val sourceIsReparsePoint = sourceInformation.fileAttributes
            .hasBits(FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.value)
        val sourceIsDirectory = !sourceIsReparsePoint &&
            sourceInformation.fileAttributes.hasBits(FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value)
        val sourceIsRegularFile = !sourceIsDirectory && !sourceIsReparsePoint
        val attributesToCopy = if (copyOptions.copyAttributes) {
            EnumWithValue.EnumUtils.toEnumSet(
                sourceInformation.fileAttributes, FileAttributes::class.java
            )
        } else {
            enumSetOf(FileAttributes.FILE_ATTRIBUTE_NORMAL)
        }
        if (sourceIsRegularFile) {
            if (targetInformation != null) {
                try {
                    operations.delete(target)
                } catch (e: SmbClientException) {
                    val exception = e.toFileSystemException(target.toString())
                    if (exception !is NoSuchFileException) {
                        throw exception
                    }
                }
            }
            try {
                operations.copyFile(
                    source, target, copyOptions.copyAttributes, copyOptions.noFollowLinks,
                    copyOptions.progressIntervalMillis, copyOptions.progressListener
                )
            } catch (e: SmbClientException) {
                (e.cause as? InterruptedIOException)?.let { throw it }
                e.maybeThrowInvalidFileNameException(target.toString())
                throw e.toFileSystemException(target.toString())
            }
        } else if (sourceIsDirectory) {
            if (targetInformation != null) {
                try {
                    operations.delete(target)
                } catch (e: SmbClientException) {
                    val exception = e.toFileSystemException(target.toString())
                    if (exception !is NoSuchFileException) {
                        throw exception
                    }
                }
            }
            try {
                operations.createDirectory(target, attributesToCopy)
            } catch (e: SmbClientException) {
                e.maybeThrowInvalidFileNameException(target.toString())
                throw e.toFileSystemException(target.toString())
            }
            copyOptions.progressListener?.invoke(sourceInformation.endOfFile)
        } else if (sourceIsReparsePoint) {
            val sourceReparseData = try {
                operations.readSymbolicLink(source)
            } catch (e: SmbClientException) {
                throw e.toFileSystemException(source.toString())
            }
            try {
                operations.createSymbolicLink(target, sourceReparseData, attributesToCopy)
            } catch (e: SmbClientException) {
                val exception = e.toFileSystemException(target.toString())
                if (exception is FileAlreadyExistsException && copyOptions.replaceExisting) {
                    try {
                        operations.delete(target)
                    } catch (e2: SmbClientException) {
                        if (e2.toFileSystemException(target.toString()) !is NoSuchFileException) {
                            e2.addSuppressed(exception)
                            throw e2.toFileSystemException(target.toString())
                        }
                    }
                    try {
                        operations.createSymbolicLink(target, sourceReparseData, attributesToCopy)
                    } catch (e2: SmbClientException) {
                        e2.addSuppressed(exception)
                        throw e2.toFileSystemException(target.toString())
                    }
                }
                e.maybeThrowInvalidFileNameException(target.toString())
                throw e.toFileSystemException(target.toString())
            }
            copyOptions.progressListener?.invoke(sourceInformation.endOfFile)
        } else {
            throw AssertionError()
        }
        // Apply timestamps after content writes so the final write cannot overwrite modified time.
        var metadataDetail: String? = null
        try {
            val fileInformation = FileBasicInformation(
                if (copyOptions.copyAttributes) sourceInformation.creationTime else FileTime(0),
                if (copyOptions.copyAttributes) sourceInformation.lastAccessTime else FileTime(0),
                sourceInformation.lastWriteTime,
                if (copyOptions.copyAttributes) sourceInformation.changeTime else FileTime(0), 0
            )
            operations.setFileInformation(target, true, fileInformation)
        } catch (e: SmbClientException) {
            metadataDetail = e.message?.take(com.wisso.wizefiles.storage.MetadataPreservation.MAX_DETAIL_LENGTH)
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        }
        copyOptions.metadataListener?.invoke(
            SmbMetadataPreservationPolicy.report(copyOptions.copyAttributes, metadataDetail)
        )
    }

    @Throws(IOException::class)
    fun move(
        source: SmbPath,
        target: SmbPath,
        copyOptions: CopyOptions,
        operations: SmbMutationOperations = SmbMutationOperations.DEFAULT
    ) {
        throwIfInterrupted()
        val sourceInformation = try {
            operations.getPathInformation(source, true)
        } catch (e: SmbClientException) {
            throw e.toFileSystemException(source.toString())
        }
        sourceInformation as? FileInformation
            ?: throw FileSystemException(source.toString(), null, "Cannot move shares")
        val targetInformation = try {
            operations.getPathInformation(target, true)
        } catch (e: SmbClientException) {
            val exception = e.toFileSystemException(target.toString())
            if (exception !is NoSuchFileException) {
                throw exception
            }
            // Ignored.
            null
        }
        if (targetInformation != null) {
            targetInformation as? FileInformation
                ?: throw FileSystemException(target.toString(), null, "Cannot move shares")
            if (SmbFileKey(source, sourceInformation.fileId)
                == SmbFileKey(target, targetInformation.fileId)) {
                copyOptions.progressListener?.invoke(sourceInformation.endOfFile)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            try {
                operations.delete(target)
            } catch (e: SmbClientException) {
                throw e.toFileSystemException(target.toString())
            }
        }
        var renameSuccessful = false
        try {
            operations.rename(source, target)
            renameSuccessful = true
        } catch (e: SmbClientException) {
            if (copyOptions.atomicMove) {
                e.maybeThrowAtomicMoveNotSupportedException(source.toString(), target.toString())
                e.maybeThrowInvalidFileNameException(target.toString())
                throw e.toFileSystemException(source.toString(), target.toString())
            }
            e.maybeThrowInvalidFileNameException(target.toString())
            val exception = e.toFileSystemException(source.toString(), target.toString())
            if (!exception.allowsCopyFallback()) throw exception
        }
        if (renameSuccessful) {
            copyOptions.progressListener?.invoke(sourceInformation.endOfFile)
            return
        }
        if (copyOptions.atomicMove) {
            throw AssertionError()
        }
        throwIfInterrupted()
        var copyOptions = copyOptions
        if (!copyOptions.copyAttributes || !copyOptions.noFollowLinks) {
            copyOptions = CopyOptions(
                copyOptions.replaceExisting, true, false, true, copyOptions.progressIntervalMillis,
                copyOptions.progressListener, copyOptions.metadataListener
            )
        }
        copy(source, target, copyOptions, operations)
        throwIfInterrupted()
        try {
            operations.delete(source)
        } catch (e: SmbClientException) {
            if (e.toFileSystemException(source.toString()) !is NoSuchFileException) {
                try {
                    operations.delete(target)
                } catch (e2: SmbClientException) {
                    e.addSuppressed(e2.toFileSystemException(target.toString()))
                }
            }
            throw e.toFileSystemException(source.toString())
        }
    }

    private fun IOException.allowsCopyFallback(): Boolean =
        ProviderJvmFailureClassifier.classify(this) == ProviderFailureSignal.PERMANENT

    private fun throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted) throw ClosedByInterruptException()
    }
}
