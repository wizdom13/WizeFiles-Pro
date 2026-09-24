// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp

import com.wisso.wizefiles.provider.ftp.client.FtpClient
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import org.apache.commons.net.ftp.FTPFile

/** Concrete FTP protocol seam used by mutation code and deterministic fault tests. */
internal interface FtpOperations {
    fun listFile(path: FtpPath, noFollowLinks: Boolean): FTPFile
    fun listFileOrNull(path: FtpPath, noFollowLinks: Boolean): FTPFile?
    fun delete(path: FtpPath, isDirectory: Boolean)
    fun createDirectory(path: FtpPath)
    fun retrieveFile(path: FtpPath): InputStream
    fun storeFile(path: FtpPath): OutputStream
    fun renameFile(source: FtpPath, target: FtpPath)
    fun setLastModifiedTime(path: FtpPath, instant: Instant)

    companion object {
        val DEFAULT: FtpOperations = object : FtpOperations {
            override fun listFile(path: FtpPath, noFollowLinks: Boolean) =
                FtpClient.listFile(path, noFollowLinks)
            override fun listFileOrNull(path: FtpPath, noFollowLinks: Boolean) =
                FtpClient.listFileOrNull(path, noFollowLinks)
            override fun delete(path: FtpPath, isDirectory: Boolean) =
                FtpClient.delete(path, isDirectory)
            override fun createDirectory(path: FtpPath) = FtpClient.createDirectory(path)
            override fun retrieveFile(path: FtpPath) = FtpClient.retrieveFile(path)
            override fun storeFile(path: FtpPath) = FtpClient.storeFile(path)
            override fun renameFile(source: FtpPath, target: FtpPath) =
                FtpClient.renameFile(source, target)
            override fun setLastModifiedTime(path: FtpPath, instant: Instant) =
                FtpClient.setLastModifiedTime(path, instant)
        }
    }
}
