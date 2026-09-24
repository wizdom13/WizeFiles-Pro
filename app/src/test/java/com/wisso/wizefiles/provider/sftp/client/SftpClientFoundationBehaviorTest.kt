// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import net.schmizz.sshj.userauth.method.AuthPassword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpClientFoundationBehaviorTest {
    @Test
    fun authoritySuppressesDefaultPortAndBlankUser() {
        val default = Authority("example.com", Authority.DEFAULT_PORT, " ")
        val custom = default.copy(port = 2222, username = "user")

        assertNull(default.toUriAuthority().userInfo)
        assertNull(default.toUriAuthority().port)
        assertEquals("user@example.com:2222", custom.toString())
    }

    @Test
    fun passwordAuthenticationCreatesAnSshPasswordMethod() {
        assertTrue(PasswordAuthentication("secret").toAuthMethod() is AuthPassword)
    }

    @Test
    fun publicKeyValidationReportsMalformedKeys() {
        assertNotNull(PublicKeyAuthentication.validate("not a private key", null))
    }
}
