// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import com.wisso.wizefiles.provider.smb.client.Authenticator
import com.wisso.wizefiles.provider.smb.client.Authority
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat

object SmbServerAuthenticator : Authenticator {
    private val credentials = TransientCredentialRegistry<SmbServer, Authority>(
        authorityOf = SmbServer::authority,
        storedServers = { Settings.STORAGES.valueCompat.filterIsInstance<SmbServer>() }
    )

    override fun getPassword(authority: Authority): String? =
        credentials.find(authority)?.password

    fun addTransientServer(server: SmbServer) = credentials.add(server)
    fun removeTransientServer(server: SmbServer) = credentials.remove(server)
}
