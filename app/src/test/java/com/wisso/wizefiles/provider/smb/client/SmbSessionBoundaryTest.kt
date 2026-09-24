package com.wisso.wizefiles.provider.smb.client

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbSessionBoundaryTest {
    @Test fun `empty resolution remains a typed SMB failure`() {
        val failure = assertThrows(SmbClientException::class.java) {
            selectSmbAddress("missing.example", emptyList())
        }
        assertTrue(failure.message.orEmpty().contains("No usable network address"))
    }

    @Test fun `resolution prefers IPv4 and falls back to another usable address`() {
        val ipv6 = InetAddress.getByAddress(ByteArray(16).apply { this[15] = 1 })
        val ipv4 = InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 1))
        assertEquals(ipv4.hostAddress, selectSmbAddress("example", listOf(ipv6, ipv4)))
        assertEquals(ipv6.hostAddress, selectSmbAddress("example", listOf(ipv6)))
    }

    @Test fun `null authentication closes connection and remains a typed failure`() {
        var closed = false
        val authority = Authority("example.com", Authority.DEFAULT_PORT, "user", null)
        val failure = assertThrows(SmbClientException::class.java) {
            requireSmbAuthentication<String>(null, authority) { closed = true }
        }
        assertTrue(closed)
        assertTrue(failure.message.orEmpty().contains("authentication returned no session"))
    }

    @Test fun `successful authentication is returned without closing connection`() {
        var closed = false
        val authority = Authority("example.com", Authority.DEFAULT_PORT, "user", null)
        val session = Any()
        val result = requireSmbAuthentication(session, authority) { closed = true }
        assertSame(session, result)
        assertFalse(closed)
    }
}
