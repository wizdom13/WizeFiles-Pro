// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp.client

import org.apache.commons.net.ftp.FTPClient

internal object FtpFeatureNegotiator {
    fun configureDataConnection(client: FTPClient, mode: Mode) {
        if (mode == Mode.PASSIVE) {
            client.setUseEPSVwithIPv4(true)
            client.enterLocalPassiveMode()
        } else {
            client.enterLocalActiveMode()
        }
    }
}
