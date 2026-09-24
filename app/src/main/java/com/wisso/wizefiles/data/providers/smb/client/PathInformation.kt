package com.wisso.wizefiles.provider.smb.client

import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.fileinformation.FileAllInformation
import com.hierynomus.msfscc.fileinformation.FileIdFullDirectoryInformation
import com.hierynomus.msfscc.fileinformation.ShareInfo
import com.hierynomus.smbj.common.SMBRuntimeException

sealed interface PathInformation

data class FileInformation(
    val creationTime: FileTime,
    val lastAccessTime: FileTime,
    val lastWriteTime: FileTime,
    val changeTime: FileTime,
    val endOfFile: Long,
    val fileAttributes: Long,
    val fileId: Long
) : PathInformation

@Throws(SMBRuntimeException::class)
fun FileIdFullDirectoryInformation.toFileInformation(): FileInformation =
    FileInformation(
        creationTime = creationTime,
        lastAccessTime = lastAccessTime,
        lastWriteTime = lastWriteTime,
        changeTime = changeTime,
        endOfFile = endOfFile,
        fileAttributes = fileAttributes,
        fileId = fileId
    )

fun FileAllInformation.toFileInformation(): FileInformation {
    val basic = basicInformation
    return FileInformation(
        creationTime = basic.creationTime,
        lastAccessTime = basic.lastAccessTime,
        lastWriteTime = basic.lastWriteTime,
        changeTime = basic.changeTime,
        endOfFile = standardInformation.endOfFile,
        fileAttributes = basic.fileAttributes,
        fileId = internalInformation.indexNumber
    )
}

data class ShareInformation(
    val type: ShareType,
    val shareInfo: ShareInfo?
) : PathInformation

enum class ShareType {
    DISK,
    PIPE,
    PRINTER
}
