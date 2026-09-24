// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.spi.FileSystemProvider
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.provider.common.PathObservable
import com.wisso.wizefiles.provider.common.PathObservableProvider
import com.wisso.wizefiles.provider.common.Searchable
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.RemoteCallback
import com.wisso.wizefiles.util.getArgs
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.Serializable
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

abstract class RemoteFileSystemProvider(
    private val remoteInterface: BinderEndpoint<IFileProviderBridge>
) : FileSystemProvider(), PathObservableProvider, Searchable {
    @Throws(IOException::class)
    override fun newInputStream(file: Path, vararg options: OpenOption): InputStream =
        remoteInterface.requireService().invokeBridge { exception ->
            openInput(file.toParcelable(), options.toParcelable(), exception)
        }

    @Throws(IOException::class)
    override fun newFileChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): FileChannel {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel {
        val options = when (options) {
            is Serializable -> options
            else -> options.toSet() as Serializable
        }
        return remoteInterface.requireService().invokeBridge { exception ->
            openChannel(
                file.toParcelable(), options.toParcelable(), attributes.toParcelable(), exception
            )
        }
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        val filter = when (filter) {
            is Parcelable -> filter
            filesAcceptAllFilter -> ParcelableAcceptAllFilter.instance
            else -> throw IllegalArgumentException("$filter is not Parcelable")
        }
        return remoteInterface.requireService().invokeBridge { exception ->
            listDirectory(directory.toParcelable(), filter.toParcelable(), exception)
        }.value
    }

    @Throws(IOException::class)
    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        remoteInterface.requireService().invokeBridge { exception ->
            makeDirectory(directory.toParcelable(), attributes.toParcelable(), exception)
        }
    }

    @Throws(IOException::class)
    override fun createSymbolicLink(link: Path, target: Path, vararg attributes: FileAttribute<*>) {
        remoteInterface.requireService().invokeBridge { exception ->
            makeSymbolicLink(
                link.toParcelable(), target.toParcelable(), attributes.toParcelable(), exception
            )
        }
    }

    @Throws(IOException::class)
    override fun createLink(link: Path, existing: Path) {
        remoteInterface.requireService().invokeBridge { exception ->
            makeHardLink(link.toParcelable(), existing.toParcelable(), exception)
        }
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        remoteInterface.requireService().invokeBridge { exception -> removePath(path.toParcelable(), exception) }
    }

    @Throws(IOException::class)
    override fun readSymbolicLink(link: Path): Path =
        remoteInterface.requireService().invokeBridge { exception ->
            resolveSymbolicLink(link.toParcelable(), exception)
        }.value()

    @Throws(IOException::class)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        awaitRemoteOperation { callback ->
            remoteInterface.requireService().invokeBridge {
                startCopy(
                    source.toParcelable(), target.toParcelable(), options.toParcelable(),
                    callback
                )
            }
        }
    }

    @Throws(IOException::class)
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        awaitRemoteOperation { callback ->
            remoteInterface.requireService().invokeBridge {
                startMove(
                    source.toParcelable(), target.toParcelable(), options.toParcelable(),
                    callback
                )
            }
        }
    }

    @Throws(IOException::class)
    override fun isSameFile(path: Path, path2: Path): Boolean =
        remoteInterface.requireService().invokeBridge { exception ->
            sameFile(path.toParcelable(), path2.toParcelable(), exception)
        }

    @Throws(IOException::class)
    override fun isHidden(path: Path): Boolean =
        remoteInterface.requireService().invokeBridge { exception -> hidden(path.toParcelable(), exception) }

    @Throws(IOException::class)
    override fun getFileStore(path: Path): FileStore =
        remoteInterface.requireService().invokeBridge {
            exception -> loadFileStore(path.toParcelable(), exception)
        }.value()

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        remoteInterface.requireService().invokeBridge { exception ->
            verifyAccess(path.toParcelable(), modes.toParcelable(), exception)
        }
    }

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A =
        remoteInterface.requireService().invokeBridge { exception ->
            loadAttributes(
                path.toParcelable(), type.toParcelable(), options.toParcelable(), exception
            )
        }.value()

    @Throws(IOException::class)
    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun observe(path: Path, intervalMillis: Long): PathObservable =
        remoteInterface.requireService().invokeBridge { exception ->
            watchPath(path.toParcelable(), intervalMillis, exception)
        }.also { it.connectObserver() }

    @Throws(IOException::class)
    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        awaitRemoteOperation { callback ->
            remoteInterface.requireService().invokeBridge {
                startSearch(
                    directory.toParcelable(), query, intervalMillis,
                    listener.toParcelable(), callback
                )
            }
        }
    }

    private fun awaitRemoteOperation(start: (RemoteCallback) -> RemoteCallback) {
        val completed = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        var interruptible: RemoteCallback? = null

        fun complete(throwable: Throwable?) {
            if (completed.compareAndSet(false, true)) {
                failure.set(throwable)
                latch.countDown()
            }
        }

        val deathRegistration = remoteInterface.onBinderDeath {
            complete(BridgeUnavailableException("Remote filesystem process died during operation"))
        }
        try {
            val callback = RemoteCallback { result ->
                runCatching {
                    result.getArgs<CallbackArgs>().exception.value
                }.fold(
                    onSuccess = ::complete,
                    onFailure = ::complete
                )
            }
            interruptible = start(callback)
            if (!latch.await(REMOTE_OPERATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                complete(SocketTimeoutException("Remote filesystem operation timed out"))
                interruptible?.sendResult(Bundle())
            }
        } catch (exception: InterruptedException) {
            complete(exception)
            interruptible?.sendResult(Bundle())
            Thread.currentThread().interrupt()
            throw InterruptedIOException().apply { initCause(exception) }
        } finally {
            deathRegistration.close()
        }
        failure.get()?.let { throwable ->
            if (throwable is IOException) throw throwable
            throw IOException("Remote filesystem operation failed", throwable)
        }
    }

    private class ParcelableAcceptAllFilter private constructor() : DirectoryStream.Filter<Path>,
        Parcelable {
        override fun accept(entry: Path): Boolean = true

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {}

        companion object {
            val instance = ParcelableAcceptAllFilter()

            @JvmField
            val CREATOR = object : Parcelable.Creator<ParcelableAcceptAllFilter> {
                override fun createFromParcel(source: Parcel): ParcelableAcceptAllFilter = instance

                override fun newArray(size: Int): Array<ParcelableAcceptAllFilter?> =
                    arrayOfNulls(size)
            }
        }
    }

    private companion object {
        const val REMOTE_OPERATION_TIMEOUT_MINUTES = 30L
    }

    @Parcelize
    internal class CallbackArgs(val exception: BridgeFailure) : ParcelableArgs
}
