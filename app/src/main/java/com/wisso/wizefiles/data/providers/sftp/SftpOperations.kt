package com.wisso.wizefiles.provider.sftp

import com.wisso.wizefiles.provider.sftp.client.SftpClient
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.OpenMode
import java.nio.channels.SeekableByteChannel

/** Concrete SFTP protocol seam used by mutation code and deterministic fault tests. */
internal interface SftpOperations {
    fun lstat(path: SftpPath): FileAttributes
    fun stat(path: SftpPath): FileAttributes
    fun openByteChannel(
        path: SftpPath,
        flags: Set<OpenMode>,
        attributes: FileAttributes
    ): SeekableByteChannel
    fun mkdir(path: SftpPath, attributes: FileAttributes)
    fun remove(path: SftpPath)
    fun rename(source: SftpPath, target: SftpPath)
    fun readlink(path: SftpPath): String
    fun symlink(link: SftpPath, target: String)
    fun setstat(path: SftpPath, attributes: FileAttributes)

    companion object {
        val DEFAULT: SftpOperations = object : SftpOperations {
            override fun lstat(path: SftpPath) = SftpClient.lstat(path)
            override fun stat(path: SftpPath) = SftpClient.stat(path)
            override fun openByteChannel(
                path: SftpPath,
                flags: Set<OpenMode>,
                attributes: FileAttributes
            ) = SftpClient.openByteChannel(path, flags, attributes)
            override fun mkdir(path: SftpPath, attributes: FileAttributes) =
                SftpClient.mkdir(path, attributes)
            override fun remove(path: SftpPath) = SftpClient.remove(path)
            override fun rename(source: SftpPath, target: SftpPath) = SftpClient.rename(source, target)
            override fun readlink(path: SftpPath) = SftpClient.readlink(path)
            override fun symlink(link: SftpPath, target: String) = SftpClient.symlink(link, target)
            override fun setstat(path: SftpPath, attributes: FileAttributes) =
                SftpClient.setstat(path, attributes)
        }
    }
}
