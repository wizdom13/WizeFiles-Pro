package com.wisso.wizefiles.core.entitlement.license

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseVerifierTest {
    private val keyPair = testKeyPair()
    private val verifier = testVerifier(keyPair)

    @Test
    fun acceptsValidBackendSignedLicense() {
        val result = verifier.verify(signLicense(testClaims(), keyPair), TEST_NOW)

        assertTrue(result is LicenseVerificationResult.Verified)
        result as LicenseVerificationResult.Verified
        assertTrue(result.claims.entitlements.isNotEmpty())
    }

    @Test
    fun rejectsPayloadChangedAfterSigning() {
        val original = SignedLicenseDocument.parse(signLicense(testClaims(), keyPair))!!
        val changedBytes = Base64.getUrlDecoder().decode(original.payload)
        changedBytes[changedBytes.lastIndex] = (changedBytes.last().toInt() xor 1).toByte()
        val changedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(changedBytes)
        val tampered = original.copy(payload = changedPayload).serialize()

        assertRejected(tampered, LicenseRejectionReason.INVALID_SIGNATURE)
    }

    @Test
    fun rejectsUnknownSigningKey() {
        val document = signLicense(testClaims(), keyPair, keyId = "unknown")

        assertRejected(document, LicenseRejectionReason.UNKNOWN_KEY)
    }

    @Test
    fun rejectsLicenseForAnotherPackage() {
        val document = signLicense(
            testClaims(packageName = "com.example.other"),
            keyPair,
        )

        assertRejected(document, LicenseRejectionReason.PACKAGE_MISMATCH)
    }

    @Test
    fun rejectsLicenseForAnotherInstallation() {
        val document = signLicense(
            testClaims(installationId = "another-installation"),
            keyPair,
        )

        assertRejected(document, LicenseRejectionReason.INSTALLATION_MISMATCH)
    }

    @Test
    fun rejectsExpiredOfflineLease() {
        val document = signLicense(
            testClaims(
                serverTime = TEST_NOW - 60_000L,
                validUntil = TEST_NOW,
            ),
            keyPair,
        )

        assertRejected(document, LicenseRejectionReason.EXPIRED)
    }

    @Test
    fun trustsSignedServerTimeWhenDeviceClockIsBehind() {
        val document = signLicense(testClaims(), keyPair)

        val result = verifier.verify(document, TEST_NOW - 24L * 60L * 60L * 1000L)

        assertTrue(result is LicenseVerificationResult.Verified)
    }

    @Test
    fun rejectsLicenseWhoseActivationTimeIsStillInTheFuture() {
        val claims = testClaims().copy(notBeforeEpochMillis = TEST_NOW + 10L * 60L * 1000L)
        val document = signLicense(claims, keyPair)

        assertRejected(document, LicenseRejectionReason.NOT_YET_VALID)
    }

    private fun assertRejected(
        serializedDocument: String,
        expectedReason: LicenseRejectionReason,
    ) {
        val result = verifier.verify(serializedDocument, TEST_NOW)
        assertEquals(
            LicenseVerificationResult.Rejected(expectedReason),
            result,
        )
    }
}
