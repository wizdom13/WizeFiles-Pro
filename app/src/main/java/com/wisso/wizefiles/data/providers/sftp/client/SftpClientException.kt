package com.wisso.wizefiles.provider.sftp.client

import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.FileSystemLoopException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import com.wisso.wizefiles.provider.common.InvalidFileNameException
import com.wisso.wizefiles.provider.common.IsDirectoryException
import com.wisso.wizefiles.provider.common.ReadOnlyFileSystemException
import com.wisso.wizefiles.storage.ProviderFailureMapper
import com.wisso.wizefiles.storage.ProviderFailureSignal
import com.wisso.wizefiles.storage.ProviderFailureSignalSource
import com.wisso.wizefiles.util.AppLog
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.userauth.UserAuthException

class SftpClientException : Exception, ProviderFailureSignalSource {
    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    private val statusCode: Response.StatusCode? = causes()
        .filterIsInstance<SFTPException>()
        .firstOrNull()
        ?.statusCode

    override val providerFailureSignal: ProviderFailureSignal
        get() = when (statusCode) {
            Response.StatusCode.NO_SUCH_FILE, Response.StatusCode.NO_SUCH_PATH,
            Response.StatusCode.DELETE_PENDING -> ProviderFailureSignal.STALE_RESOURCE
            Response.StatusCode.PERMISSION_DENIED, Response.StatusCode.CANNOT_DELETE ->
                ProviderFailureSignal.PERMISSION_REVOKED
            Response.StatusCode.FILE_ALREADY_EXISTS -> ProviderFailureSignal.CONFLICT
            Response.StatusCode.WRITE_PROTECT -> ProviderFailureSignal.READ_ONLY
            Response.StatusCode.NO_SPACE_ON_FILESYSTEM, Response.StatusCode.QUOTA_EXCEEDED ->
                ProviderFailureSignal.DISK_FULL
            Response.StatusCode.NO_CONNECTION, Response.StatusCode.CONNECITON_LOST ->
                ProviderFailureSignal.UNAVAILABLE
            Response.StatusCode.BAD_MESSAGE -> ProviderFailureSignal.MALFORMED_RESPONSE
            else -> when {
                causes().any { it is java.io.InterruptedIOException && it !is java.net.SocketTimeoutException } ->
                    ProviderFailureSignal.INTERRUPTED
                causes().any { it is java.net.SocketTimeoutException } -> ProviderFailureSignal.TIMEOUT
                causes().any { it is UserAuthException } ->
                    ProviderFailureSignal.PERMISSION_REVOKED
                causes().any {
                    it is java.net.ConnectException || it is java.net.NoRouteToHostException ||
                        it is java.net.UnknownHostException || it is java.net.SocketException
                } -> ProviderFailureSignal.UNAVAILABLE
                causes().any {
                    it.message?.contains("no space", ignoreCase = true) == true ||
                        it.message?.contains("disk full", ignoreCase = true) == true
                } -> ProviderFailureSignal.DISK_FULL
                else -> ProviderFailureSignal.PERMANENT
            }
        }

    fun toFileSystemException(file: String?, other: String? = null): FileSystemException {
        val safeMessage = message?.let(AppLog::sanitize)?.take(ProviderFailureMapper.MAX_MESSAGE_LENGTH)
        return when (statusCode) {
            Response.StatusCode.NO_SUCH_FILE, Response.StatusCode.NO_SUCH_PATH,
            Response.StatusCode.DELETE_PENDING -> NoSuchFileException(file, other, safeMessage)
            Response.StatusCode.PERMISSION_DENIED, Response.StatusCode.CANNOT_DELETE ->
                AccessDeniedException(file, other, safeMessage)
            Response.StatusCode.FILE_ALREADY_EXISTS ->
                FileAlreadyExistsException(file, other, safeMessage)
            Response.StatusCode.WRITE_PROTECT -> ReadOnlyFileSystemException(file, other, safeMessage)
            Response.StatusCode.DIR_NOT_EMPTY -> DirectoryNotEmptyException(file)
            Response.StatusCode.NOT_A_DIRECTORY -> NotDirectoryException(file)
            Response.StatusCode.INVALID_FILENAME -> InvalidFileNameException(file, other, safeMessage)
            Response.StatusCode.LINK_LOOP -> FileSystemLoopException(file)
            Response.StatusCode.FILE_IS_A_DIRECTORY -> IsDirectoryException(file, other, safeMessage)
            else -> FileSystemException(file, other, safeMessage)
        }.apply { initCause(this@SftpClientException) }
    }

    private fun causes(): Sequence<Throwable> = sequence {
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = this@SftpClientException.cause
        repeat(MAX_CAUSE_DEPTH) {
            if (current == null || !seen.add(current)) return@sequence
            yield(current!!)
            current = current!!.cause
        }
    }

    private companion object { const val MAX_CAUSE_DEPTH = 8 }
}
