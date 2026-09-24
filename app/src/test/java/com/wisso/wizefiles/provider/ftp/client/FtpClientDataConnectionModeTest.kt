package com.wisso.wizefiles.provider.ftp.client

import org.apache.commons.net.ftp.FTPClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpClientDataConnectionModeTest {

    @Test
    fun `passive mode enables epsv with ipv4 and enters passive mode`() {
        val client = RecordingFTPClient()

        with(FtpClient) {
            client.applyDataConnectionMode(Mode.PASSIVE)
        }

        assertTrue(client.useEpsvWithIpv4)
        assertTrue(client.enteredPassiveMode)
        assertFalse(client.enteredActiveMode)
    }

    @Test
    fun `active mode enters active mode without enabling epsv with ipv4`() {
        val client = RecordingFTPClient()

        with(FtpClient) {
            client.applyDataConnectionMode(Mode.ACTIVE)
        }

        assertFalse(client.useEpsvWithIpv4)
        assertTrue(client.enteredActiveMode)
        assertFalse(client.enteredPassiveMode)
    }

    private class RecordingFTPClient : FTPClient() {
        var enteredPassiveMode = false
        var enteredActiveMode = false
        var useEpsvWithIpv4 = false

        override fun enterLocalPassiveMode() {
            enteredPassiveMode = true
        }

        override fun enterLocalActiveMode() {
            enteredActiveMode = true
        }

        override fun setUseEPSVwithIPv4(useEPSVwithIPv4: Boolean) {
            useEpsvWithIpv4 = useEPSVwithIPv4
        }
    }
}
