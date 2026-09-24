package com.wisso.wizefiles.provider.sftp

import java.io.IOException
import java.time.Instant
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.channels.ClosedByInterruptException
import com.wisso.wizefiles.provider.common.CopyOptions
import com.wisso.wizefiles.provider.common.copyTo
import com.wisso.wizefiles.provider.common.newInputStream
import com.wisso.wizefiles.provider.common.newOutputStream
import com.wisso.wizefiles.provider.sftp.client.SftpClientException
import com.wisso.wizefiles.storage.ProviderFailureSignal
import com.wisso.wizefiles.storage.ProviderJvmFailureClassifier
import com.wisso.wizefiles.util.enumSetOf
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode

internal object SftpCopyMove {
    @Throws(IOException::class)
    fun copy(
        source: SftpPath,
        target: SftpPath,
        copyOptions: CopyOptions,
        operations: SftpOperations = SftpOperations.DEFAULT
    ) {
        throwIfInterrupted()
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val sourceAttributes = try {
            if (copyOptions.noFollowLinks) operations.lstat(source) else operations.stat(source)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(source.toString())
        }
        if (!sourceAttributes.has(FileAttributes.Flag.MODE)) {
            throw FileSystemException(
                source.toString(), null, "Missing SSH_FILEXFER_ATTR_PERMISSIONS"
            )
        }
        val targetAttributes = try {
            operations.lstat(target)
        } catch (e: SftpClientException) {
            val exception = e.toFileSystemException(target.toString())
            if (exception !is NoSuchFileException) {
                throw exception
            }
            // Ignored.
            null
        }
        val sourceSize = if (sourceAttributes.has(FileAttributes.Flag.SIZE)) {
            sourceAttributes.size
        } else {
            0
        }
        if (targetAttributes != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            // Symbolic links may not be supported so we cannot simply delete the target here.
        }
        val sourceType = sourceAttributes.type
        val sourceModeAttributes = FileAttributes.Builder()
            .apply {
                if (sourceAttributes.has(FileAttributes.Flag.MODE)) {
                    withPermissions(sourceAttributes.mode.mask)
                }
            }
            .build()
        when (sourceType) {
            FileMode.Type.REGULAR -> {
                if (targetAttributes != null) {
                    try {
                        operations.remove(target)
                    } catch (e: SftpClientException) {
                        val exception = e.toFileSystemException(target.toString())
                        if (exception !is NoSuchFileException) {
                            throw exception
                        }
                    }
                }
                val sourceInputStream = try {
                    operations.openByteChannel(source, enumSetOf(OpenMode.READ), FileAttributes.EMPTY)
                } catch (e: SftpClientException) {
                    throw e.toFileSystemException(source.toString())
                }.newInputStream()
                var targetOpened = false
                try {
                    sourceInputStream.use { input ->
                        val targetFlags = enumSetOf(OpenMode.WRITE, OpenMode.TRUNC, OpenMode.CREAT)
                        if (!copyOptions.replaceExisting) targetFlags += OpenMode.EXCL
                        val targetOutputStream = try {
                            operations.openByteChannel(target, targetFlags, sourceModeAttributes)
                        } catch (e: SftpClientException) {
                            throw e.toFileSystemException(target.toString())
                        }.newOutputStream()
                        targetOpened = true
                        targetOutputStream.use { output ->
                            input.copyTo(
                                output, copyOptions.progressIntervalMillis,
                                copyOptions.progressListener
                            )
                        }
                    }
                } catch (failure: IOException) {
                    val translated = SftpClientException(failure).toFileSystemException(
                        if (targetOpened) target.toString() else source.toString()
                    )
                    if (targetOpened) cleanupTarget(target, translated, operations)
                    throw translated
                } catch (failure: Throwable) {
                    if (targetOpened) cleanupTarget(target, failure, operations)
                    throw failure
                }
            }
            FileMode.Type.DIRECTORY -> {
                if (targetAttributes != null) {
                    try {
                        operations.remove(target)
                    } catch (e: SftpClientException) {
                        val exception = e.toFileSystemException(target.toString())
                        if (exception !is NoSuchFileException) {
                            throw exception
                        }
                    }
                }
                try {
                    operations.mkdir(target, sourceModeAttributes)
                } catch (e: SftpClientException) {
                    throw e.toFileSystemException(target.toString())
                }
                copyOptions.progressListener?.invoke(sourceSize)
            }
            FileMode.Type.SYMLINK -> {
                val sourceTarget = try {
                    operations.readlink(source)
                } catch (e: SftpClientException) {
                    throw e.toFileSystemException(source.toString())
                }
                try {
                    operations.symlink(target, sourceTarget)
                } catch (e: SftpClientException) {
                    val exception = e.toFileSystemException(target.toString())
                    if (exception is FileAlreadyExistsException && copyOptions.replaceExisting) {
                        try {
                            operations.remove(target)
                        } catch (e2: SftpClientException) {
                            if (e2.toFileSystemException(target.toString())
                                    !is NoSuchFileException) {
                                e2.addSuppressed(exception)
                                throw e2.toFileSystemException(target.toString())
                            }
                        }
                        try {
                            operations.symlink(target, sourceTarget)
                        } catch (e2: SftpClientException) {
                            e2.addSuppressed(exception)
                            throw e2.toFileSystemException(target.toString())
                        }
                        copyOptions.progressListener?.invoke(sourceSize)
                        return
                    }
                    throw e.toFileSystemException(target.toString())
                }
                copyOptions.progressListener?.invoke(sourceSize)
            }
            else -> throw FileSystemException(source.toString(), null, "type $sourceType")
        }
        // We don't take error when copying attribute fatal, so errors will only be logged from now
        // on.
        if (sourceType != FileMode.Type.SYMLINK) {
            val attributes = FileAttributes.Builder()
                .apply {
                    if (copyOptions.copyAttributes
                        && sourceAttributes.has(FileAttributes.Flag.UIDGID)) {
                        withUIDGID(sourceAttributes.uid, sourceAttributes.gid)
                    }
                    if (sourceAttributes.type != FileMode.Type.SYMLINK
                        && sourceAttributes.has(FileAttributes.Flag.MODE)) {
                        withPermissions(sourceAttributes.mode.mask)
                    }
                    if (sourceAttributes.has(FileAttributes.Flag.ACMODTIME)) {
                        withAtimeMtime(
                            if (copyOptions.copyAttributes) {
                                sourceAttributes.atime
                            } else {
                                // We cannot leave atime unchanged in SFTP, but since we've just
                                // written the file, its atime is simply now.
                                Instant.now().epochSecond
                            }, sourceAttributes.mtime
                        )
                    }
                }
                .build()
            var metadataFailure: String? = null
            try {
                operations.setstat(target, attributes)
            } catch (e: SftpClientException) {
                metadataFailure = e.message
                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            }
            copyOptions.metadataListener?.invoke(
                SftpMetadataPreservationPolicy.report(copyOptions.copyAttributes, metadataFailure)
            )
        }
    }

    @Throws(IOException::class)
    fun move(
        source: SftpPath,
        target: SftpPath,
        copyOptions: CopyOptions,
        operations: SftpOperations = SftpOperations.DEFAULT
    ) {
        throwIfInterrupted()
        val sourceAttributes = try {
            operations.lstat(source)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(source.toString())
        }
        if (!sourceAttributes.has(FileAttributes.Flag.MODE)) {
            throw FileSystemException(
                source.toString(), null, "Missing SSH_FILEXFER_ATTR_PERMISSIONS"
            )
        }
        val targetAttributes = try {
            operations.lstat(target)
        } catch (e: SftpClientException) {
            val exception = e.toFileSystemException(target.toString())
            if (exception !is NoSuchFileException) {
                throw exception
            }
            // Ignored.
            null
        }
        val sourceSize = if (sourceAttributes.has(FileAttributes.Flag.SIZE)) {
            sourceAttributes.size
        } else {
            0
        }
        if (targetAttributes != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            try {
                operations.remove(target)
            } catch (e: SftpClientException) {
                throw e.toFileSystemException(target.toString())
            }
        }
        var renameSuccessful = false
        try {
            operations.rename(source, target)
            renameSuccessful = true
        } catch (e: SftpClientException) {
            val exception = e.toFileSystemException(source.toString(), target.toString())
            if (copyOptions.atomicMove || !exception.allowsCopyFallback()) throw exception
        }
        if (renameSuccessful) {
            copyOptions.progressListener?.invoke(sourceSize)
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
            operations.remove(source)
        } catch (e: SftpClientException) {
            if (e.toFileSystemException(source.toString()) !is NoSuchFileException) {
                try {
                    operations.remove(target)
                } catch (e2: SftpClientException) {
                    e.addSuppressed(e2.toFileSystemException(target.toString()))
                }
            }
            throw e.toFileSystemException(source.toString())
        }
    }

    private fun cleanupTarget(target: SftpPath, primary: Throwable, operations: SftpOperations) {
        try {
            operations.remove(target)
        } catch (cleanup: SftpClientException) {
            primary.addSuppressed(cleanup.toFileSystemException(target.toString()))
        }
    }

    private fun IOException.allowsCopyFallback(): Boolean =
        ProviderJvmFailureClassifier.classify(this) == ProviderFailureSignal.PERMANENT

    private fun throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted) throw ClosedByInterruptException()
    }
}
