package com.wisso.wizefiles.provider.common

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.io.IOException

internal object ForeignCopyMove {
    @Throws(IOException::class)
    fun copy(source: Path, target: Path, vararg options: CopyOption) {
        val copyOptions = options.toCopyOptions()
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val linkOptions = if (copyOptions.noFollowLinks) {
            arrayOf(LinkOption.NOFOLLOW_LINKS)
        } else {
            emptyArray()
        }
        val sourceAttributes = source.readAttributes(BasicFileAttributes::class.java, *linkOptions)
        if (!(sourceAttributes.isRegularFile || sourceAttributes.isDirectory
                || sourceAttributes.isSymbolicLink)) {
            throw IOException("Cannot copy special file to foreign provider")
        }
        if (
            !copyOptions.replaceExisting &&
            target.existsInCommittedStorage(LinkOption.NOFOLLOW_LINKS)
        ) {
            throw FileAlreadyExistsException(source.toString(), target.toString(), null)
        }
        when {
            sourceAttributes.isRegularFile -> {
                if (copyOptions.replaceExisting) {
                    target.deleteIfExists()
                }
                val openOptions = if (copyOptions.noFollowLinks) {
                    arrayOf(LinkOption.NOFOLLOW_LINKS)
                } else {
                    emptyArray()
                }
                source.newInputStream(*openOptions).use { inputStream ->
                    val outputStream = target.newOutputStream(
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
                    )
                    var successful = false
                    try {
                        inputStream.copyTo(
                            outputStream, copyOptions.progressIntervalMillis,
                            copyOptions.progressListener
                        )
                        successful = true
                    } finally {
                        try {
                            outputStream.close()
                        } finally {
                            if (!successful) {
                                try {
                                    target.deleteIfExists()
                                } catch (e: IOException) {
                                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                                } catch (e: UnsupportedOperationException) {
                                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                                }
                            }
                        }
                    }
                }
            }
            sourceAttributes.isDirectory -> {
                if (copyOptions.replaceExisting) {
                    target.deleteIfExists()
                }
                target.createDirectory()
                copyOptions.progressListener?.invoke(sourceAttributes.size())
            }
            sourceAttributes.isSymbolicLink -> {
                val sourceTarget = source.readSymbolicLink()
                try {
                    // Might throw UnsupportedOperationException, so we cannot delete beforehand.
                    target.createSymbolicLink(sourceTarget)
                } catch (e: FileAlreadyExistsException) {
                    if (!copyOptions.replaceExisting) {
                        throw e
                    }
                    target.deleteIfExists()
                    target.createSymbolicLink(sourceTarget)
                }
                copyOptions.progressListener?.invoke(sourceAttributes.size())
            }
            else -> throw AssertionError()
        }
        // We don't take error when copying attribute fatal, so errors will only be logged from
        // now on.
        val targetAttributeView =
            target.getFileAttributeView(BasicFileAttributeView::class.java) ?: return
        val lastModifiedTime = sourceAttributes.lastModifiedTime()
            .takeIf { it != FileTime::class.EPOCH }
        val lastAccessTime = if (copyOptions.copyAttributes) {
            sourceAttributes.lastAccessTime().takeIf { it != FileTime::class.EPOCH }
        } else {
            null
        }
        val creationTime = if (copyOptions.copyAttributes) {
            sourceAttributes.creationTime().takeIf { it != FileTime::class.EPOCH }
        } else {
            null
        }
        try {
            targetAttributeView.setTimes(lastModifiedTime, lastAccessTime, creationTime)
        } catch (e: IOException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        } catch (e: UnsupportedOperationException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        }
    }

    @Throws(IOException::class)
    fun move(source: Path, target: Path, vararg options: CopyOption) {
        val copyOptions = options.toCopyOptions()
        if (copyOptions.atomicMove) {
            throw AtomicMoveNotSupportedException(
                source.toString(), target.toString(),
                "Cannot move file atomically to foreign provider"
            )
        }
        val optionsForCopy = if (copyOptions.copyAttributes && copyOptions.noFollowLinks) {
            options
        } else {
            CopyOptions(
                copyOptions.replaceExisting, true, false, true, copyOptions.progressIntervalMillis,
                copyOptions.progressListener
            ).toArray()
        }
        val sourceAttributes = source.readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (sourceAttributes.isDirectory && !sourceAttributes.isSymbolicLink) {
            moveDirectoryRecursively(source, target, optionsForCopy)
            return
        }
        copy(source, target, *optionsForCopy)
        deleteSourceAfterForeignMove(source, target)
    }

    @Throws(IOException::class)
    private fun moveDirectoryRecursively(source: Path, target: Path, optionsForCopy: Array<out CopyOption>) {
        copy(source, target, *optionsForCopy)
        try {
            source.newDirectoryStream().use { directoryStream ->
                directoryStream.forEach { child ->
                    move(child, target.resolve(child.fileName.toString()), *optionsForCopy)
                }
            }
            deleteSourceAfterForeignMove(source, target)
        } catch (e: IOException) {
            deleteTargetAfterFailedForeignMove(target, e)
            throw e
        } catch (e: UnsupportedOperationException) {
            deleteTargetAfterFailedForeignMove(target, e)
            throw e
        }
    }

    private fun deleteSourceAfterForeignMove(source: Path, target: Path) {
        try {
            source.delete()
        } catch (e: IOException) {
            if (e !is NoSuchFileException) {
                deleteTargetAfterFailedForeignMove(target, e)
            }
            throw e
        } catch (e: UnsupportedOperationException) {
            deleteTargetAfterFailedForeignMove(target, e)
            throw e
        }
    }

    private fun deleteTargetAfterFailedForeignMove(target: Path, cause: Throwable) {
        try {
            deleteTreeIfExists(target)
        } catch (e: IOException) {
            cause.addSuppressed(e)
        } catch (e: UnsupportedOperationException) {
            cause.addSuppressed(e)
        }
    }

    @Throws(IOException::class)
    private fun deleteTreeIfExists(path: Path) {
        if (
            !path.existsInCommittedStorage(LinkOption.NOFOLLOW_LINKS)
        ) {
            return
        }
        val attributes = path.readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (attributes.isDirectory && !attributes.isSymbolicLink) {
            path.newDirectoryStream().use { directoryStream ->
                directoryStream.forEach { child -> deleteTreeIfExists(child) }
            }
        }
        path.deleteIfExists()
    }
}
