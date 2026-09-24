package com.wisso.wizefiles.feature.nearby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearbySessionRegistryTest {
    @Test
    fun `registry owns endpoint discovery and bounded names`() {
        val registry = NearbySessionRegistry()
        registry.discovered(NearbyEndpoint("endpoint", "Peer"))

        assertEquals("Peer", registry.endpoint("endpoint")?.name)
        registry.lost("endpoint")
        assertNull(registry.endpoint("endpoint"))
    }

    @Test
    fun `authentication tokens are copied and cleared with the session`() {
        val source = byteArrayOf(1, 2, 3)
        val registry = NearbySessionRegistry()
        registry.beginAuthentication("endpoint", source, 42)
        source.fill(9)

        assertArrayEquals(byteArrayOf(1, 2, 3), registry.authenticationToken())
        registry.reset()
        assertNull(registry.authenticationToken())
        assertNull(registry.pendingAuthEndpointId)
        assertEquals(0L, registry.authenticationExpiresAtMillis)
    }
}
