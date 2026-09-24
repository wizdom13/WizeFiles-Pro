package com.wisso.wizefiles.core.files.provider

import android.os.Handler
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.system.ErrnoException
import android.system.OsConstants
import com.wisso.wizefiles.core.android.compat.ProxyFileDescriptorCallbackCompat
import com.wisso.wizefiles.core.android.compat.openProxyFileDescriptorCompat
import com.wisso.wizefiles.core.app.storageManager
import com.wisso.wizefiles.core.files.provider.legacy.canOpenDirectly
import com.wisso.wizefiles.core.files.provider.legacy.coerceLegacyOpenMode
import com.wisso.wizefiles.core.files.provider.legacy.toLegacyOpenMode
import com.wisso.wizefiles.core.files.provider.legacy.toLegacyOpenOptions
import com.wisso.wizefiles.provider.common.InvalidFileNameException
import com.wisso.wizefiles.provider.common.IsDirectoryException
import com.wisso.wizefiles.provider.common.force
import com.wisso.wizefiles.provider.common.isForceable
import com.wisso.wizefiles.provider.os.syscall.SyscallException
import com.wisso.wizefiles.util.withoutPenaltyDeathOnNetwork
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.FileSystemLoopException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

internal object PathParcelFileDescriptorOpener {
    @Throws(FileNotFoundException::class)
    fun open(path: Path, mode: String, callbackHandler: Handler): ParcelFileDescriptor {
        val modeBits = path.coerceLegacyOpenMode(mode.toLegacyOpenMode())
        if (path.canOpenDirectly(modeBits)) {
            return ParcelFileDescriptor.open(path.toFile(), modeBits)
        }
        val options = modeBits.toLegacyOpenOptions()
        val channel = try {
            // Strict mode thread policy is passed through binder, but some apps (notably music
            // players) like to open file on their main thread.
            StrictMode::class.withoutPenaltyDeathOnNetwork {
                Files.newByteChannel(path, options)
            }
        } catch (e: IOException) {
            throw e.toFileNotFoundException()
        }
        return try {
            storageManager.openProxyFileDescriptorCompat(
                modeBits,
                ChannelCallback(channel),
                callbackHandler
            )
        } catch (e: IOException) {
            runCatching { channel.close() }
            throw e.toFileNotFoundException()
        }
    }
}

private fun IOException.toFileNotFoundException(): FileNotFoundException =
    if (this is FileNotFoundException) {
        this
    } else {
        FileNotFoundException(message).apply { initCause(this@toFileNotFoundException) }
    }

private class ChannelCallback(
    private val channel: SeekableByteChannel
) : ProxyFileDescriptorCallbackCompat() {
    private var offset = 0L
    private var released = false

    @Throws(ErrnoException::class)
    override fun onGetSize(): Long {
        ensureNotReleased()
        return try {
            channel.size()
        } catch (e: IOException) {
            throw e.toErrnoException()
        }
    }

    @Throws(ErrnoException::class)
    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        ensureNotReleased()
        if (this.offset != offset) {
            try {
                channel.position(offset)
            } catch (e: IOException) {
                throw e.toErrnoException()
            }
            this.offset = offset
        }
        val buffer = ByteBuffer.wrap(data, 0, size)
        // Unlike ReadableByteChannel which may not fill the buffer and returns -1 upon
        // end-of-stream, we need to read as much as we can unless end-of-stream is reached.
        while (buffer.hasRemaining()) {
            val channelSize = try {
                channel.read(buffer)
            } catch (e: IOException) {
                throw e.toErrnoException()
            }
            if (channelSize == -1) {
                break
            }
            this.offset += channelSize
        }
        return (this.offset - offset).toInt()
    }

    @Throws(ErrnoException::class)
    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        ensureNotReleased()
        if (this.offset != offset) {
            try {
                channel.position(offset)
            } catch (e: IOException) {
                throw e.toErrnoException()
            }
            this.offset = offset
        }
        val buffer = ByteBuffer.wrap(data, 0, size)
        return try {
            channel.write(buffer)
        } catch (e: IOException) {
            throw e.toErrnoException()
        }.also { this.offset += it.toLong() }
    }

    @Throws(ErrnoException::class)
    override fun onFsync() {
        ensureNotReleased()
        if (channel.isForceable) {
            try {
                channel.force(true)
            } catch (e: IOException) {
                throw e.toErrnoException()
            }
        }
    }

    @Throws(ErrnoException::class)
    private fun ensureNotReleased() {
        if (released) {
            throw ErrnoException(null, OsConstants.EBADF)
        }
    }

    override fun onRelease() {
        if (released) {
            return
        }
        try {
            channel.close()
        } catch (e: IOException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        }
        released = true
    }

    private fun IOException.toErrnoException(): ErrnoException {
        val cause = cause
        return if (this is FileSystemException && cause is SyscallException) {
            ErrnoException(cause.functionName, cause.errno, this)
        } else {
            val errno = when (this) {
                is AccessDeniedException -> OsConstants.EPERM
                is FileSystemLoopException -> OsConstants.ELOOP
                is InvalidFileNameException -> OsConstants.EINVAL
                is IsDirectoryException -> OsConstants.EISDIR
                is NoSuchFileException -> OsConstants.ENOENT
                is ClosedByInterruptException, is InterruptedIOException -> OsConstants.EINTR
                else -> OsConstants.EIO
            }
            ErrnoException(message, errno, this)
        }
    }
}
