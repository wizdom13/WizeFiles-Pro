// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.util.UUID

enum class ApkKeyStoreFormat {
    PKCS12,
    JKS,
    BKS
}

enum class ApkSigningOutputConflictPolicy {
    FAIL,
    KEEP_BOTH
}

data class ApkSigningWorkflowSpec(
    val operationId: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val outputUri: String,
    val keyStoreUri: String,
    val keyAlias: String = "",
    val keyStoreFormat: ApkKeyStoreFormat = ApkKeyStoreFormat.PKCS12,
    val schemes: Set<ApkSignatureScheme> = setOf(
        ApkSignatureScheme.V1,
        ApkSignatureScheme.V2,
        ApkSignatureScheme.V3
    ),
    val minSdkVersion: Int? = null,
    val conflictPolicy: ApkSigningOutputConflictPolicy =
        ApkSigningOutputConflictPolicy.KEEP_BOTH
) {
    init {
        require(operationId.isNotBlank()) { "A signing operation ID is required" }
        require(sourceUri.isNotBlank()) { "An input APK is required" }
        require(outputUri.isNotBlank()) { "An output APK is required" }
        require(keyStoreUri.isNotBlank()) { "A key store is required" }
        require(sourceUri != outputUri) { "The original APK cannot be overwritten" }
        require(minSdkVersion == null || minSdkVersion > 0) {
            "Minimum SDK version must be positive"
        }
        signatureSelection()
    }

    fun signatureSelection(): ApkSignatureSelection = ApkSignatureSelection.of(schemes)
}

data class ApkSigningKeyGenerationRequest(
    val alias: String,
    val subjectDistinguishedName: String,
    val validityYears: Int = 25,
    val rsaKeySize: Int = 3072
) {
    init {
        require(alias.isNotBlank()) { "A key alias is required" }
        require(subjectDistinguishedName.isNotBlank()) { "A certificate subject is required" }
        require(validityYears in 1..100) { "Validity must be between 1 and 100 years" }
        require(rsaKeySize in setOf(2048, 3072, 4096)) { "Unsupported RSA key size" }
    }
}

data class ApkSigningKeyAlias(
    val alias: String,
    val certificateSha256: String,
    val subject: String,
    val notAfterMillis: Long
)
