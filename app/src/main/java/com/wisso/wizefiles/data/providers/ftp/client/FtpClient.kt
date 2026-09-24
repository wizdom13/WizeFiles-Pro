package com.wisso.wizefiles.provider.ftp.client

import java.nio.file.Path as Java8Path
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.nio.channels.SeekableByteChannel
import com.wisso.wizefiles.provider.common.ForwardingInputStream
import com.wisso.wizefiles.provider.common.ForwardingOutputStream
import com.wisso.wizefiles.provider.common.LocalWatchService
import com.wisso.wizefiles.provider.common.NotifyEntryModifiedOutputStream
import com.wisso.wizefiles.provider.common.NotifyEntryModifiedSeekableByteChannel
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient

object FtpClient {
    private const val RETRIEVE_FILE_SETUP_MAX_ATTEMPTS = 2
    private const val LIST_DIRECTORY_SETUP_MAX_ATTEMPTS = 2
    private const val LIST_FILE_SETUP_MAX_ATTEMPTS = 2
    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val SOCKET_TIMEOUT_MILLIS = 30_000
    private const val CONTROL_KEEP_ALIVE_SECONDS = 30L
    private val TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT)
            .withChronology(IsoChronology.INSTANCE)
            .withZone(ZoneOffset.UTC)

    @Volatile
    lateinit var authenticator: Authenticator

    private val connectionManager by lazy {
        FtpConnectionManager(::createClient, ::closeClient, ::isConnectionLostIOException)
    }

    private val directoryFilesCache = Collections.synchronizedMap(WeakHashMap<Path, FTPFile>())

    private fun acquireClient(authority: Authority): FTPClient =
        connectionManager.acquire(authority)

    @Throws(IOException::class)
    private fun createClient(authority: Authority): FTPClient {
        val password = authenticator.getPassword(authority)
            ?: throw IOException("No password found for $authority")
        return authority.protocol.createClient().apply {
            configure(FTPClientConfig(""))
            // This has to be set before connect().
            controlEncoding = authority.encoding
            listHiddenFiles = true
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            defaultTimeout = SOCKET_TIMEOUT_MILLIS
            setDataTimeout(Duration.ofMillis(SOCKET_TIMEOUT_MILLIS.toLong()))
            setControlKeepAliveTimeout(Duration.ofSeconds(CONTROL_KEEP_ALIVE_SECONDS))
            connect(authority.host, authority.port)
            try {
                if (!FTPReply.isPositiveCompletion(replyCode)) {
                    throwNegativeReplyCodeException()
                }
                if (!login(authority.username, password)) {
                    throwNegativeReplyCodeException()
                }
            } catch (t: Throwable) {
                disconnect()
                throw t
            }
            // This has to be called after connect() despite being entirely local.
            applyDataConnectionMode(authority.mode)
            try {
                if (this is FTPSClient) {
                    // @see https://datatracker.ietf.org/doc/html/rfc4217#section-9
                    execPBSZ(0)
                    execPROT("P")
                }
                if (!setFileType(FTPClient.BINARY_FILE_TYPE)) {
                    throwNegativeReplyCodeException()
                }
            } catch (t: Throwable) {
                closeClient(this)
                throw t
            }
        }
    }

    private fun releaseClient(authority: Authority, client: FTPClient) =
        connectionManager.release(authority, client)

    fun close(authority: Authority) = connectionManager.close(authority)

    fun closeAll() = connectionManager.closeAll()

    internal fun replaceIdleConnectionsForTesting(authority: Authority, clients: List<FTPClient>) =
        connectionManager.replaceIdle(authority, clients)

    internal fun idleConnectionsForTesting(authority: Authority): List<FTPClient> =
        connectionManager.idle(authority)

    private fun discardClient(client: FTPClient) = connectionManager.discard(client)

    private fun disconnectQuietly(client: FTPClient) {
        try {
            client.disconnect()
        } catch (e: IOException) {
            if (!isConnectionLostIOException(e)) {
                com.wisso.wizefiles.util.AppLog.w("FtpClient", "FTP disconnect failed", e)
            }
        }
    }

    private fun closeClient(client: FTPClient) {
        if (client.isConnected) {
            try {
                client.logout()
            } catch (e: IOException) {
                if (!isConnectionLostIOException(e)) {
                    com.wisso.wizefiles.util.AppLog.w("FtpClient", "FTP logout failed", e)
                }
            }
        }
        disconnectQuietly(client)
    }

    private fun releaseOrDiscardClient(
        authority: Authority,
        client: FTPClient,
        throwable: Throwable?
    ) = connectionManager.release(authority, client, throwable)

    private inline fun <R> useClient(authority: Authority, block: (FTPClient) -> R): R {
        val client = acquireClient(authority)
        var failure: Throwable? = null
        try {
            return block(client)
        } catch (t: Throwable) {
            failure = t
            throw t
        } finally {
            releaseOrDiscardClient(authority, client, failure)
        }
    }

    @Throws(IOException::class)
    fun createDirectory(path: Path) {
        useClient(path.authority) { client ->
            if (!client.makeDirectory(path.remotePath)) {
                client.throwNegativeReplyCodeException()
            }
        }
        LocalWatchService.onEntryCreated(path as Java8Path)
    }

    @Throws(IOException::class)
    fun createFile(path: Path) {
        storeFile(path).close()
        LocalWatchService.onEntryCreated(path as Java8Path)
    }

    @Throws(IOException::class)
    fun delete(path: Path) {
        val cachedFile = synchronized(directoryFilesCache) {
            directoryFilesCache[path]
        }
        if (cachedFile != null) {
            delete(path, cachedFile.isDirectory)
            return
        }

        // FTP has no portable metadata command that avoids a data connection. Try DELE first,
        // then RMD for a 550 reply, instead of opening a fragile listing data connection merely
        // to discover the target type. This is especially important for transfer staging files.
        try {
            deleteFile(path)
        } catch (fileFailure: NegativeReplyCodeException) {
            if (fileFailure.replyCode != FTPReply.FILE_UNAVAILABLE) {
                throw fileFailure
            }
            try {
                deleteDirectory(path)
            } catch (directoryFailure: NegativeReplyCodeException) {
                directoryFailure.addSuppressed(fileFailure)
                throw directoryFailure
            }
        }
    }

    @Throws(IOException::class)
    fun delete(path: Path, isDirectory: Boolean) {
        if (isDirectory) {
            deleteDirectory(path)
        } else {
            deleteFile(path)
        }
    }

    @Throws(IOException::class)
    fun deleteFile(path: Path) {
        useClient(path.authority) { client ->
            if (!client.deleteFile(path.remotePath)) {
                client.throwNegativeReplyCodeException()
            }
        }
        directoryFilesCache -= path
        LocalWatchService.onEntryDeleted(path as Java8Path)
    }

    @Throws(IOException::class)
    fun deleteDirectory(path: Path) {
        useClient(path.authority) { client ->
            if (!client.removeDirectory(path.remotePath)) {
                client.throwNegativeReplyCodeException()
            }
        }
        directoryFilesCache -= path
        LocalWatchService.onEntryDeleted(path as Java8Path)
    }

    @Throws(IOException::class)
    fun renameFile(source: Path, target: Path) {
        if (source.authority != target.authority) {
            throw IOException("Paths aren't on the same authority")
        }
        useClient(source.authority) { client ->
            if (!client.rename(source.remotePath, target.remotePath)) {
                client.throwNegativeReplyCodeException()
            }
        }
        directoryFilesCache -= source
        directoryFilesCache -= target
        LocalWatchService.onEntryDeleted(source as Java8Path)
        LocalWatchService.onEntryCreated(target as Java8Path)
    }

    @Throws(IOException::class)
    fun retrieveFile(path: Path): InputStream {
        val authority = path.authority
        var attempt = 0
        var lastFailure: Throwable? = null
        while (attempt < RETRIEVE_FILE_SETUP_MAX_ATTEMPTS) {
            attempt++
            val client = acquireClient(authority)
            val inputStream = try {
                client.retrieveFileStream(path.remotePath) ?: client.throwNegativeReplyCodeException()
            } catch (t: Throwable) {
                releaseOrDiscardClient(authority, client, t)
                lastFailure = t
                if (attempt < RETRIEVE_FILE_SETUP_MAX_ATTEMPTS && isConnectionLostIOException(t)) {
                    continue
                }
                throw t
            }
            return CompletePendingCommandInputStream(inputStream, authority, client)
        }
        throw IllegalStateException("Unreachable", lastFailure)
    }

    @Throws(IOException::class)
    fun listDirectory(path: Path): List<Path> {
        val authority = path.authority
        var attempt = 0
        while (attempt < LIST_DIRECTORY_SETUP_MAX_ATTEMPTS) {
            attempt++
            val client = acquireClient(authority)
            try {
                val files = client.mlistDirCompat(path.remotePath)
                    ?: client.throwNegativeReplyCodeException()
                return files.mapNotNull { file ->
                    if (file.name == "." || file.name == "..") {
                        return@mapNotNull null
                    }
                    path.resolve(file.name).also { directoryFilesCache[it] = file }
                }.also {
                    releaseClient(authority, client)
                }
            } catch (t: Throwable) {
                val isRetryableListingSetupFailure =
                    t is IOException && ftpListingSetupFailureReasonOrNull(t) != null
                if (isRetryableListingSetupFailure) {
                    discardClient(client)
                    if (attempt < LIST_DIRECTORY_SETUP_MAX_ATTEMPTS) {
                        continue
                    }
                } else {
                    releaseOrDiscardClient(authority, client, t)
                }
                throw t
            }
        }
        throw IllegalStateException("Unreachable")
    }

    @Throws(IOException::class)
    fun listFileOrNull(path: Path, noFollowLinks: Boolean): FTPFile? =
        try {
            listFile(path, noFollowLinks)
        } catch (e: NegativeReplyCodeException) {
            null
        }

    @Throws(IOException::class)
    fun listFile(path: Path, noFollowLinks: Boolean): FTPFile {
        val file = listFileNoFollowLinks(path, noFollowLinks)
        if (!file.isSymbolicLink || noFollowLinks) {
            return file
        }
        val targetString = file.link ?: throw IOException("FTPFile.getLink() returned null: $file")
        val target = path.resolve(targetString)
        return listFileNoFollowLinks(target, false)
    }

    @Throws(IOException::class)
    private fun listFileNoFollowLinks(path: Path, preserveCacheForSymbolicLink: Boolean): FTPFile {
        synchronized(directoryFilesCache) {
            directoryFilesCache[path]?.let {
                if (!(it.isSymbolicLink && preserveCacheForSymbolicLink)) {
                    directoryFilesCache -= path
                }
                return it
            }
        }

        val authority = path.authority
        var attempt = 0
        while (attempt < LIST_FILE_SETUP_MAX_ATTEMPTS) {
            attempt++
            val client = acquireClient(authority)
            try {
                val file = client.mlistFileCompat(path.remotePath)
                if (file == null) {
                    if (FTPReply.isPositiveCompletion(client.replyCode)) {
                        // LIST completed successfully but the requested entry was absent. Reusing
                        // its final 226 reply as an error misclassifies a normal missing file.
                        throw NegativeReplyCodeException(
                            FTPReply.FILE_UNAVAILABLE,
                            "FTP entry does not exist: ${path.remotePath}"
                        )
                    }
                    client.throwNegativeReplyCodeException()
                }
                releaseClient(authority, client)
                return file
            } catch (t: Throwable) {
                val isRetryableListingSetupFailure =
                    t is IOException && ftpListingSetupFailureReasonOrNull(t) != null
                if (isRetryableListingSetupFailure) {
                    discardClient(client)
                    if (attempt < LIST_FILE_SETUP_MAX_ATTEMPTS) {
                        continue
                    }
                } else {
                    releaseOrDiscardClient(authority, client, t)
                }
                throw t
            }
        }
        throw IllegalStateException("Unreachable")
    }

    @Throws(IOException::class)
    fun openByteChannel(path: Path, isAppend: Boolean): SeekableByteChannel {
        val authority = path.authority
        val client = acquireClient(authority)
        if (!client.hasFeature(FTPCmd.REST)) {
            throw IOException("Missing feature ${FTPCmd.REST.command}")
        }
        return NotifyEntryModifiedSeekableByteChannel(
            FileByteChannel(
                client, { releaseClient(authority, client) }, path.remotePath, isAppend
            ), path as Java8Path
        )
    }

    @Throws(IOException::class)
    fun setLastModifiedTime(path: Path, lastModifiedTime: Instant) {
        val lastModifiedTimeString = TIMESTAMP_FORMATTER.format(lastModifiedTime)
        useClient(path.authority) { client ->
            if (!client.setModificationTimeCompat(path.remotePath, lastModifiedTimeString)) {
                client.throwNegativeReplyCodeException()
            }
        }
        LocalWatchService.onEntryModified(path as Java8Path)
    }

    @Throws(IOException::class)
    fun storeFile(path: Path): OutputStream {
        val authority = path.authority
        val client = acquireClient(authority)
        val outputStream = try {
            client.storeFileStream(path.remotePath) ?: client.throwNegativeReplyCodeException()
        } catch (t: Throwable) {
            releaseOrDiscardClient(authority, client, t)
            throw t
        }
        return NotifyEntryModifiedOutputStream(
            CompletePendingCommandOutputStream(outputStream, authority, client), path as Java8Path
        )
    }

    interface Path {
        val authority: Authority
        val remotePath: String
        fun resolve(other: String): Path
    }

    private class CompletePendingCommandInputStream(
        inputStream: InputStream,
        private val authority: Authority,
        private val client: FTPClient
    ) : ForwardingInputStream(inputStream) {
        private var streamFailure: IOException? = null

        @Throws(IOException::class)
        override fun read(): Int =
            try {
                super.read()
            } catch (e: IOException) {
                throw recordAndNormalizeFailure(e)
            }

        @Throws(IOException::class)
        override fun read(b: ByteArray): Int =
            try {
                super.read(b)
            } catch (e: IOException) {
                throw recordAndNormalizeFailure(e)
            }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            try {
                super.read(b, off, len)
            } catch (e: IOException) {
                throw recordAndNormalizeFailure(e)
            }

        private fun recordAndNormalizeFailure(e: IOException): IOException {
            val mapped = e.withFtpConnectionLostReasonIfNeeded()
            streamFailure = mapped
            return mapped
        }

        @Throws(IOException::class)
        override fun close() {
            var failure: Throwable? = null
            try {
                super.close()
                if (!client.completePendingCommand()) {
                    // We may close the input stream before the file is fully read (may happen when
                    // decoding images) and it will result in an error reported here, but that's
                    // totally fine.
                    val error = client.createNegativeReplyCodeException()
                    if (!isConnectionLostIOException(error)) {
                        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", error)
                    }
                }
            } catch (t: Throwable) {
                failure = t
                throw t
            } finally {
                releaseOrDiscardClient(authority, client, failure ?: streamFailure)
            }
        }
    }

    private class CompletePendingCommandOutputStream(
        outputStream: OutputStream,
        private val authority: Authority,
        private val client: FTPClient
    ) : ForwardingOutputStream(outputStream) {
        @Throws(IOException::class)
        override fun close() {
            var failure: Throwable? = null
            try {
                super.close()
                if (!client.completePendingCommand()) {
                    client.throwNegativeReplyCodeException()
                }
            } catch (t: Throwable) {
                failure = t
                throw t
            } finally {
                releaseOrDiscardClient(authority, client, failure)
            }
        }
    }

    internal fun isConnectionLostIOException(throwable: Throwable): Boolean =
        FtpErrorMapper.isConnectionLost(throwable)

    internal fun ftpConnectionLostReasonOrNull(throwable: IOException): String? =
        FtpErrorMapper.connectionLostReason(throwable)

    internal fun ftpDataConnectionFailureReasonOrNull(throwable: IOException): String? =
        FtpErrorMapper.dataConnectionReason(throwable)

    internal fun ftpListingSetupFailureReasonOrNull(throwable: IOException): String? =
        ftpDataConnectionFailureReasonOrNull(throwable)
            ?: ftpConnectionLostReasonOrNull(throwable)

    private fun IOException.withFtpConnectionLostReasonIfNeeded(): IOException {
        val mappedReason = ftpConnectionLostReasonOrNull(this) ?: return this
        if (mappedReason == message) {
            return this
        }
        return IOException(mappedReason, this)
    }

    internal fun FTPClient.applyDataConnectionMode(mode: Mode) =
        FtpFeatureNegotiator.configureDataConnection(this, mode)

}
