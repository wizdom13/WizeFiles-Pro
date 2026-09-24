// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import com.wisso.wizefiles.util.closeSafe
import java.io.IOException
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException

internal class SftpConnectionManager(
    private val authentication: (Authority) -> Authentication?
) {
    private data class Connection(val ssh: SSHClient, val sftp: SFTPClient)
    private val connections = mutableMapOf<Authority, Connection>()

    fun get(authority: Authority): SFTPClient = synchronized(connections) {
        connections[authority]?.let { existing ->
            if (existing.sftp.sftpEngine.subsystem.isOpen) return existing.sftp
            close(existing)
            connections -= authority
        }
        val auth = authentication(authority)
            ?: throw SftpClientException("No authentication found for $authority")
        val verifier = PinnedSftpHostKeyVerifier(SftpHostKeyTrustStore.appInstance)
        val ssh = SSHClient().apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            timeout = SOCKET_TIMEOUT_MILLIS
            addHostKeyVerifier(verifier)
        }
        try {
            ssh.connect(authority.host, authority.port)
        } catch (failure: IOException) {
            ssh.closeSafe()
            SftpErrorMapper.hostKeyFailure(failure, authority, verifier)
                ?.let { throw SftpClientException(it) }
            throw SftpClientException(failure)
        }
        try {
            ssh.auth(authority.username, auth.toAuthMethod())
        } catch (failure: UserAuthException) {
            ssh.closeSafe()
            throw SftpClientException(failure)
        } catch (failure: TransportException) {
            ssh.closeSafe()
            throw SftpClientException(failure)
        }
        val sftp = try {
            ssh.newSFTPClient()
        } catch (failure: IOException) {
            ssh.closeSafe()
            throw SftpClientException(failure)
        }
        connections[authority] = Connection(ssh, sftp)
        sftp
    }

    fun close(authority: Authority) {
        synchronized(connections) { connections.remove(authority) }?.let(::close)
    }

    fun closeAll() {
        synchronized(connections) { connections.values.toList().also { connections.clear() } }
            .forEach(::close)
    }

    private fun close(connection: Connection) {
        connection.sftp.closeSafe()
        connection.ssh.closeSafe()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val SOCKET_TIMEOUT_MILLIS = 30_000
    }
}

internal object SftpErrorMapper {
    fun hostKeyFailure(
        throwable: Throwable,
        authority: Authority,
        verifier: PinnedSftpHostKeyVerifier
    ): SftpHostKeyVerificationException? = verifier.consumeFailure()
        ?: SftpHostKeyFailureParser.parseUnknownHostKey(
            throwable, authority.host, authority.port
        )
}
