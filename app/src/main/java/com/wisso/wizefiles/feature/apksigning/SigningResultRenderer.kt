// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

internal object SigningResultRenderer {
    data class Sections(
        val verified: Boolean,
        val schemes: String,
        val certificates: List<String>,
        val errors: List<String>,
        val warnings: List<String>
    )

    fun sections(report: ApkVerificationReport): Sections = Sections(
        verified = report.verified,
        schemes = report.verifiedSchemes.sortedBy(ApkSignatureScheme::ordinal)
            .joinToString { it.name.lowercase() },
        certificates = report.signerCertificateSha256,
        errors = report.errors.map { "${it.code}: ${it.message}" },
        warnings = report.warnings.map { "${it.code}: ${it.message}" }
    )
}
