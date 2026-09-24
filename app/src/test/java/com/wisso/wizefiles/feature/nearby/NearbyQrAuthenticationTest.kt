package com.wisso.wizefiles.feature.nearby

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyQrAuthenticationTest {
    @Test
    fun `matching tokens produce a valid one-time QR proof`() {
        val token = ByteArray(64) { index -> (index * 17).toByte() }
        assertTrue(NearbyQrAuthentication.matches(token, NearbyQrAuthentication.encode(token)))
    }

    @Test
    fun `different and replayed connection tokens are rejected`() {
        val previousToken = ByteArray(64) { 0x21 }
        val freshToken = ByteArray(64) { 0x42 }
        val previousQr = NearbyQrAuthentication.encode(previousToken)

        assertFalse(NearbyQrAuthentication.matches(freshToken, previousQr))
    }

    @Test
    fun `malformed and unsupported QR payloads are rejected`() {
        val token = ByteArray(64) { 0x33 }
        listOf(
            "",
            "WZF-NEARBY:2:unsupported",
            "WZF-NEARBY:1:",
            "WZF-NEARBY:1:not_base64!",
            "https://example.com"
        ).forEach { value ->
            assertFalse(value, NearbyQrAuthentication.matches(token, value))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty raw authentication tokens cannot be encoded`() {
        NearbyQrAuthentication.encode(ByteArray(0))
    }
}
