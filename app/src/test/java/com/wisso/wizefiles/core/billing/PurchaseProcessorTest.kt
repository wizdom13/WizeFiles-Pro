package com.wisso.wizefiles.core.billing

import com.wisso.wizefiles.core.entitlement.license.LicenseClaims
import com.wisso.wizefiles.core.entitlement.license.LicenseEntitlementController
import com.wisso.wizefiles.core.entitlement.license.LicenseRejectionReason
import com.wisso.wizefiles.core.entitlement.license.LicenseVerificationResult
import com.wisso.wizefiles.core.entitlement.Entitlement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseProcessorTest {
    private val licenseController = FakeLicenseController()
    private val backend = FakeBackend()
    private val processor = PurchaseProcessor(
        packageName = "com.wisso.wizefiles",
        appVersionCode = 603,
        licenseController = licenseController,
        backendClient = backend,
    )

    @Test
    fun pendingPurchaseNeverReachesBackendOrAcknowledgement() = runTest {
        var acknowledged = false

        val result = processor.process(purchase(state = PlayPurchaseState.PENDING)) {
            acknowledged = true
            true
        }

        assertEquals(PurchaseProcessingResult.Pending(BillingProducts.PRO_LIFETIME), result)
        assertEquals(0, backend.calls)
        assertFalse(acknowledged)
    }

    @Test
    fun unknownProductIsIgnored() = runTest {
        val result = processor.process(purchase(productId = "not_wizefiles")) { true }

        assertEquals(PurchaseProcessingResult.Ignored, result)
        assertEquals(0, backend.calls)
    }

    @Test
    fun backendFailureDoesNotGrantOrAcknowledge() = runTest {
        backend.result = BackendLicenseResult.Failed(BillingError.BACKEND_UNAVAILABLE)
        var acknowledged = false

        val result = processor.process(purchase()) {
            acknowledged = true
            true
        }

        assertEquals(PurchaseProcessingResult.Failed(BillingError.BACKEND_UNAVAILABLE), result)
        assertFalse(licenseController.accepted)
        assertFalse(acknowledged)
    }

    @Test
    fun rejectedSignedDocumentDoesNotAcknowledge() = runTest {
        licenseController.acceptResult = LicenseVerificationResult.Rejected(
            LicenseRejectionReason.INVALID_SIGNATURE,
        )
        var acknowledged = false

        val result = processor.process(purchase()) {
            acknowledged = true
            true
        }

        assertEquals(PurchaseProcessingResult.Failed(BillingError.VERIFICATION_FAILED), result)
        assertFalse(acknowledged)
    }

    @Test
    fun verifiedPurchaseIsGrantedBeforeAcknowledgement() = runTest {
        val order = mutableListOf<String>()
        licenseController.onAccept = { order += "grant" }

        val result = processor.process(purchase()) {
            order += "acknowledge"
            true
        }

        assertEquals(PurchaseProcessingResult.Activated(BillingProducts.PRO_LIFETIME, false), result)
        assertEquals(listOf("grant", "acknowledge"), order)
        assertEquals(BillingProducts.PRO_LIFETIME, backend.lastRequest?.productId)
        assertEquals("installation-1", backend.lastRequest?.installationId)
    }

    @Test
    fun acknowledgementFailureKeepsVerifiedEntitlementAndRequestsRetry() = runTest {
        val result = processor.process(purchase()) { false }

        assertEquals(PurchaseProcessingResult.Activated(BillingProducts.PRO_LIFETIME, true), result)
        assertTrue(licenseController.accepted)
    }

    @Test
    fun alreadyAcknowledgedPurchaseIsNotAcknowledgedAgain() = runTest {
        var acknowledgeCalls = 0

        val result = processor.process(purchase(acknowledged = true)) {
            acknowledgeCalls++
            true
        }

        assertEquals(PurchaseProcessingResult.Activated(BillingProducts.PRO_LIFETIME, false), result)
        assertEquals(0, acknowledgeCalls)
    }

    private fun purchase(
        productId: String = BillingProducts.PRO_LIFETIME,
        state: PlayPurchaseState = PlayPurchaseState.PURCHASED,
        acknowledged: Boolean = false,
    ) = PlayPurchaseSnapshot(
        productIds = setOf(productId),
        purchaseToken = "sensitive-token",
        state = state,
        acknowledged = acknowledged,
    )

    private class FakeBackend : LicenseBackendClient {
        var calls = 0
        var lastRequest: BackendLicenseRequest? = null
        var result: BackendLicenseResult = BackendLicenseResult.SignedDocument("signed-license")

        override suspend fun verify(request: BackendLicenseRequest): BackendLicenseResult {
            calls++
            lastRequest = request
            return result
        }
    }

    private class FakeLicenseController : LicenseEntitlementController {
        var accepted = false
        var onAccept: () -> Unit = {}
        var acceptResult: LicenseVerificationResult = verifiedResult()

        override fun installationId(): String = "installation-1"

        override fun accept(serializedDocument: String): LicenseVerificationResult {
            accepted = true
            onAccept()
            return acceptResult
        }

        override fun restore(): LicenseVerificationResult? = null

        override fun clear(): Boolean = true
    }

    companion object {
        private fun verifiedResult(): LicenseVerificationResult.Verified =
            LicenseVerificationResult.Verified(
                LicenseClaims(
                    schemaVersion = 1,
                    licenseId = "license-1",
                    packageName = "com.wisso.wizefiles",
                    installationId = "installation-1",
                    entitlements = setOf(Entitlement.PRO),
                    issuedAtEpochMillis = 1,
                    notBeforeEpochMillis = 1,
                    refreshAfterEpochMillis = 2,
                    validUntilEpochMillis = 3,
                    serverTimeEpochMillis = 1,
                ),
            )
    }
}

