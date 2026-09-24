package com.wisso.wizefiles.core.imageloader.coil

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPackageContainerIconPolicyTest {
    @Test
    fun `remote materialization requires a known size within the download ceiling`() {
        val maximum = AndroidPackageContainerIconPolicy.MAXIMUM_REMOTE_CONTAINER_BYTES

        assertFalse(AndroidPackageContainerIconPolicy.shouldMaterializeRemote(null))
        assertTrue(AndroidPackageContainerIconPolicy.shouldMaterializeRemote(0L))
        assertTrue(AndroidPackageContainerIconPolicy.shouldMaterializeRemote(maximum))
        assertFalse(AndroidPackageContainerIconPolicy.shouldMaterializeRemote(maximum + 1L))
    }

    @Test
    fun `failure cache expires so transient remote failures can retry`() {
        var nowMillis = 1_000L
        val cache = ExpiringBoundedStringSet(
            maximumSize = 2,
            ttlMillis = 100L,
            nowMillisProvider = { nowMillis }
        )

        cache.add("remote-package")
        assertTrue(cache.contains("remote-package"))

        nowMillis = 1_099L
        assertTrue(cache.contains("remote-package"))

        nowMillis = 1_100L
        assertFalse(cache.contains("remote-package"))
    }

    @Test
    fun `failure cache remains bounded and respects recent access`() {
        var nowMillis = 1_000L
        val cache = ExpiringBoundedStringSet(
            maximumSize = 2,
            ttlMillis = 1_000L,
            nowMillisProvider = { nowMillis }
        )

        cache.add("first")
        nowMillis += 1L
        cache.add("second")
        assertTrue(cache.contains("first"))
        nowMillis += 1L
        cache.add("third")

        assertFalse(cache.contains("second"))
        assertTrue(cache.contains("first"))
        assertTrue(cache.contains("third"))
    }
}
