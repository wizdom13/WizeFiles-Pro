package com.wisso.wizefiles.provider.remote

import java.nio.file.FileSystem

class RemoteFileSystemInterface(private val fileSystem: FileSystem) : IFileSystemBridge.Stub() {
    override fun closeFileSystem(exception: BridgeFailure) {
        serveBridge(exception) { fileSystem.close() }
    }
}
