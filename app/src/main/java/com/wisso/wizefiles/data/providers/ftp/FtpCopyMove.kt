// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp

import com.wisso.wizefiles.provider.common.CopyOptions
import com.wisso.wizefiles.provider.common.copyTo
import com.wisso.wizefiles.storage.ProviderFailureSignal
import com.wisso.wizefiles.storage.ProviderJvmFailureClassifier
import java.io.IOException
import java.nio.channels.ClosedByInterruptException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption

internal object FtpCopyMove {
    @Throws(IOException::class)
    fun copy(
        source: FtpPath,
        target: FtpPath,
        copyOptions: CopyOptions,
        operations: FtpOperations = FtpOperations.DEFAULT
    ) {
        throwIfInterrupted()
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val sourceFile = try {
            operations.listFile(source, copyOptions.noFollowLinks)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(source.toString())
        }
        val targetFile = try {
            operations.listFileOrNull(target, true)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(target.toString())
        }
        val sourceSize = sourceFile.size
        if (targetFile != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            try {
                operations.delete(target, targetFile.isDirectory)
            } catch (e: IOException) {
                throw e.toFileSystemExceptionForFtp(target.toString())
            }
        }
        when {
            sourceFile.isDirectory -> {
                try {
                    operations.createDirectory(target)
                } catch (e: IOException) {
                    throw e.toFileSystemExceptionForFtp(target.toString())
                }
                copyOptions.progressListener?.invoke(sourceSize)
            }
            sourceFile.isSymbolicLink ->
                throw UnsupportedOperationException("Cannot copy symbolic links")
            else -> copyFile(source, target, sourceFile.isDirectory, copyOptions, operations)
        }
        if (!sourceFile.isSymbolicLink) {
            var metadataFailure: String? = null
            sourceFile.timestamp?.let { timestamp ->
                try {
                    operations.setLastModifiedTime(target, timestamp.toInstant())
                } catch (e: IOException) {
                    metadataFailure = e.message
                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                }
            }
            copyOptions.metadataListener?.invoke(
                FtpMetadataPreservationPolicy.report(copyOptions.copyAttributes, metadataFailure)
            )
        }
    }

    private fun copyFile(
        source: FtpPath,
        target: FtpPath,
        sourceIsDirectory: Boolean,
        copyOptions: CopyOptions,
        operations: FtpOperations
    ) {
        val sourceInputStream = try {
            operations.retrieveFile(source)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(source.toString())
        }
        var targetOpened = false
        try {
            sourceInputStream.use { input ->
                val targetOutputStream = try {
                    operations.storeFile(target)
                } catch (e: IOException) {
                    throw e.toFileSystemExceptionForFtp(target.toString())
                }
                targetOpened = true
                targetOutputStream.use { output ->
                    input.copyTo(
                        output,
                        copyOptions.progressIntervalMillis,
                        copyOptions.progressListener
                    )
                }
            }
        } catch (failure: IOException) {
            val translated = failure.toFileSystemExceptionForFtp(
                if (targetOpened) target.toString() else source.toString()
            )
            if (targetOpened) cleanupTarget(target, sourceIsDirectory, translated, operations)
            throw translated
        } catch (failure: Throwable) {
            if (targetOpened) cleanupTarget(target, sourceIsDirectory, failure, operations)
            throw failure
        }
    }

    @Throws(IOException::class)
    fun move(
        source: FtpPath,
        target: FtpPath,
        copyOptions: CopyOptions,
        operations: FtpOperations = FtpOperations.DEFAULT
    ) {
        throwIfInterrupted()
        val sourceFile = try {
            operations.listFile(source, copyOptions.noFollowLinks)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(source.toString())
        }
        val targetFile = try {
            operations.listFileOrNull(target, true)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(target.toString())
        }
        val sourceSize = sourceFile.size
        if (targetFile != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            try {
                operations.delete(target, targetFile.isDirectory)
            } catch (e: IOException) {
                throw e.toFileSystemExceptionForFtp(target.toString())
            }
        }
        try {
            operations.renameFile(source, target)
            copyOptions.progressListener?.invoke(sourceSize)
            return
        } catch (e: IOException) {
            val translated = e.toFileSystemExceptionForFtp(source.toString(), target.toString())
            if (copyOptions.atomicMove || !translated.allowsCopyFallback()) throw translated
        }

        throwIfInterrupted()
        var fallbackOptions = copyOptions
        if (!fallbackOptions.copyAttributes || !fallbackOptions.noFollowLinks) {
            fallbackOptions = CopyOptions(
                fallbackOptions.replaceExisting,
                true,
                false,
                true,
                fallbackOptions.progressIntervalMillis,
                fallbackOptions.progressListener,
                fallbackOptions.metadataListener
            )
        }
        copy(source, target, fallbackOptions, operations)
        throwIfInterrupted()
        try {
            operations.delete(source, sourceFile.isDirectory)
        } catch (e: IOException) {
            val translated = e.toFileSystemExceptionForFtp(source.toString())
            if (translated !is NoSuchFileException) {
                try {
                    operations.delete(target, sourceFile.isDirectory)
                } catch (cleanup: IOException) {
                    translated.addSuppressed(
                        cleanup.toFileSystemExceptionForFtp(target.toString())
                    )
                }
            }
            throw translated
        }
    }

    private fun cleanupTarget(
        target: FtpPath,
        isDirectory: Boolean,
        primary: Throwable,
        operations: FtpOperations
    ) {
        try {
            operations.delete(target, isDirectory)
        } catch (cleanup: IOException) {
            primary.addSuppressed(cleanup.toFileSystemExceptionForFtp(target.toString()))
        }
    }

    private fun IOException.allowsCopyFallback(): Boolean =
        ProviderJvmFailureClassifier.classify(this) == ProviderFailureSignal.PERMANENT

    private fun throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted) throw ClosedByInterruptException()
    }
}
