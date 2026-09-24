package com.wisso.wizefiles.core.entitlement.license

interface LicenseEntitlementController {
    fun installationId(): String?

    fun accept(serializedDocument: String): LicenseVerificationResult

    fun restore(): LicenseVerificationResult?

    fun clear(): Boolean
}

object UnsupportedBuildLicenseController : LicenseEntitlementController {
    override fun installationId(): String? = null

    override fun accept(serializedDocument: String): LicenseVerificationResult =
        LicenseVerificationResult.Rejected(LicenseRejectionReason.UNSUPPORTED_BUILD)

    override fun restore(): LicenseVerificationResult? = null

    override fun clear(): Boolean = true
}
