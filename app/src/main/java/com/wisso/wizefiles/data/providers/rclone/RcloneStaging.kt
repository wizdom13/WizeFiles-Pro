// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.rclone

import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption

internal object RcloneStaging {
    fun openInput(path: RclonePath): InputStream {
        val stagingFile = RcloneEngine.download(path.remoteName, path.remotePath)
        return object : FilterInputStream(stagingFile.inputStream()) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    stagingFile.delete()
                }
            }
        }
    }

    fun openOutput(path: RclonePath, append: Boolean): OutputStream {
        val stagingFile = if (append && RcloneEngine.stat(path.remoteName, path.remotePath) != null) {
            RcloneEngine.download(path.remoteName, path.remotePath)
        } else {
            RcloneEngine.newStagingFile("upload")
        }
        val delegate = if (append) {
            java.io.FileOutputStream(stagingFile, true).buffered()
        } else {
            stagingFile.outputStream().buffered()
        }
        RcloneUploadCache.register(path, stagingFile)
        return object : FilterOutputStream(delegate) {
            private var closed = false

            override fun close() {
                if (closed) return
                closed = true
                var failure: Throwable? = null
                try {
                    super.close()
                    RcloneUploadCache.markReadable(path, stagingFile)
                    RcloneEngine.upload(stagingFile, path.remoteName, path.remotePath)
                    RcloneUploadCache.complete(path, stagingFile)
                } catch (throwable: Throwable) {
                    failure = throwable
                    throw throwable
                } finally {
                    RcloneUploadCache.remove(path, stagingFile)
                    if (!stagingFile.delete() && failure == null) {
                        stagingFile.deleteOnExit()
                    }
                }
            }
        }
    }

    fun openChannel(path: RclonePath, options: Set<OpenOption>): SeekableByteChannel {
        val writes = options.hasWriteIntent()
        val exists = RcloneEngine.stat(path.remoteName, path.remotePath) != null
        val stagingFile = if (exists) {
            RcloneEngine.download(path.remoteName, path.remotePath)
        } else {
            RcloneEngine.newStagingFile("channel")
        }
        val localOptions = rcloneStagingChannelOptions(options)
        val delegate = Files.newByteChannel(stagingFile.toPath(), localOptions)
        if (writes) {
            RcloneUploadCache.register(path, stagingFile)
        }
        return UploadingSeekableByteChannel(delegate) {
            try {
                if (writes) {
                    RcloneUploadCache.markReadable(path, stagingFile)
                    RcloneEngine.upload(stagingFile, path.remoteName, path.remotePath)
                    RcloneUploadCache.complete(path, stagingFile)
                }
            } finally {
                if (writes) {
                    RcloneUploadCache.remove(path, stagingFile)
                }
                stagingFile.delete()
            }
        }
    }
}

internal fun rcloneStagingChannelOptions(options: Set<OpenOption>): Set<OpenOption> {
    val writes = options.hasWriteIntent()
    return options.toMutableSet().apply {
        // CREATE_NEW belongs to the remote destination. createTempFile() has already
        // created the unique local staging file, so reapplying it always fails.
        remove(StandardOpenOption.CREATE_NEW)
        if (isEmpty()) add(StandardOpenOption.READ)
        if (writes) add(StandardOpenOption.WRITE)
        add(StandardOpenOption.CREATE)
    }
}

private fun Set<OpenOption>.hasWriteIntent(): Boolean =
    StandardOpenOption.WRITE in this ||
        StandardOpenOption.APPEND in this ||
        StandardOpenOption.CREATE in this ||
        StandardOpenOption.CREATE_NEW in this

private class UploadingSeekableByteChannel(
    private val delegate: SeekableByteChannel,
    private val onClose: () -> Unit
) : SeekableByteChannel {
    private var closed = false

    override fun read(destination: ByteBuffer): Int = delegate.read(destination)

    override fun write(source: ByteBuffer): Int = delegate.write(source)

    override fun position(): Long = delegate.position()

    override fun position(newPosition: Long): SeekableByteChannel {
        delegate.position(newPosition)
        return this
    }

    override fun size(): Long = delegate.size()

    override fun truncate(size: Long): SeekableByteChannel {
        delegate.truncate(size)
        return this
    }

    override fun isOpen(): Boolean = !closed && delegate.isOpen

    override fun close() {
        if (closed) return
        closed = true
        try {
            delegate.close()
        } finally {
            onClose()
        }
    }
}
