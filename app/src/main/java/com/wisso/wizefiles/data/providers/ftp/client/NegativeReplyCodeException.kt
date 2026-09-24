// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp.client

import com.wisso.wizefiles.provider.common.InvalidFileNameException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply

class NegativeReplyCodeException(
    internal val replyCode: Int,
    replyText: String
) : IOException(replyText) {
    fun toFileSystemException(file: String?, other: String? = null): FileSystemException {
        val converted = when (replyCode) {
            FTPReply.NOT_LOGGED_IN,
            FTPReply.NEED_ACCOUNT_FOR_STORING_FILES ->
                AccessDeniedException(file, other, message)
            FTPReply.FILE_UNAVAILABLE ->
                NoSuchFileException(file, other, message)
            FTPReply.FILE_NAME_NOT_ALLOWED ->
                InvalidFileNameException(file, other, message)
            else ->
                FileSystemException(file, other, message)
        }
        converted.initCause(this)
        return converted
    }
}

internal fun FTPClient.createNegativeReplyCodeException(): NegativeReplyCodeException =
    NegativeReplyCodeException(replyCode, replyString)

internal fun FTPClient.throwNegativeReplyCodeException(): Nothing =
    throw createNegativeReplyCodeException()
