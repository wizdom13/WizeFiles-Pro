package com.wisso.wizefiles.feature.apksigning

data class ApkVerificationIssue(
    val code: String,
    val message: String
)

data class ApkVerificationReport(
    val verified: Boolean,
    val verifiedSchemes: Set<ApkSignatureScheme>,
    val signerCertificateSha256: List<String>,
    val errors: List<ApkVerificationIssue>,
    val warnings: List<ApkVerificationIssue>
) {
    fun hasVerifiedScheme(scheme: ApkSignatureScheme): Boolean = scheme in verifiedSchemes
}
