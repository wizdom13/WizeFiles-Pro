// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import androidx.lifecycle.ViewModel
import com.wisso.wizefiles.storage.path.AppPath

internal enum class ApkSignVerifyMode { SIGN, VERIFY }

/** Owns durable screen selections; password material deliberately remains in transient views. */
internal class ApkSignVerifyViewModel : ViewModel() {
    var initialized = false
    var mode = ApkSignVerifyMode.SIGN
    var source: AppPath? = null
    var output: AppPath? = null
    var keyStore: AppPath? = null
    var detachedV4: AppPath? = null
    var keyStoreFormat = ApkKeyStoreFormat.PKCS12
    var pendingGeneration = false
    var resumeOperationId: String? = null

    fun switchMode(target: ApkSignVerifyMode) {
        if (mode == target) return
        mode = target
        output = null
        keyStore = null
        detachedV4 = null
        pendingGeneration = false
    }
}
