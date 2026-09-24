package com.wisso.wizefiles.feature.packageinstaller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivilegedInstallCommandsTest {
    @Test
    fun `builds a bounded cross-user downgrade session command`() {
        assertEquals(
            "pm install-create -r -S 4096 -d --user 10",
            PrivilegedInstallCommands.create(4096, allowDowngrade = true, targetUserId = 10)
        )
    }

    @Test
    fun `quotes split names and staged paths`() {
        assertEquals(
            "pm install-write -S 42 17 'feature'\\''s.apk' '/tmp/app bundle.apk'",
            PrivilegedInstallCommands.write(17, "feature's.apk", 42, "/tmp/app bundle.apk")
        )
    }

    @Test
    fun `extracts the package manager session id`() {
        assertEquals(
            123,
            PrivilegedInstallCommands.parseSessionId("Success: created install session [123]")
        )
    }

    @Test
    fun `rejects line breaks before shell execution`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrivilegedInstallCommands.write(1, "base.apk", 1, "/tmp/app.apk\npm uninstall victim")
        }
    }
}
