// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import com.wisso.wizefiles.provider.archive.ArchiveFileSystemProvider
import com.wisso.wizefiles.provider.content.ContentFileSystemProvider
import com.wisso.wizefiles.provider.document.DocumentFileSystemProvider
import com.wisso.wizefiles.provider.ftp.FtpFileSystemProvider
import com.wisso.wizefiles.provider.rclone.RcloneFileSystemProvider
import com.wisso.wizefiles.provider.sftp.SftpFileSystemProvider
import com.wisso.wizefiles.provider.os.LinuxFileSystemProvider
import com.wisso.wizefiles.provider.smb.SmbFileSystemProvider

fun AppPath.toLegacyPathOrNull(): Path? = toLocalFileOrNull()?.let { localFile ->
    LinuxFileSystemProvider.fileSystem.getPath(localFile.path)
} ?: (this as? RawAppPath)?.rawPath
    ?.let { rawPath ->
        val uri = runCatching { URI.create(rawPath) }.getOrNull()
        if (uri?.scheme != null) {
            when (uri.scheme) {
                "archive" -> runCatching { ArchiveFileSystemProvider.getPath(uri) }.getOrNull()
                "document" -> runCatching { DocumentFileSystemProvider.getPath(uri) }.getOrNull()
                "content" -> runCatching { ContentFileSystemProvider.getPath(uri) }.getOrNull()
                "ftp", "ftps" -> runCatching { FtpFileSystemProvider.getPath(uri) }.getOrNull()
                "rclone" -> runCatching { RcloneFileSystemProvider.getPath(uri) }.getOrNull()
                "sftp" -> runCatching { SftpFileSystemProvider.getPath(uri) }.getOrNull()
                "smb" -> runCatching { SmbFileSystemProvider.getPath(uri) }.getOrNull()
                "smb2", "smb3" -> runCatching {
                    SmbFileSystemProvider.getPath(uri.toSmbUri())
                }.getOrNull()
                "file" -> runCatching { Paths.get(uri) }.getOrNull()
                else -> runCatching { Paths.get(uri) }.getOrNull()
            }
        } else {
            runCatching { Paths.get(rawPath) }.getOrNull()
        }
    }

private fun URI.toSmbUri(): URI = URI(
    "smb",
    userInfo,
    host,
    port,
    path,
    query,
    fragment
)
