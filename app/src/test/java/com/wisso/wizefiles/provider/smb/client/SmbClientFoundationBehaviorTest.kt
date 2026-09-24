package com.wisso.wizefiles.provider.smb.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmbClientFoundationBehaviorTest {
    @Test
    fun authorityBuildsDomainQualifiedUsersAndSuppressesDefaults() {
        val anonymous = Authority("server", Authority.DEFAULT_PORT, "", "")
        val domainUser = anonymous.copy(username = "user", domain = "DOMAIN", port = 1445)

        assertNull(anonymous.toUriAuthority().userInfo)
        assertNull(anonymous.toUriAuthority().port)
        assertEquals("DOMAIN\\user@server:1445", domainUser.toString())
    }

    @Test
    fun shareFlagsKeepProtocolBitValues() {
        assertEquals(1, ShareTypes.STYPE_PRINTQ)
        assertEquals(2, ShareTypes.STYPE_DEVICE)
        assertEquals(3, ShareTypes.STYPE_IPC)
        assertEquals(Int.MIN_VALUE, ShareTypes.STYPE_SPECIAL)
    }

    @Test
    fun reparseStatusesUseUnsignedNtStatusValues() {
        assertEquals(0xC0000275L, NtStatuses.NOT_A_REPARSE_POINT)
        assertEquals(0xC0000276L, NtStatuses.INVALID_REPARSE_TAG)
        assertEquals(0xC0000277L, NtStatuses.REPARSE_TAG_MISMATCH)
    }
}
