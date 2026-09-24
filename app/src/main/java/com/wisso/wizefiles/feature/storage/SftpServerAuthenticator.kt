// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import com.wisso.wizefiles.provider.sftp.client.Authentication
import com.wisso.wizefiles.provider.sftp.client.Authenticator
import com.wisso.wizefiles.provider.sftp.client.Authority
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat

object SftpServerAuthenticator : Authenticator {
    private val credentials = TransientCredentialRegistry<SftpServer, Authority>(
        authorityOf = SftpServer::authority,
        storedServers = { Settings.STORAGES.valueCompat.filterIsInstance<SftpServer>() }
    )

    override fun getAuthentication(authority: Authority): Authentication? =
        credentials.find(authority)?.authentication

    fun addTransientServer(server: SftpServer) = credentials.add(server)
    fun removeTransientServer(server: SftpServer) = credentials.remove(server)
}
