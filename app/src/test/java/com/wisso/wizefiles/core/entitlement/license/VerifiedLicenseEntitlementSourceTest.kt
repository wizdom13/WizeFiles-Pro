package com.wisso.wizefiles.core.entitlement.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedLicenseEntitlementSourceTest {
    private val keyPair = testKeyPair()
    private val verifier = testVerifier(keyPair)

    @Test
    fun verifiedDocumentIsCachedAndRestoredWithoutAnAuthorityBoolean() {
        val cache = FakeLicenseCache()
        val firstSource = VerifiedLicenseEntitlementSource { TEST_NOW }
        assertNull(firstSource.initialize(verifier, cache))
        assertEquals(TEST_INSTALLATION, firstSource.installationId())

        val accepted = firstSource.accept(signLicense(testClaims(), keyPair))

        assertTrue(accepted is LicenseVerificationResult.Verified)
        assertTrue(firstSource.state.value.isPro)
        assertNotNull(cache.cachedLicense)

        val restoredSource = VerifiedLicenseEntitlementSource { TEST_NOW + 1_000L }
        val restored = restoredSource.initialize(verifier, cache)

        assertTrue(restored is LicenseVerificationResult.Verified)
        assertTrue(restoredSource.state.value.isPro)
    }

    @Test
    fun invalidCachedDocumentFailsClosedAndIsDeleted() {
        val cache = FakeLicenseCache(
            CachedSignedLicense("not-json", TEST_NOW),
        )
        val source = VerifiedLicenseEntitlementSource { TEST_NOW }

        val result = source.initialize(verifier, cache)

        assertEquals(
            LicenseVerificationResult.Rejected(LicenseRejectionReason.MALFORMED_DOCUMENT),
            result,
        )
        assertFalse(source.state.value.isPro)
        assertNull(cache.cachedLicense)
    }

    @Test
    fun olderBackendResponseCannotRollTrustedTimeBackward() {
        val original = signLicense(testClaims(serverTime = TEST_NOW), keyPair)
        val cache = FakeLicenseCache(CachedSignedLicense(original, TEST_NOW))
        val source = VerifiedLicenseEntitlementSource { TEST_NOW }
        source.initialize(verifier, cache)
        val older = signLicense(
            testClaims(serverTime = TEST_NOW - 10L * 60L * 1000L),
            keyPair,
        )

        val result = source.accept(older)

        assertEquals(
            LicenseVerificationResult.Rejected(LicenseRejectionReason.CLOCK_ROLLBACK),
            result,
        )
        assertTrue(source.state.value.isPro)
        assertEquals(original, cache.cachedLicense?.serializedDocument)
    }

    @Test
    fun verifiedRevocationDocumentRemovesPro() {
        val cache = FakeLicenseCache()
        val source = VerifiedLicenseEntitlementSource { TEST_NOW }
        source.initialize(verifier, cache)
        source.accept(signLicense(testClaims(), keyPair))

        val result = source.accept(
            signLicense(testClaims(entitlements = emptySet()), keyPair),
        )

        assertTrue(result is LicenseVerificationResult.Verified)
        assertFalse(source.state.value.isPro)
    }

    private class FakeLicenseCache(
        var cachedLicense: CachedSignedLicense? = null,
    ) : LicenseCache {
        override fun installationId(): String = TEST_INSTALLATION

        override fun read(): CachedSignedLicense? = cachedLicense

        override fun write(cachedLicense: CachedSignedLicense): Boolean {
            this.cachedLicense = cachedLicense
            return true
        }

        override fun clear(): Boolean {
            cachedLicense = null
            return true
        }
    }
}
