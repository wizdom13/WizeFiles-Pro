package com.wisso.wizefiles.core.billing

import com.wisso.wizefiles.core.entitlement.license.LicenseEntitlementController
import com.wisso.wizefiles.core.entitlement.license.LicenseVerificationResult

enum class PlayPurchaseState {
    PENDING,
    PURCHASED,
    UNSPECIFIED,
}

data class PlayPurchaseSnapshot(
    val productIds: Set<String>,
    val purchaseToken: String,
    val state: PlayPurchaseState,
    val acknowledged: Boolean,
)

data class BackendLicenseRequest(
    val packageName: String,
    val installationId: String,
    val productId: String,
    val productType: BillingProductType,
    val purchaseToken: String,
    val appVersionCode: Long,
)

sealed interface BackendLicenseResult {
    data class SignedDocument(val serializedDocument: String) : BackendLicenseResult

    data class Failed(val error: BillingError) : BackendLicenseResult
}

fun interface LicenseBackendClient {
    suspend fun verify(request: BackendLicenseRequest): BackendLicenseResult
}

fun interface PurchaseAcknowledger {
    suspend fun acknowledge(purchaseToken: String): Boolean
}

sealed interface PurchaseProcessingResult {
    data object Ignored : PurchaseProcessingResult

    data class Pending(val productId: String) : PurchaseProcessingResult

    data class Activated(
        val productId: String,
        val acknowledgementPending: Boolean,
    ) : PurchaseProcessingResult

    data class Failed(val error: BillingError) : PurchaseProcessingResult
}

class PurchaseProcessor(
    private val packageName: String,
    private val appVersionCode: Long,
    private val licenseController: LicenseEntitlementController,
    private val backendClient: LicenseBackendClient,
) {
    suspend fun process(
        purchase: PlayPurchaseSnapshot,
        acknowledger: PurchaseAcknowledger,
    ): PurchaseProcessingResult {
        val productId = purchase.productIds.singleOrNull()
            ?: return PurchaseProcessingResult.Ignored
        val productType = BillingProducts.typeOf(productId)
            ?: return PurchaseProcessingResult.Ignored
        if (purchase.purchaseToken.isBlank()) return PurchaseProcessingResult.Ignored
        when (purchase.state) {
            PlayPurchaseState.PENDING -> return PurchaseProcessingResult.Pending(productId)
            PlayPurchaseState.UNSPECIFIED -> return PurchaseProcessingResult.Ignored
            PlayPurchaseState.PURCHASED -> Unit
        }
        val installationId = licenseController.installationId()
            ?: return PurchaseProcessingResult.Failed(BillingError.VERIFICATION_FAILED)
        val backendResult = try {
            backendClient.verify(
                BackendLicenseRequest(
                    packageName = packageName,
                    installationId = installationId,
                    productId = productId,
                    productType = productType,
                    purchaseToken = purchase.purchaseToken,
                    appVersionCode = appVersionCode,
                ),
            )
        } catch (_: Exception) {
            BackendLicenseResult.Failed(BillingError.BACKEND_UNAVAILABLE)
        }
        if (backendResult is BackendLicenseResult.Failed) {
            return PurchaseProcessingResult.Failed(backendResult.error)
        }
        backendResult as BackendLicenseResult.SignedDocument
        val verification = runCatching {
            licenseController.accept(backendResult.serializedDocument)
        }.getOrNull()
        if (verification !is LicenseVerificationResult.Verified) {
            return PurchaseProcessingResult.Failed(BillingError.VERIFICATION_FAILED)
        }
        val acknowledged = purchase.acknowledged || try {
            acknowledger.acknowledge(purchase.purchaseToken)
        } catch (_: Exception) {
            false
        }
        val acknowledgementPending = !acknowledged
        return PurchaseProcessingResult.Activated(productId, acknowledgementPending)
    }
}
