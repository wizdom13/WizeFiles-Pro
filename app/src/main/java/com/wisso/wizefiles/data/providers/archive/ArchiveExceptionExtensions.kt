package com.wisso.wizefiles.provider.archive

import android.system.OsConstants
import com.wisso.wizefiles.provider.archive.legacy.archivePathString
import com.wisso.wizefiles.provider.archive.legacy.requireLegacyArchivePath
import com.wisso.wizefiles.provider.archive.legacy.toArchiveAppPath
import com.wisso.wizefiles.provider.common.ForwardingInputStream
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.libarchive.ArchiveException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.FileSystemException
import java.nio.file.Path

private const val ARCHIVE_ERRNO_MISC = -1

fun ArchiveException.toFileSystemOrInterruptedIOException(file: AppPath): IOException {
    val archiveMessage = message.orEmpty()
    return when {
        code == OsConstants.EINTR -> InterruptedIOException(archiveMessage)
        code == ARCHIVE_ERRNO_MISC && archiveMessage.isPasswordError() ->
            ArchivePasswordRequiredException(file.requireLegacyArchivePath(), archiveMessage)
        else -> FileSystemException(
            file.archivePathString(),
            null,
            archiveMessage.toUserFriendlyArchiveMessage()
        )
    }.apply { initCause(this@toFileSystemOrInterruptedIOException) }
}

fun ArchiveException.toFileSystemOrInterruptedIOException(file: Path): IOException =
    toFileSystemOrInterruptedIOException(file.toArchiveAppPath())

internal fun String.isPasswordError(): Boolean =
    this == "Incorrect passphrase" || this == "Passphrase required for this entry"

internal fun String.toUserFriendlyArchiveMessage(): String {
    val lowerCaseMessage = lowercase()
    return when {
        "passphrase" in lowerCaseMessage || "password" in lowerCaseMessage ->
            "Wrong password or archive password is required"
        "unsupported" in lowerCaseMessage || "not supported" in lowerCaseMessage ->
            "Unsupported archive feature or compression method"
        "corrupt" in lowerCaseMessage || "damaged" in lowerCaseMessage ||
            "truncated" in lowerCaseMessage || "checksum" in lowerCaseMessage ->
            "Archive appears to be corrupt or incomplete"
        "volume" in lowerCaseMessage || "multi-volume" in lowerCaseMessage ||
            "next file" in lowerCaseMessage || "cannot open file" in lowerCaseMessage ->
            "A required archive part is missing (multipart archive)"
        else -> this
    }
}

class ArchiveExceptionInputStream(
    inputStream: InputStream,
    private val file: AppPath
) : ForwardingInputStream(inputStream) {
    constructor(inputStream: InputStream, file: Path) : this(inputStream, file.toArchiveAppPath())

    @Throws(IOException::class)
    override fun read(): Int =
        try {
            super.read()
        } catch (e: ArchiveException) {
            throw e.toFileSystemOrInterruptedIOException(file)
        }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int =
        try {
            super.read(b)
        } catch (e: ArchiveException) {
            throw e.toFileSystemOrInterruptedIOException(file)
        }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int =
        try {
            super.read(b, off, len)
        } catch (e: ArchiveException) {
            throw e.toFileSystemOrInterruptedIOException(file)
        }

    @Throws(IOException::class)
    override fun skip(n: Long): Long = try {
        super.skip(n)
    } catch (e: ArchiveException) {
        throw e.toFileSystemOrInterruptedIOException(file)
    }

    @Throws(IOException::class)
    override fun available(): Int =
        try {
            super.available()
        } catch (e: ArchiveException) {
            throw e.toFileSystemOrInterruptedIOException(file)
        }

    @Throws(IOException::class)
    override fun close() {
        try {
            super.close()
        } catch (e: ArchiveException) {
            throw e.toFileSystemOrInterruptedIOException(file)
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        try {
            super.reset()
        } catch (e: ArchiveException) {
            throw e.toFileSystemOrInterruptedIOException(file)
        }
    }
}
