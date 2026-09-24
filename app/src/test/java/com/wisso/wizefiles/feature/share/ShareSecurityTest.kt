package com.wisso.wizefiles.feature.share

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareSecurityTest {
    @Test fun `canonical path accepts ordinary nested names`() {
        assertEquals("Travel/Dubai July",SharePathSecurity.canonicalRelativePath("Travel/Dubai July"))
    }
    @Test fun `traversal variants are rejected`() {
        listOf("../secret","a/../secret","%2e%2e/secret","a%2fb","a\\b","line%0d%0abreak","line\nbreak").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { SharePathSecurity.canonicalRelativePath(value) }
        }
    }
    @Test fun `privileged and private roots are excluded`() {
        listOf("root:/system","shizuku:/data","file:///data/data/app","file:///storage/emulated/0/Android/data/x","file:///safe/Vault","smb://user:password@server/share").forEach { uri ->
            assertThrows(IllegalArgumentException::class.java) { SharePathSecurity.requireShareableRoot(uri) }
        }
    }
    @Test fun `exchange is the safe default`() {
        val p=ShareProfile(name="PC Access")
        assertEquals(SharePermission.EXCHANGE_FILES,p.permission)
        assertTrue(p.permission.allows(ShareCapability.CREATE))
        assertFalse(p.permission.allows(ShareCapability.DELETE))
        assertFalse(p.ftpWritable)
        assertTrue(p.approveDestructiveInBrowser)
    }
    @Test fun `five failures temporarily block a client`() {
        var now=1L; val limiter=PairingRateLimiter(now={now})
        repeat(5){limiter.failed("pc")}; assertFalse(limiter.canAttempt("pc"))
        now+=5*60_000; assertTrue(limiter.canAttempt("pc"))
    }
    @Test fun `client pool rejects work above its bound`() {
        val entered=CountDownLatch(1)
        val release=CountDownLatch(1)
        val pool=BoundedShareClientPool(1)
        try {
            assertTrue(pool.execute {
                entered.countDown()
                release.await()
            })
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            assertFalse(pool.execute {})
        } finally {
            release.countDown()
            pool.close()
        }
    }
}
