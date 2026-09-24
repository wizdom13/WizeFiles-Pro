package com.wisso.wizefiles.provider.sftp.client

import com.wisso.wizefiles.provider.common.AbstractFileByteChannel
import com.wisso.wizefiles.provider.common.asFuture
import com.wisso.wizefiles.provider.common.map
import com.wisso.wizefiles.util.closeSafe
import com.wisso.wizefiles.util.findCauseByClass
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedByInterruptException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import net.schmizz.sshj.sftp.PacketType
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.RemoteFileAccessor
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException

class FileByteChannel(
    private val file: RemoteFile,
    isAppend: Boolean
) : AbstractFileByteChannel(isAppend) {
    override fun onReadAsync(position: Long, size: Int, timeoutMillis: Long): Future<ByteBuffer> {
        val request = try {
            RemoteFileAccessor.asyncRead(file, position, size)
        } catch (failure: IOException) {
            throw translate(failure)
        }
        return request.asFuture().map(
            { response -> decodeRead(response, size) },
            ::translateFutureFailure
        )
    }

    override fun onWrite(position: Long, source: ByteBuffer) {
        val start = source.position()
        val payload = ByteArray(source.remaining())
        source.duplicate().get(payload)
        try {
            file.write(position, payload, 0, payload.size)
        } catch (failure: IOException) {
            throw translate(failure)
        }
        source.position(start + payload.size)
    }

    override fun onTruncate(size: Long) {
        try {
            file.setLength(size)
        } catch (failure: IOException) {
            throw translate(failure)
        }
    }

    override fun onSize(): Long = try {
        file.length()
    } catch (failure: IOException) {
        throw translate(failure)
    }

    override fun onClose() {
        try {
            file.close()
        } catch (failure: SFTPException) {
            if (failure.statusCode != Response.StatusCode.NO_SUCH_FILE) throw failure
        }
    }

    private fun decodeRead(response: Response, requested: Int): ByteBuffer = when (response.type) {
        PacketType.STATUS -> {
            response.ensureStatusIs(Response.StatusCode.EOF)
            ByteBuffer.allocate(0)
        }
        PacketType.DATA -> {
            val advertised = response.readUInt32AsInt()
            if (advertised < 0) throw SFTPException("Negative SFTP data length")
            val count = minOf(advertised, requested)
            if (count == 0) ByteBuffer.allocate(0)
            else ByteBuffer.wrap(response.array(), response.rpos(), count)
        }
        else -> throw SFTPException("Unexpected SFTP read packet: " + response.type)
    }

    private fun translateFutureFailure(failure: Exception): Exception {
        val ioFailure = (failure as? ExecutionException)?.cause as? IOException ?: return failure
        return ExecutionException(translate(ioFailure))
    }

    private fun translate(failure: IOException): IOException = when {
        failure is SFTPException && failure.statusCode == Response.StatusCode.INVALID_HANDLE -> {
            setClosed()
            AsynchronousCloseException().apply { initCause(failure) }
        }
        failure.findCauseByClass<InterruptedException>() != null -> {
            closeSafe()
            ClosedByInterruptException().apply { initCause(failure) }
        }
        else -> failure
    }
}
