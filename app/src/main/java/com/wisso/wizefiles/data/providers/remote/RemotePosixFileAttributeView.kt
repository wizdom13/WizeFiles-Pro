package com.wisso.wizefiles.provider.remote

import java.nio.file.attribute.FileTime
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.PosixFileAttributeView
import com.wisso.wizefiles.provider.common.PosixFileAttributes
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser
import com.wisso.wizefiles.provider.common.toParcelable
import java.io.IOException

abstract class RemotePosixFileAttributeView(
    private val remoteInterface: BinderEndpoint<IPosixAttributesBridge>
) : PosixFileAttributeView {
    @Throws(IOException::class)
    override fun readAttributes(): PosixFileAttributes =
        remoteInterface.requireService().invokeBridge { exception -> loadPosixAttributes(exception) }.value()

    @Throws(IOException::class)
    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        remoteInterface.requireService().invokeBridge { exception ->
            updateTimes(
                lastModifiedTime?.toParcelable(), lastAccessTime?.toParcelable(),
                createTime?.toParcelable(), exception
            )
        }
    }

    @Throws(IOException::class)
    override fun setOwner(owner: PosixUser) {
        remoteInterface.requireService().invokeBridge { exception -> updateOwner(owner, exception) }
    }

    @Throws(IOException::class)
    override fun setGroup(group: PosixGroup) {
        remoteInterface.requireService().invokeBridge { exception -> updateGroup(group, exception) }
    }

    @Throws(IOException::class)
    override fun setMode(mode: Set<PosixFileModeBit>) {
        remoteInterface.requireService().invokeBridge { exception -> updateMode(mode.toParcelable(), exception) }
    }

    @Throws(IOException::class)
    override fun setSeLinuxContext(context: ByteString) {
        remoteInterface.requireService().invokeBridge { exception ->
            updateSeLinuxContext(context.toParcelable(), exception)
        }
    }

    @Throws(IOException::class)
    override fun restoreSeLinuxContext() {
        remoteInterface.requireService().invokeBridge { exception -> resetSeLinuxContext(exception) }
    }
}
