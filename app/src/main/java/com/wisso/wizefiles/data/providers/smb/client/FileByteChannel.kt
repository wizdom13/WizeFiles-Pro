// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb.client

import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.io.ByteChunkProvider
import com.hierynomus.smbj.share.File
import com.hierynomus.smbj.share.FileAccessor
import com.wisso.wizefiles.provider.common.AbstractFileByteChannel
import com.wisso.wizefiles.provider.common.map
import com.wisso.wizefiles.util.closeSafe
import com.wisso.wizefiles.util.findCauseByClass
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedByInterruptException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

class FileByteChannel(
    private val file: File,
    isAppend: Boolean
) : AbstractFileByteChannel(isAppend, shouldCancelRead = false) {
    override fun onReadAsync(position: Long, size: Int, timeoutMillis: Long): Future<ByteBuffer> {
        val request = try {
            FileAccessor.readAsync(file, position, size)
        } catch (failure: SMBRuntimeException) {
            throw translate(failure)
        }
        return request.map(
            { response ->
                when (response.header.statusCode) {
                    NtStatus.STATUS_END_OF_FILE.value -> ByteBuffer.allocate(0)
                    NtStatus.STATUS_SUCCESS.value -> {
                        val count = minOf(response.data.size, size)
                        if (count == 0) ByteBuffer.allocate(0)
                        else ByteBuffer.wrap(response.data, 0, count)
                    }
                    else -> throw translate(
                        SMBRuntimeException(
                            SMBApiException(response.header, "SMB read failed")
                        )
                    )
                }
            },
            { failure ->
                ExecutionException(translate(SMBRuntimeException(failure)))
            }
        )
    }

    override fun onWrite(position: Long, source: ByteBuffer) {
        val start = source.position()
        val available = source.remaining()
        val count = try {
            file.write(BufferChunks(source.duplicate(), position))
        } catch (failure: SMBRuntimeException) {
            throw translate(failure)
        }
        if (count !in 0..available) {
            throw IOException("SMB server acknowledged an invalid write count: " + count)
        }
        source.position(start + count)
    }

    override fun onTruncate(size: Long) = smbCall { file.setLength(size) }

    override fun onSize(): Long = smbCall {
        file.getFileInformation(FileStandardInformation::class.java).endOfFile
    }

    override fun onForce(metaData: Boolean) = smbCall { file.flush() }

    override fun onClose() {
        try {
            file.close()
        } catch (failure: SMBRuntimeException) {
            if (failure.findCauseByClass<InterruptedException>() != null) {
                throw InterruptedIOException().apply { initCause(failure) }
            }
            throw IOException(failure)
        }
    }

    private inline fun <T> smbCall(action: () -> T): T = try {
        action()
    } catch (failure: SMBRuntimeException) {
        throw translate(failure)
    }

    private fun translate(failure: SMBRuntimeException): IOException = when {
        failure.findCauseByClass<SMBApiException>()?.status == NtStatus.STATUS_FILE_CLOSED -> {
            setClosed()
            AsynchronousCloseException().apply { initCause(failure) }
        }
        failure.findCauseByClass<InterruptedException>() != null -> {
            closeSafe()
            ClosedByInterruptException().apply { initCause(failure) }
        }
        else -> IOException(failure)
    }

    private class BufferChunks(buffer: ByteBuffer, offset: Long) : ByteChunkProvider() {
        private val source = buffer

        init {
            this.offset = offset
        }

        override fun isAvailable(): Boolean = source.hasRemaining()
        override fun bytesLeft(): Int = source.remaining()
        override fun prepareWrite(maxBytesToPrepare: Int) = Unit

        override fun getChunk(chunk: ByteArray): Int {
            val count = minOf(chunk.size, source.remaining())
            source.get(chunk, 0, count)
            return count
        }
    }
}
