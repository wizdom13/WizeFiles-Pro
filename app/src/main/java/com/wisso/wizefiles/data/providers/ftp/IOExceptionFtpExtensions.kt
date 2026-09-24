// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp

import java.nio.file.FileSystemException
import com.wisso.wizefiles.provider.ftp.client.FtpClient
import com.wisso.wizefiles.provider.ftp.client.NegativeReplyCodeException
import java.io.IOException

fun IOException.toFileSystemExceptionForFtp(
    file: String?,
    other: String? = null
): FileSystemException =
    when (this) {
        is NegativeReplyCodeException -> toFileSystemException(file, other)
        else ->
            FileSystemException(file, other, toFtpConnectionReason())
                .apply { initCause(this@toFileSystemExceptionForFtp) }
    }

private fun IOException.toFtpConnectionReason(): String? {
    FtpClient.ftpConnectionLostReasonOrNull(this)?.let { return it }
    FtpClient.ftpDataConnectionFailureReasonOrNull(this)?.let { return it }
    return message
}
