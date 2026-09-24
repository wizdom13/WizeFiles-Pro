// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.os.Build
import java.io.InputStream

internal fun nearbyDeviceName(): String =
    Build.MODEL.take(32).ifBlank { "Android device" }

internal fun skipNearbyInputFully(input: InputStream, offset: Long) {
    var remaining = offset
    while (remaining > 0) {
        val skipped = input.skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
        } else {
            check(input.read() >= 0) { "Source ended early" }
            remaining--
        }
    }
}
