package com.wisso.wizefiles.feature.apksigning

import java.util.UUID

data class ApksSigningWorkflowSpec(
    val operationId: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val outputUri: String,
    val signingKeyUri: String,
    val certificateUri: String = "",
    val keySource: AabSigningKeySource = AabSigningKeySource.KEY_STORE,
    val keyAlias: String = "",
    val keyStoreFormat: ApkKeyStoreFormat = ApkKeyStoreFormat.PKCS12,
    val schemes: Set<ApkSignatureScheme> = setOf(
        ApkSignatureScheme.V1,
        ApkSignatureScheme.V2,
        ApkSignatureScheme.V3
    ),
    val conflictPolicy: ApkSigningOutputConflictPolicy =
        ApkSigningOutputConflictPolicy.KEEP_BOTH
) {
    init {
        require(operationId.isNotBlank()) { "A signing operation ID is required" }
        require(sourceUri.isNotBlank()) { "An input APKS is required" }
        require(outputUri.isNotBlank()) { "An output APKS is required" }
        require(signingKeyUri.isNotBlank()) { "Signing key material is required" }
        require(sourceUri != outputUri) { "The original APKS cannot be overwritten" }
        require(ApkSignatureScheme.V4 !in schemes) {
            "APKS cannot contain detached v4 signature sidecars"
        }
        signatureSelection()
        if (keySource == AabSigningKeySource.PKCS8_CERTIFICATE) {
            require(certificateUri.isNotBlank()) { "An X.509 certificate is required" }
        }
    }

    fun signatureSelection(): ApkSignatureSelection = ApkSignatureSelection.of(schemes)

    fun credentialUris(): List<String> = buildList {
        add(signingKeyUri)
        certificateUri.takeIf(String::isNotBlank)?.let(::add)
    }
}
