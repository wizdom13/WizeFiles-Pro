package com.wisso.wizefiles.storage

import com.wisso.wizefiles.provider.ftp.client.Authenticator
import com.wisso.wizefiles.provider.ftp.client.Authority
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat

object FtpServerAuthenticator : Authenticator {
    private val credentials = TransientCredentialRegistry<FtpServer, Authority>(
        authorityOf = FtpServer::authority,
        storedServers = { Settings.STORAGES.valueCompat.filterIsInstance<FtpServer>() }
    )

    override fun getPassword(authority: Authority): String? =
        credentials.find(authority)?.password

    fun addTransientServer(server: FtpServer) = credentials.add(server)
    fun removeTransientServer(server: FtpServer) = credentials.remove(server)
}
