// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb

import android.os.Parcelable
import java.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.wisso.wizefiles.provider.common.AbstractBasicFileAttributes
import com.wisso.wizefiles.provider.common.BasicFileType
import com.wisso.wizefiles.provider.common.EPOCH
import com.wisso.wizefiles.provider.common.FileTimeParceler
import com.wisso.wizefiles.provider.smb.client.ShareInformation
import com.wisso.wizefiles.provider.smb.client.ShareType

@Parcelize
internal class SmbShareFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val lastAccessTime: @WriteWith<FileTimeParceler> FileTime,
    override val creationTime: @WriteWith<FileTimeParceler> FileTime,
    override val type: BasicFileType,
    override val size: Long,
    override val fileKey: Parcelable,
    private val totalSpace: Long?,
    private val usableSpace: Long?,
    private val unallocatedSpace: Long?
) : AbstractBasicFileAttributes() {
    fun totalSpace(): Long? = totalSpace

    fun usableSpace(): Long? = usableSpace

    fun unallocatedSpace(): Long? = unallocatedSpace

    companion object {
        fun from(shareInformation: ShareInformation, path: SmbPath): SmbShareFileAttributes {
            val lastModifiedTime = FileTime::class.EPOCH
            val lastAccessTime = lastModifiedTime
            val creationTime = lastModifiedTime
            val type = when (shareInformation.type) {
                ShareType.DISK -> BasicFileType.DIRECTORY
                else -> BasicFileType.OTHER
            }
            val size = 0L
            val fileKey = path
            val shareInfo = shareInformation.shareInfo
            val totalSpace = shareInfo?.totalSpace
            val usableSpace = shareInfo?.callerFreeSpace
            val unallocatedSpace = shareInfo?.freeSpace
            return SmbShareFileAttributes(
                lastModifiedTime, lastAccessTime, creationTime, type, size, fileKey, totalSpace,
                usableSpace, unallocatedSpace
            )
        }
    }
}
