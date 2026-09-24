package com.wisso.wizefiles.provider.ftp.client

import com.wisso.wizefiles.core.android.compat.nullInputStream
import com.wisso.wizefiles.provider.common.AbstractFileByteChannel
import com.wisso.wizefiles.provider.common.ByteBufferInputStream
import com.wisso.wizefiles.provider.common.readFully
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import org.apache.commons.net.ftp.FTPClient

class FileByteChannel(
    private val client: FTPClient,
    private val releaseClient: (FTPClient) -> Unit,
    private val path: String,
    isAppend: Boolean
) : AbstractFileByteChannel(isAppend, joinCancelledRead = true) {
    private val sessionMonitor = Any()

    override fun onRead(position: Long, size: Int): ByteBuffer = withSession {
        restartOffset = position
        val stream = retrieveFileStream(path) ?: failFromReply()
        val bytes = ByteArray(size)
        val count = try {
            stream.use { it.readFully(bytes, 0, size) }
        } finally {
            completePendingCommand()
        }
        ByteBuffer.wrap(bytes, 0, count)
    }

    override fun onWrite(position: Long, source: ByteBuffer) {
        withSession {
            restartOffset = position
            storeBuffer(source, append = false)
        }
    }

    override fun onAppend(source: ByteBuffer) {
        withSession { storeBuffer(source, append = true) }
    }

    override fun onTruncate(size: Long) {
        withSession {
            restartOffset = size
            InputStream::class.nullInputStream().use { empty ->
                if (!storeFile(path, empty)) failFromReply()
            }
        }
    }

    override fun onSize(): Long = withSession {
        val response = getSize(path) ?: failFromReply()
        response.toLongOrNull()
            ?.takeIf { it >= 0 }
            ?: throw IOException("FTP server returned an invalid file size: " + response)
    }

    override fun onClose() {
        withSession { releaseClient(this) }
    }

    private fun FTPClient.storeBuffer(buffer: ByteBuffer, append: Boolean) {
        ByteBufferInputStream(buffer).use { input ->
            val accepted = if (append) appendFile(path, input) else storeFile(path, input)
            if (!accepted) failFromReply()
        }
    }

    private fun FTPClient.failFromReply(): Nothing {
        throwNegativeReplyCodeException()
    }

    private inline fun <T> withSession(action: FTPClient.() -> T): T =
        synchronized(sessionMonitor) { client.action() }
}
