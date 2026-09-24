package com.wisso.wizefiles.provider.remote

import java.nio.file.FileSystem
import java.io.IOException

abstract class RemoteFileSystem(
    private val remoteInterface: BinderEndpoint<IFileSystemBridge>
) : FileSystem() {
    @Throws(IOException::class)
    override fun close() {
        if (!remoteInterface.isConnected()) {
            return
        }
        remoteInterface.requireService().invokeBridge { exception -> closeFileSystem(exception) }
    }
}
