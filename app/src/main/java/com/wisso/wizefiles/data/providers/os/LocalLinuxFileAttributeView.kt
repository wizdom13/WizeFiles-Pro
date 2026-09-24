// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os

import java.nio.file.attribute.FileTime
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.PosixFileAttributeView
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser
import com.wisso.wizefiles.provider.common.toInt
import com.wisso.wizefiles.provider.os.syscall.LinuxSyscallConstants
import com.wisso.wizefiles.provider.os.syscall.StructTimespec
import com.wisso.wizefiles.provider.os.syscall.Syscall
import com.wisso.wizefiles.provider.os.syscall.SyscallException
import java.io.IOException

internal class LocalLinuxFileAttributeView(
    private val path: ByteString,
    private val noFollowLinks: Boolean
) : PosixFileAttributeView {
    override fun name(): String = NAME

    @Throws(IOException::class)
    override fun readAttributes(): LinuxFileAttributes {
        val stat = try {
            if (noFollowLinks) {
                Syscall.lstat(path)
            } else {
                Syscall.stat(path)
            }
        } catch (e: SyscallException) {
            throw e.toFileSystemException(path.toString())
        }
        val owner = try {
            LinuxUserPrincipalLookupService.getUserById(stat.st_uid)
        } catch (e: SyscallException) {
            // It's okay to have a non-existent UID.
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e.toFileSystemException(path.toString()))
            PosixUser(stat.st_uid, null)
        }
        val group = try {
            LinuxUserPrincipalLookupService.getGroupById(stat.st_gid)
        } catch (e: SyscallException) {
            // It's okay to have a non-existent GID.
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e.toFileSystemException(path.toString()))
            PosixGroup(stat.st_gid, null)
        }
        val seLinuxContext = readSeLinuxContextBestEffort()
        return LinuxFileAttributes.from(stat, owner, group, seLinuxContext)
    }

    private fun readSeLinuxContextBestEffort(): ByteString =
        try {
            if (noFollowLinks) {
                Syscall.lgetfilecon(path)
            } else {
                Syscall.getfilecon(path)
            }
        } catch (exception: SyscallException) {
            logSeLinuxUnavailableOnce()
            ByteString.EMPTY
        } catch (exception: RuntimeException) {
            logSeLinuxUnavailableOnce()
            ByteString.EMPTY
        }

    private fun logSeLinuxUnavailableOnce() {
        if (hasLoggedSeLinuxUnavailable.compareAndSet(false, true)) {
            com.wisso.wizefiles.util.AppLog.w(
                "FileAttributes",
                "SELinux file contexts are unavailable; continuing without optional labels"
            )
        }
    }

    @Throws(IOException::class)
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
        val times = arrayOf(lastAccessTime.toTimespec(), lastModifiedTime.toTimespec())
        try {
            if (noFollowLinks) {
                Syscall.lutimens(path, times)
            } else {
                Syscall.utimens(path, times)
            }
        } catch (e: SyscallException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    private fun FileTime?.toTimespec(): StructTimespec {
        if (this == null) {
            return StructTimespec(0, LinuxSyscallConstants.UTIME_OMIT)
        }
        val instant = toInstant()
        return StructTimespec(instant.epochSecond, instant.nano.toLong())
    }

    @Throws(IOException::class)
    override fun setOwner(owner: PosixUser) {
        val uid = owner.id
        try {
            if (noFollowLinks) {
                Syscall.lchown(path, uid, -1)
            } else {
                Syscall.chown(path, uid, -1)
            }
        } catch (e: SyscallException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    @Throws(IOException::class)
    override fun setGroup(group: PosixGroup) {
        val gid = group.id
        try {
            if (noFollowLinks) {
                Syscall.lchown(path, -1, gid)
            } else {
                Syscall.chown(path, -1, gid)
            }
        } catch (e: SyscallException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    @Throws(IOException::class)
    override fun setMode(mode: Set<PosixFileModeBit>) {
        if (noFollowLinks) {
            throw UnsupportedOperationException("Cannot set mode for symbolic links")
        }
        val modeInt = mode.toInt()
        try {
            Syscall.chmod(path, modeInt)
        } catch (e: SyscallException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    @Throws(IOException::class)
    override fun setSeLinuxContext(context: ByteString) {
        try {
            if (noFollowLinks) {
                Syscall.lsetfilecon(path, context)
            } else {
                Syscall.setfilecon(path, context)
            }
        } catch (e: SyscallException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    @Throws(IOException::class)
    override fun restoreSeLinuxContext() {
        val path = if (noFollowLinks) {
            path
        } else {
            try {
                Syscall.realpath(path)
            } catch (e: SyscallException) {
                throw e.toFileSystemException(path.toString())
            }
        }
        try {
            Syscall.selinux_android_restorecon(path, 0)
        } catch (e: SyscallException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    companion object {
        private val NAME = LinuxFileSystemProvider.scheme
        private val hasLoggedSeLinuxUnavailable = java.util.concurrent.atomic.AtomicBoolean()

        val SUPPORTED_NAMES = setOf("basic", "posix", NAME)
    }
}
