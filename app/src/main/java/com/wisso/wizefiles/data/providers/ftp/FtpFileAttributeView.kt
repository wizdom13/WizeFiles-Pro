// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp

import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import com.wisso.wizefiles.provider.ftp.client.FtpClient
import java.io.IOException

internal class FtpFileAttributeView(
    private val path: FtpPath,
    private val noFollowLinks: Boolean
) : BasicFileAttributeView {
    override fun name(): String = NAME

    @Throws(IOException::class)
    override fun readAttributes(): FtpFileAttributes {
        val file = try {
            FtpClient.listFile(path, noFollowLinks)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(path.toString())
        }
        return FtpFileAttributes.from(file, path)
    }

    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        if (lastModifiedTime == null) {
            // Only throw if caller is trying to set only last access time and/or create time, so
            // that foreign copy move can still set last modified time.
            if (lastAccessTime != null) {
                throw UnsupportedOperationException("lastAccessTime")
            }
            if (createTime != null) {
                throw UnsupportedOperationException("createTime")
            }
            return
        }
        if (noFollowLinks) {
            throw UnsupportedOperationException(LinkOption.NOFOLLOW_LINKS.toString())
        }
        try {
            FtpClient.setLastModifiedTime(path, lastModifiedTime.toInstant())
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(path.toString())
        }
    }

    companion object {
        private val NAME = FtpFileSystemProvider.scheme

        val SUPPORTED_NAMES = setOf("basic", NAME)
    }
}
