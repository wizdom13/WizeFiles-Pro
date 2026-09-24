// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.nio.file.Path

internal class SigningKeyCoordinator(
    private val service: ProviderApkKeyStoreService = ProviderApkKeyStoreService()
) {
    fun aliases(
        path: Path,
        format: ApkKeyStoreFormat,
        secrets: ApkSigningSecrets
    ): Result<List<ApkSigningKeyAlias>> = runCatching { service.aliases(path, format, secrets) }

    fun generate(
        path: Path,
        alias: String,
        subject: String,
        secrets: ApkSigningSecrets
    ): Result<ApkSigningKeyAlias> = runCatching {
        service.generatePkcs12(
            path,
            ApkSigningKeyGenerationRequest(alias, subject),
            secrets
        )
    }
}
