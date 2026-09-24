package com.wisso.wizefiles.provider.sftp.client

import java.nio.channels.SeekableByteChannel
import com.wisso.wizefiles.provider.common.LocalWatchService
import com.wisso.wizefiles.provider.common.NotifyEntryModifiedSeekableByteChannel
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap
import java.nio.file.Path as Java8Path

object SftpClient {
    @Volatile
    lateinit var authenticator: Authenticator

    private val connectionManager by lazy {
        SftpConnectionManager { authority -> authenticator.getAuthentication(authority) }
    }

    private val directoryFileAttributesCache =
        Collections.synchronizedMap(WeakHashMap<Path, FileAttributes>())

    @Throws(SftpClientException::class)
    fun access(path: Path, flags: Set<OpenMode>) {
        val file = open(path, flags, FileAttributes.EMPTY)
        try {
            file.close()
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
    }

    @Throws(SftpClientException::class)
    fun lstat(path: Path): FileAttributes {
        val client = connectionManager.get(path.authority)
        synchronized(directoryFileAttributesCache) {
            directoryFileAttributesCache[path]?.let {
                return it.also { directoryFileAttributesCache -= path }
            }
        }
        return try {
            client.lstat(path.remotePath)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
    }

    @Throws(SftpClientException::class)
    fun mkdir(path: Path, attributes: FileAttributes) {
        val client = connectionManager.get(path.authority)
        try {
            client.sftpEngine.makeDir(path.remotePath, attributes)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
        LocalWatchService.onEntryCreated(path as Java8Path)
    }

    @Throws(SftpClientException::class)
    private fun open(path: Path, flags: Set<OpenMode>, attributes: FileAttributes): RemoteFile {
        val client = connectionManager.get(path.authority)
        return try {
            client.open(path.remotePath, flags, attributes)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
    }

    @Throws(SftpClientException::class)
    fun openByteChannel(
        path: Path,
        flags: Set<OpenMode>,
        attributes: FileAttributes
    ): SeekableByteChannel {
        val file = open(path, flags, attributes)
        return NotifyEntryModifiedSeekableByteChannel(
            FileByteChannel(file, flags.contains(OpenMode.APPEND)), path as Java8Path
        )
    }

    @Throws(SftpClientException::class)
    fun readlink(path: Path): String {
        val client = connectionManager.get(path.authority)
        return try {
            client.readlink(path.remotePath)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
    }

    @Throws(SftpClientException::class)
    fun realpath(path: Path): Path {
        val client = connectionManager.get(path.authority)
        val realPath = try {
            client.canonicalize(path.remotePath)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
        return path.resolve(realPath)
    }

    @Throws(SftpClientException::class)
    fun remove(path: Path) {
        val attributes = lstat(path)
        val isDirectory = attributes.type == FileMode.Type.DIRECTORY
        if (isDirectory) {
            rmdir(path)
        } else {
            unlink(path)
        }
    }

    // Note that unlike POSIX rename(), this won't overwrite an existing file.
    @Throws(SftpClientException::class)
    fun rename(path: Path, newPath: Path) {
        if (newPath.authority != path.authority) {
            throw SftpClientException(
                SFTPException(Response.StatusCode.FAILURE, "Paths aren't on the same authority")
            )
        }
        val client = connectionManager.get(path.authority)
        try {
            client.rename(path.remotePath, newPath.remotePath)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
        directoryFileAttributesCache -= path
        directoryFileAttributesCache -= newPath
        LocalWatchService.onEntryDeleted(path as Java8Path)
        LocalWatchService.onEntryCreated(newPath as Java8Path)
    }

    @Throws(SftpClientException::class)
    fun rmdir(path: Path) {
        val client = connectionManager.get(path.authority)
        try {
            client.rmdir(path.remotePath)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
        directoryFileAttributesCache -= path
        LocalWatchService.onEntryDeleted(path as Java8Path)
    }

    @Throws(SftpClientException::class)
    fun scandir(path: Path): List<Path> {
        val client = connectionManager.get(path.authority)
        val files = try {
            client.ls(path.remotePath)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
        return files.map { file ->
            // The attributes here are from lstat().
            // https://github.com/openssh/openssh-portable/blob/71241fc05db4bbb11bb29340b44b92e2575373d8/sftp-server.c#L1110
            path.resolve(file.name).also { directoryFileAttributesCache[it] = file.attributes }
        }
    }

    @Throws(SftpClientException::class)
    fun setstat(path: Path, attributes: FileAttributes) {
        val client = connectionManager.get(path.authority)
        try {
            client.setattr(path.remotePath, attributes)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
        directoryFileAttributesCache -= path
        LocalWatchService.onEntryModified(path as Java8Path)
    }

    @Throws(SftpClientException::class)
    fun stat(path: Path): FileAttributes {
        val client = connectionManager.get(path.authority)
        synchronized(directoryFileAttributesCache) {
            directoryFileAttributesCache[path]?.let {
                if (it.type != FileMode.Type.SYMLINK) {
                    return it.also { directoryFileAttributesCache -= path }
                }
            }
        }
        return try {
            client.stat(path.remotePath)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
    }

    @Throws(SftpClientException::class)
    fun symlink(link: Path, target: String) {
        val client = connectionManager.get(link.authority)
        try {
            client.symlink(link.remotePath, target)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
        LocalWatchService.onEntryCreated(link as Java8Path)
    }

    @Throws(SftpClientException::class)
    fun unlink(path: Path) {
        val client = connectionManager.get(path.authority)
        try {
            client.rm(path.remotePath)
        } catch (e: IOException) {
            throw SftpClientException(e)
        }
        directoryFileAttributesCache -= path
        LocalWatchService.onEntryDeleted(path as Java8Path)
    }

    fun close(authority: Authority) = connectionManager.close(authority)

    fun closeAll() = connectionManager.closeAll()

    internal fun mapHostKeyVerificationFailureOrNull(
        throwable: Throwable,
        authority: Authority,
        hostKeyVerifier: PinnedSftpHostKeyVerifier
    ): SftpHostKeyVerificationException? =
        SftpErrorMapper.hostKeyFailure(throwable, authority, hostKeyVerifier)

    interface Path {
        val authority: Authority
        val remotePath: String
        fun resolve(other: String): Path
    }
}
