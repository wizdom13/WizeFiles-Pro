// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.nio.file.Path

internal class ApkVerificationExecutor(
    private val verifier: ProviderApkVerifier = ProviderApkVerifier()
) {
    fun verify(input: Path, detachedV4: Path?, minimumSdk: Int?): Result<ApkVerificationReport> =
        runCatching { verifier.verify(input, detachedV4, minimumSdk) }
}
