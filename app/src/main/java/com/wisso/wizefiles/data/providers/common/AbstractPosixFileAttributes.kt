package com.wisso.wizefiles.provider.common

import android.os.Parcelable
import java.nio.file.attribute.FileTime

abstract class AbstractPosixFileAttributes : PosixFileAttributes, Parcelable {
    protected abstract val lastModifiedTime: FileTime
    protected abstract val lastAccessTime: FileTime
    protected abstract val creationTime: FileTime
    protected abstract val type: PosixFileType
    protected abstract val size: Long
    protected abstract val fileKey: Parcelable
    protected abstract val owner: PosixUser?
    protected abstract val group: PosixGroup?
    protected abstract val mode: Set<PosixFileModeBit>?
    protected abstract val seLinuxContext: ByteString?

    final override fun type(): PosixFileType = type
    final override fun size(): Long = size
    final override fun fileKey(): Parcelable = fileKey

    final override fun creationTime(): FileTime = creationTime
    final override fun lastAccessTime(): FileTime = lastAccessTime
    final override fun lastModifiedTime(): FileTime = lastModifiedTime

    final override fun owner(): PosixUser? = owner
    final override fun group(): PosixGroup? = group
    final override fun mode(): Set<PosixFileModeBit>? = mode
    final override fun seLinuxContext(): ByteString? = seLinuxContext
}
