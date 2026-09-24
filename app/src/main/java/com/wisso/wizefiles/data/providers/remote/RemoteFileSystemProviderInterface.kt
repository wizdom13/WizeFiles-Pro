package com.wisso.wizefiles.provider.remote

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.spi.FileSystemProvider
import com.wisso.wizefiles.provider.common.PathObservableProvider
import com.wisso.wizefiles.provider.common.Searchable
import com.wisso.wizefiles.util.RemoteCallback
import com.wisso.wizefiles.util.toBundle
import java.util.concurrent.Executors

class RemoteFileSystemProviderInterface(
    private val provider: FileSystemProvider
) : IFileProviderBridge.Stub() {
    private val executorService = Executors.newCachedThreadPool()

    override fun openInput(
        file: BridgeObject,
        options: BridgeSerializable,
        exception: BridgeFailure
    ): BridgeInputStream? =
        serveBridge(exception) { provider.newInputStream(file.value(), *options.value()).toBridge() }

    override fun openChannel(
        file: BridgeObject,
        options: BridgeSerializable,
        attributes: BridgeFileAttributes,
        exception: BridgeFailure
    ): BridgeSeekableByteChannel? =
        serveBridge(exception) {
            provider.newByteChannel(file.value(), options.value(), *attributes.value).toBridge()
        }

    override fun listDirectory(
        directory: BridgeObject,
        filter: BridgeObject,
        exception: BridgeFailure
    ): BridgeDirectoryListing? =
        serveBridge(exception) {
            provider.newDirectoryStream(directory.value(), filter.value())
                .use { BridgeDirectoryListing(it) }
        }

    override fun makeDirectory(
        directory: BridgeObject,
        attributes: BridgeFileAttributes,
        exception: BridgeFailure
    ) {
        serveBridge(exception) { provider.createDirectory(directory.value(), *attributes.value) }
    }

    override fun makeSymbolicLink(
        link: BridgeObject,
        target: BridgeObject,
        attributes: BridgeFileAttributes,
        exception: BridgeFailure
    ) {
        serveBridge(exception) {
            provider.createSymbolicLink(link.value(), target.value(), *attributes.value)
        }
    }

    override fun makeHardLink(
        link: BridgeObject,
        existing: BridgeObject,
        exception: BridgeFailure
    ) {
        serveBridge(exception) { provider.createLink(link.value(), existing.value()) }
    }

    override fun removePath(path: BridgeObject, exception: BridgeFailure) {
        serveBridge(exception) { provider.delete(path.value()) }
    }

    override fun resolveSymbolicLink(
        link: BridgeObject,
        exception: BridgeFailure
    ): BridgeObject? =
        serveBridge(exception) { provider.readSymbolicLink(link.value()).toParcelable() }

    override fun startCopy(
        source: BridgeObject,
        target: BridgeObject,
        options: BridgeCopyOptions,
        callback: RemoteCallback
    ): RemoteCallback {
        val future = executorService.submit<Unit> {
            val exception = BridgeFailure()
            serveBridge(exception) {
                provider.copy(source.value(), target.value(), *options.value)
            }
            callback.sendResult(RemoteFileSystemProvider.CallbackArgs(exception).toBundle())
        }
        return RemoteCallback { future.cancel(true) }
    }

    override fun startMove(
        source: BridgeObject,
        target: BridgeObject,
        options: BridgeCopyOptions,
        callback: RemoteCallback
    ): RemoteCallback {
        val future = executorService.submit<Unit> {
            val exception = BridgeFailure()
            serveBridge(exception) {
                provider.move(source.value(), target.value(), *options.value)
            }
            callback.sendResult(RemoteFileSystemProvider.CallbackArgs(exception).toBundle())
        }
        return RemoteCallback { future.cancel(true) }
    }

    override fun sameFile(
        path: BridgeObject,
        path2: BridgeObject,
        exception: BridgeFailure
    ): Boolean = serveBridge(exception) { provider.isSameFile(path.value(), path2.value()) } ?: false

    override fun hidden(path: BridgeObject, exception: BridgeFailure): Boolean =
        serveBridge(exception) { provider.isHidden(path.value()) } ?: false

    override fun loadFileStore(
        path: BridgeObject,
        exception: BridgeFailure
    ): BridgeObject? = serveBridge(exception) { provider.getFileStore(path.value()).toParcelable() }

    override fun verifyAccess(
        path: BridgeObject,
        modes: BridgeSerializable,
        exception: BridgeFailure
    ) {
        serveBridge(exception) { provider.checkAccess(path.value(), *modes.value()) }
    }

    override fun loadAttributes(
        path: BridgeObject,
        type: BridgeSerializable,
        options: BridgeSerializable,
        exception: BridgeFailure
    ): BridgeObject? =
        serveBridge(exception) {
            provider.readAttributes(
                // We have to explicitly specify the Class type here, or it will be resolved to the
                // String overload.
                path.value(), type.value<Class<BasicFileAttributes>>(), *options.value()
            ).toParcelable()
        }

    override fun watchPath(
        path: BridgeObject,
        intervalMillis: Long,
        exception: BridgeFailure
    ): BridgePathObservable? =
        serveBridge(exception) {
            (provider as PathObservableProvider).observe(path.value(), intervalMillis).toBridge()
        }

    override fun startSearch(
        directory: BridgeObject,
        query: String,
        intervalMillis: Long,
        listener: BridgePathBatchListener,
        callback: RemoteCallback
    ): RemoteCallback {
        val future = executorService.submit<Unit> {
            val exception = BridgeFailure()
            serveBridge(exception) {
                (provider as Searchable).search(
                    directory.value(), query, intervalMillis, listener.value
                )
            }
            callback.sendResult(RemoteFileSystemProvider.CallbackArgs(exception).toBundle())
        }
        return RemoteCallback { future.cancel(true) }
    }
}
