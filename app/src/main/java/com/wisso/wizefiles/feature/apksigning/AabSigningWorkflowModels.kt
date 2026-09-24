// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.util.UUID

data class AabSigningWorkflowSpec(
    val operationId: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val outputUri: String,
    val signingKeyUri: String,
    val certificateUri: String = "",
    val keySource: AabSigningKeySource = AabSigningKeySource.KEY_STORE,
    val keyAlias: String = "",
    val keyStoreFormat: ApkKeyStoreFormat = ApkKeyStoreFormat.PKCS12,
    val conflictPolicy: ApkSigningOutputConflictPolicy =
        ApkSigningOutputConflictPolicy.KEEP_BOTH
) {
    init {
        require(operationId.isNotBlank()) { "A signing operation ID is required" }
        require(sourceUri.isNotBlank()) { "An input AAB is required" }
        require(outputUri.isNotBlank()) { "An output AAB is required" }
        require(signingKeyUri.isNotBlank()) { "Signing key material is required" }
        require(sourceUri != outputUri) { "The original AAB cannot be overwritten" }
        if (keySource == AabSigningKeySource.PKCS8_CERTIFICATE) {
            require(certificateUri.isNotBlank()) { "An X.509 certificate is required" }
        }
    }

    fun credentialUris(): List<String> = buildList {
        add(signingKeyUri)
        certificateUri.takeIf(String::isNotBlank)?.let(::add)
    }
}
