// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp.client

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient

enum class Protocol(val scheme: String, val defaultPort: Int) {
    FTP("ftp", FTPClient.DEFAULT_PORT),
    FTPS("ftps", FTPSClient.DEFAULT_FTPS_PORT),
    FTPES("ftpes", FTPClient.DEFAULT_PORT);

    fun createClient(): FTPClient =
        when (this) {
            FTP -> FTPClient()
            FTPS -> FTPSClient(true)
            FTPES -> FTPSClient(false)
        }

    companion object {
        val SCHEMES: List<String> = entries.map(Protocol::scheme)

        fun fromScheme(scheme: String): Protocol =
            entries.find { protocol -> protocol.scheme == scheme }
                ?: throw IllegalArgumentException("Unsupported FTP scheme: " + scheme)
    }
}
