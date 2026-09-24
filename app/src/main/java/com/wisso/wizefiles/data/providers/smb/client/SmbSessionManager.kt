// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb.client

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.Share
import com.wisso.wizefiles.util.closeSafe
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import jcifs.context.SingletonContext

internal class SmbSessionManager(
    private val password: (Authority) -> String?,
    private val connectionFactory: SmbConnectionFactory = SmbConnectionFactory()
) {
    private val sessions = mutableMapOf<Authority, Session>()

    fun session(authority: Authority): Session = synchronized(sessions) {
        sessions[authority]?.let { existing ->
            if (existing.connection.isConnected) return existing
            SmbErrorMapper.closeDisconnectedSession(existing)
            existing.connection.closeSafe()
            sessions -= authority
        }
        val secret = password(authority)
            ?: throw SmbClientException("No password found for $authority")
        val connection = connectionFactory.connect(authority)
        val context = AuthenticationContext(
            authority.username, secret.toCharArray(), authority.domain
        )
        val authenticated = requireSmbAuthentication(
            authenticated = try {
                connection.authenticate(context)
            } catch (failure: SMBRuntimeException) {
                connection.closeSafe()
                throw SmbClientException(failure)
            },
            authority = authority,
            closeConnection = { connection.closeSafe() }
        )
        sessions[authority] = authenticated
        authenticated
    }

    fun share(session: Session, name: String): Share = try {
        session.connectShare(name)
    } catch (failure: SMBRuntimeException) {
        throw SmbClientException(failure)
    }

    fun diskShare(session: Session, name: String): DiskShare =
        share(session, name) as? DiskShare
            ?: throw SmbClientException("$name is not a DiskShare")
}

internal class SmbConnectionFactory(private val client: SMBClient = SMBClient()) {
    fun connect(authority: Authority) = try {
        client.connect(resolve(authority.host), authority.port)
    } catch (failure: IOException) {
        throw SmbClientException(failure)
    }

    private fun resolve(host: String): String {
        val addresses = try {
            SingletonContext.getInstance().nameServiceClient.getAllByName(host, false)
                .mapNotNull { it.toInetAddress() }
        } catch (failure: UnknownHostException) {
            throw SmbClientException(failure)
        }
        return selectSmbAddress(host, addresses)
    }
}


internal fun selectSmbAddress(host: String, addresses: List<InetAddress>): String =
    (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())
        ?.hostAddress ?: throw SmbClientException("No usable network address found for $host")

internal fun <T : Any> requireSmbAuthentication(
    authenticated: T?,
    authority: Authority,
    closeConnection: () -> Unit
): T = authenticated ?: run {
    closeConnection()
    throw SmbClientException("SMB authentication returned no session for $authority")
}

internal object SmbErrorMapper {
    fun closeDisconnectedSession(session: Session) {
        try {
            session.close()
        } catch (failure: SMBRuntimeException) {
            if (!failure.isDisconnectedSmbLogoffFailure()) throw SmbClientException(failure)
        }
    }
}
