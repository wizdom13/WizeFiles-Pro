package com.wisso.wizefiles.provider.remote

import com.wisso.wizefiles.provider.FileSystemProviders

open class RemoteFileServiceInterface : IFileServiceBridge.Stub() {
    override fun providerFor(scheme: String): IFileProviderBridge =
        RemoteFileSystemProviderInterface(FileSystemProviders[scheme])

    override fun connectFileSystem(fileSystem: BridgeObject): IFileSystemBridge =
        RemoteFileSystemInterface(fileSystem.value())

    override fun connectPosixStore(
        fileStore: BridgeObject
    ): IPosixStoreBridge = RemotePosixFileStoreInterface(fileStore.value())

    override fun connectPosixAttributes(
        attributeView: BridgeObject
    ): IPosixAttributesBridge =
        RemotePosixFileAttributeViewInterface(attributeView.value())
}
