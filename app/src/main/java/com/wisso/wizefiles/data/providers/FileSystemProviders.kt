// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider

import com.wisso.wizefiles.provider.archive.ArchiveFileSystemProvider
import com.wisso.wizefiles.provider.common.AndroidFileTypeDetector
import com.wisso.wizefiles.provider.content.ContentFileSystemProvider
import com.wisso.wizefiles.provider.document.DocumentFileSystemProvider
import com.wisso.wizefiles.provider.ftp.FtpFileSystemProvider
import com.wisso.wizefiles.provider.ftp.FtpesFileSystemProvider
import com.wisso.wizefiles.provider.ftp.FtpsFileSystemProvider
import com.wisso.wizefiles.provider.legacy.getInstalledFileSystemProvider
import com.wisso.wizefiles.provider.legacy.installDefaultFileSystemProvider
import com.wisso.wizefiles.provider.legacy.installFileSystemProvider
import com.wisso.wizefiles.provider.legacy.installFileTypeDetector
import com.wisso.wizefiles.provider.os.LinuxFileSystemProvider
import com.wisso.wizefiles.provider.root.isRunningAsRoot
import com.wisso.wizefiles.provider.rclone.RcloneFileSystemProvider
import com.wisso.wizefiles.provider.sftp.SftpFileSystemProvider
import com.wisso.wizefiles.provider.smb.SmbFileSystemProvider
import java.nio.file.spi.FileSystemProvider

object FileSystemProviders {
    @Volatile
    var overflowWatchEvents = false

    fun install() {
        installDefaultFileSystemProvider(LinuxFileSystemProvider)
        installFileSystemProvider(ArchiveFileSystemProvider)
        if (!isRunningAsRoot) {
            installFileSystemProvider(ContentFileSystemProvider)
            installFileSystemProvider(DocumentFileSystemProvider)
            installFileSystemProvider(FtpFileSystemProvider)
            installFileSystemProvider(FtpsFileSystemProvider)
            installFileSystemProvider(FtpesFileSystemProvider)
            installFileSystemProvider(SftpFileSystemProvider)
            installFileSystemProvider(SmbFileSystemProvider)
            installFileSystemProvider(RcloneFileSystemProvider)
        }
        installFileTypeDetector(AndroidFileTypeDetector)
    }

    operator fun get(scheme: String): FileSystemProvider =
        getInstalledFileSystemProvider(scheme) as FileSystemProvider
}
