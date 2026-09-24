// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp

import java.nio.file.LinkOption
import java.nio.file.attribute.FileTime
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.PosixFileAttributeView
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser
import com.wisso.wizefiles.provider.common.toInt
import com.wisso.wizefiles.provider.sftp.client.SftpClient
import com.wisso.wizefiles.provider.sftp.client.SftpClientException
import net.schmizz.sshj.sftp.FileAttributes
import java.io.IOException

internal class SftpFileAttributeView(
    private val path: SftpPath,
    private val noFollowLinks: Boolean
) : PosixFileAttributeView {
    override fun name(): String = NAME

    @Throws(IOException::class)
    override fun readAttributes(): SftpFileAttributes {
        val attributes = getAttributes()
        return SftpFileAttributes.from(attributes, path)
    }

    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        if (lastAccessTime == null && lastModifiedTime == null) {
            // Only throw if caller is trying to set only create time, so that foreign copy move can
            // still set other times.
            if (createTime != null) {
                throw UnsupportedOperationException("createTime")
            }
            return
        }
        if (noFollowLinks) {
            throw UnsupportedOperationException(LinkOption.NOFOLLOW_LINKS.toString())
        }
        val currentAttributes = if (lastAccessTime == null || lastModifiedTime == null) {
            getAttributes().also {
                if (!it.has(FileAttributes.Flag.ACMODTIME)) {
                    throw UnsupportedOperationException("Missing SSH_FILEXFER_ACMODTIME")
                }
            }
        } else {
            null
        }
        val attributes = FileAttributes.Builder()
            .withAtimeMtime(
                lastAccessTime?.toInstant()?.epochSecond ?: currentAttributes!!.atime,
                lastModifiedTime?.toInstant()?.epochSecond ?: currentAttributes!!.mtime
            )
            .build()
        try {
            SftpClient.setstat(path, attributes)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    @Throws(IOException::class)
    override fun setOwner(owner: PosixUser) {
        if (noFollowLinks) {
            throw UnsupportedOperationException(LinkOption.NOFOLLOW_LINKS.toString())
        }
        val currentAttributes = getAttributes()
        if (!currentAttributes.has(FileAttributes.Flag.UIDGID)) {
            throw UnsupportedOperationException("Missing SSH_FILEXFER_ATTR_UIDGID")
        }
        val attributes = FileAttributes.Builder()
            .withUIDGID(owner.id, currentAttributes.gid)
            .build()
        try {
            SftpClient.setstat(path, attributes)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    @Throws(IOException::class)
    override fun setGroup(group: PosixGroup) {
        if (noFollowLinks) {
            throw UnsupportedOperationException(LinkOption.NOFOLLOW_LINKS.toString())
        }
        val currentAttributes = getAttributes()
        if (!currentAttributes.has(FileAttributes.Flag.UIDGID)) {
            throw UnsupportedOperationException("Missing SSH_FILEXFER_ATTR_UIDGID")
        }
        val attributes = FileAttributes.Builder()
            .withUIDGID(currentAttributes.uid, group.id)
            .build()
        try {
            SftpClient.setstat(path, attributes)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    @Throws(IOException::class)
    override fun setMode(mode: Set<PosixFileModeBit>) {
        if (noFollowLinks) {
            throw UnsupportedOperationException("Cannot set mode for symbolic links")
        }
        val attributes = FileAttributes.Builder()
            .withPermissions(mode.toInt())
            .build()
        try {
            SftpClient.setstat(path, attributes)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    @Throws(IOException::class)
    private fun getAttributes(): FileAttributes =
        try {
            if (noFollowLinks) SftpClient.lstat(path) else SftpClient.stat(path)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(path.toString())
        }

    @Throws(IOException::class)
    override fun setSeLinuxContext(context: ByteString) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun restoreSeLinuxContext() {
        throw UnsupportedOperationException()
    }

    companion object {
        private val NAME = SftpFileSystemProvider.scheme

        val SUPPORTED_NAMES = setOf("basic", "posix", NAME)
    }
}
