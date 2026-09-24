// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os

import android.os.Parcel
import android.os.Parcelable
import android.system.OsConstants
import android.system.StructStatVfs
import java.nio.file.attribute.FileAttributeView
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.ByteStringBuilder
import com.wisso.wizefiles.provider.common.FileStoreNotFoundException
import com.wisso.wizefiles.provider.common.PosixFileStore
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.provider.os.syscall.LinuxSyscallConstants
import com.wisso.wizefiles.provider.os.syscall.Int32Ref
import com.wisso.wizefiles.provider.os.syscall.StructMntent
import com.wisso.wizefiles.provider.os.syscall.Syscall
import com.wisso.wizefiles.provider.os.syscall.SyscallException
import com.wisso.wizefiles.util.andInv
import com.wisso.wizefiles.util.hasBits
import com.wisso.wizefiles.util.readParcelable
import java.io.IOException

internal class LocalLinuxFileStore : PosixFileStore, Parcelable {
    private val path: LinuxPath
    private lateinit var mntent: StructMntent

    @Throws(IOException::class)
    constructor(path: LinuxPath) {
        this.path = path
        refresh()
    }

    private constructor(fileSystem: LocalLinuxFileSystem, mntent: StructMntent) {
        path = fileSystem.getPath(mntent.mnt_dir)
        this.mntent = mntent
    }

    @Throws(IOException::class)
    override fun refresh() {
        this.mntent = try {
            findMountEntry(path)
        } catch (e: SyscallException) {
            throw e.toFileSystemException(path.toString())
        } ?: throw FileStoreNotFoundException(path.toString())
    }

    @Throws(SyscallException::class)
    private fun findMountEntry(path: LinuxPath): StructMntent? {
        val entries = mutableMapOf<LinuxPath, StructMntent>()
        // The last mount entry for the same path will win because we are putting them into a Map,
        // so no need to traverse in reverse order like other implementations.
        for (mntent in getMountEntries()) {
            val entryPath = path.fileSystem.getPath(mntent.mnt_dir)
            entries[entryPath] = mntent
        }
        var path = path
        while (true) {
            val mntent = entries[path]
            if (mntent != null) {
                return mntent
            }
            path = path.parent ?: break
        }
        return null
    }

    override fun name(): String = mntent.mnt_dir.toString()

    override fun type(): String = mntent.mnt_type.toString()

    override fun isReadOnly(): Boolean = Syscall.hasmntopt(mntent, OPTION_RO)

    @Throws(IOException::class)
    override fun setReadOnly(readOnly: Boolean) {
        // Fetch the latest mount entry before we remount.
        refresh()
        if (isReadOnly == readOnly) {
            return
        }
        var (flags, options) = getFlagsFromOptions(mntent.mnt_opts)
        flags = if (readOnly) {
            flags or LinuxSyscallConstants.MS_RDONLY
        } else {
            flags andInv LinuxSyscallConstants.MS_RDONLY
        }
        val data = options.cstr
        try {
            remount(mntent.mnt_fsname, mntent.mnt_dir, mntent.mnt_type, flags, data)
        } catch (e: SyscallException) {
            throw e.toFileSystemException(mntent.mnt_dir.toString())
        }
        refresh()
    }

    private fun getFlagsFromOptions(options: ByteString): Pair<Long, ByteString> {
        var flags = 0L
        val builder = ByteStringBuilder()
        for (option in options.split(OPTIONS_DELIMITER)) {
            val flag = OPTION_FLAG_MAP[option]
            if (flag != null) {
                flags = flags or flag
            } else {
                if (!builder.isEmpty) {
                    builder.append(OPTIONS_DELIMITER)
                }
                builder.append(option)
            }
        }
        return flags to builder.toByteString()
    }

    @Throws(SyscallException::class)
    private fun remount(
        source: ByteString?,
        target: ByteString,
        fileSystemType: ByteString?,
        mountFlags: Long,
        data: ByteArray?
    ) {
        val mountFlags = mountFlags or LinuxSyscallConstants.MS_REMOUNT
        try {
            Syscall.mount(source, target, fileSystemType, mountFlags, data)
        } catch (e: SyscallException) {
            val readOnly = mountFlags.hasBits(LinuxSyscallConstants.MS_RDONLY)
            val isReadOnlyError = e.errno == OsConstants.EACCES || e.errno == OsConstants.EROFS
            if (readOnly || !isReadOnlyError) {
                throw e
            }
            try {
                val fd = Syscall.open(source!!, OsConstants.O_RDONLY, 0)
                try {
                    Syscall.ioctl_int(fd, LinuxSyscallConstants.BLKROSET, Int32Ref(0))
                } finally {
                    Syscall.close(fd)
                }
                Syscall.mount(source, target, fileSystemType, mountFlags, data)
            } catch (e2: SyscallException) {
                e.addSuppressed(e2)
                throw e
            }
        }
    }

    @Throws(IOException::class)
    override fun getTotalSpace(): Long {
        val statVfs = getStatVfs()
        return statVfs.f_blocks * statVfs.f_bsize
    }

    @Throws(IOException::class)
    override fun getUsableSpace(): Long {
        val statVfs = getStatVfs()
        return statVfs.f_bavail * statVfs.f_bsize
    }

    @Throws(IOException::class)
    override fun getUnallocatedSpace(): Long {
        val statVfs = getStatVfs()
        return statVfs.f_bfree * statVfs.f_bsize
    }

    @Throws(IOException::class)
    private fun getStatVfs(): StructStatVfs =
        try {
            Syscall.statvfs(path.toByteString())
        } catch (e: SyscallException) {
            throw e.toFileSystemException(path.toString())
        }

    override fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        LinuxFileSystemProvider.supportsFileAttributeView(type)

    override fun supportsFileAttributeView(name: String): Boolean =
        name in LinuxFileAttributeView.SUPPORTED_NAMES

    private constructor(source: Parcel) {
        path = source.readParcelable()!!
        mntent = source.readParcelable()!!
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(path, flags)
        dest.writeParcelable(mntent, flags)
    }

    companion object {
        private val PATH_PROC_SELF_MOUNTS = "/proc/self/mounts".toByteString()

        private val MODE_R = "r".toByteString()

        private val OPTIONS_DELIMITER = ",".toByteString()
        private val OPTION_RO = "ro".toByteString()
        // @see https://android.googlesource.com/platform/system/core/+/master/fs_mgr/fs_mgr_fstab.cpp
        //      kMountFlagsList
        // @see https://github.com/mmalecki/util-linux/blob/master/mount-deprecated/mount.c opt_map
        // @see https://android.googlesource.com/platform/external/toybox/+/refs/heads/master/toys/lsb/mount.c
        //      flag_opts()
        // @see http://lists.landley.net/pipermail/toybox-landley.net/2012-August/000628.html
        private val OPTION_FLAG_MAP = mapOf(
            "defaults" to 0L,
            "ro" to LinuxSyscallConstants.MS_RDONLY,
            "rw" to 0L,
            "nosuid" to LinuxSyscallConstants.MS_NOSUID,
            "suid" to 0L,
            "nodev" to LinuxSyscallConstants.MS_NODEV,
            "dev" to 0L,
            "noexec" to LinuxSyscallConstants.MS_NOEXEC,
            "exec" to 0L,
            "sync" to LinuxSyscallConstants.MS_SYNCHRONOUS,
            "async" to 0L,
            "remount" to LinuxSyscallConstants.MS_REMOUNT,
            "mand" to LinuxSyscallConstants.MS_MANDLOCK,
            "nomand" to 0L,
            "dirsync" to LinuxSyscallConstants.MS_DIRSYNC,
            "noatime" to LinuxSyscallConstants.MS_NOATIME,
            "atime" to 0L,
            "nodiratime" to LinuxSyscallConstants.MS_NODIRATIME,
            "diratime" to 0L,
            "bind" to LinuxSyscallConstants.MS_BIND,
            "rbind" to (LinuxSyscallConstants.MS_BIND or LinuxSyscallConstants.MS_REC),
            "move" to LinuxSyscallConstants.MS_MOVE,
            "rec" to LinuxSyscallConstants.MS_REC,
            "verbose" to LinuxSyscallConstants.MS_VERBOSE,
            "silent" to LinuxSyscallConstants.MS_SILENT,
            "loud" to 0L,
            //"posixacl" to LinuxSyscallConstants.MS_POSIXACL,
            //"noposixacl" to 0L,
            "unbindable" to LinuxSyscallConstants.MS_UNBINDABLE,
            "runbindable" to (LinuxSyscallConstants.MS_UNBINDABLE or LinuxSyscallConstants.MS_REC),
            "private" to LinuxSyscallConstants.MS_PRIVATE,
            "rprivate" to (LinuxSyscallConstants.MS_PRIVATE or LinuxSyscallConstants.MS_REC),
            "slave" to LinuxSyscallConstants.MS_SLAVE,
            "rslave" to (LinuxSyscallConstants.MS_SLAVE or LinuxSyscallConstants.MS_REC),
            "shared" to LinuxSyscallConstants.MS_SHARED,
            "rshared" to (LinuxSyscallConstants.MS_SHARED or LinuxSyscallConstants.MS_REC),
            "relatime" to LinuxSyscallConstants.MS_RELATIME,
            "norelatime" to 0L,
            //"kernmount" to LinuxSyscallConstants.MS_KERNMOUNT,
            "iversion" to LinuxSyscallConstants.MS_I_VERSION,
            "noiversion" to 0L,
            "strictatime" to LinuxSyscallConstants.MS_STRICTATIME,
            "nostrictatime" to 0L,
            "lazytime" to LinuxSyscallConstants.MS_LAZYTIME,
            "nolazytime" to 0L,
            //"submount" to LinuxSyscallConstants.MS_SUBMOUNT,
            //"noremotelock" to LinuxSyscallConstants.MS_NOREMOTELOCK,
            //"remotelock" to 0L,
            //"nosec" to LinuxSyscallConstants.MS_NOSEC,
            //"sec" to 0L,
            //"born" to LinuxSyscallConstants.MS_BORN,
            //"active" to LinuxSyscallConstants.MS_ACTIVE,
            "nouser" to LinuxSyscallConstants.MS_NOUSER,
            "user" to 0L
        ).mapKeys { it.key.toByteString() }

        fun getFileStores(fileSystem: LocalLinuxFileSystem): List<LocalLinuxFileStore> {
            val entries = try {
                getMountEntries()
            } catch (e: SyscallException) {
                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                return emptyList()
            }
            return entries.map { LocalLinuxFileStore(fileSystem, it) }
        }

        @Throws(SyscallException::class)
        private fun getMountEntries(): List<StructMntent> {
            val entries = mutableListOf<StructMntent>()
            val file = Syscall.setmntent(PATH_PROC_SELF_MOUNTS, MODE_R)
            try {
                while (true) {
                    val mntent = Syscall.getmntent(file) ?: break
                    entries += mntent
                }
            } finally {
                Syscall.endmntent(file)
            }
            return entries
        }

        @JvmField
        val CREATOR = object : Parcelable.Creator<LocalLinuxFileStore> {
            override fun createFromParcel(source: Parcel): LocalLinuxFileStore =
                LocalLinuxFileStore(source)

            override fun newArray(size: Int): Array<LocalLinuxFileStore?> = arrayOfNulls(size)
        }
    }
}
