package com.wisso.wizefiles.provider.remote

import java.nio.file.FileSystem
import com.wisso.wizefiles.provider.common.PosixFileAttributeView
import com.wisso.wizefiles.provider.common.PosixFileStore

abstract class RemoteFileService(private val remoteInterface: BinderEndpoint<IFileServiceBridge>) {
    @Throws(BridgeUnavailableException::class)
    fun providerFor(scheme: String): IFileProviderBridge =
        remoteInterface.requireService().invokeBridge { providerFor(scheme) }

    @Throws(BridgeUnavailableException::class)
    fun connectFileSystem(fileSystem: FileSystem): IFileSystemBridge =
        remoteInterface.requireService().invokeBridge { connectFileSystem(fileSystem.toParcelable()) }

    @Throws(BridgeUnavailableException::class)
    fun connectPosixStore(fileStore: PosixFileStore): IPosixStoreBridge =
        remoteInterface.requireService().invokeBridge { connectPosixStore(fileStore.toParcelable()) }

    @Throws(BridgeUnavailableException::class)
    fun connectPosixAttributes(
        attributeView: PosixFileAttributeView
    ): IPosixAttributesBridge =
        remoteInterface.requireService().invokeBridge {
            connectPosixAttributes(attributeView.toParcelable())
        }
}
