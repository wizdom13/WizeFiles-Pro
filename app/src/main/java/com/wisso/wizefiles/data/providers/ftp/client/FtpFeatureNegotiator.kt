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
