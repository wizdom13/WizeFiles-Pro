// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details.apk

import android.content.pm.PackageInfo

class ApkInfo(
    val packageInfo: PackageInfo,
    val label: String,
    val signingCertificateDigests: List<String>,
    val pastSigningCertificateDigests: List<String>
)
