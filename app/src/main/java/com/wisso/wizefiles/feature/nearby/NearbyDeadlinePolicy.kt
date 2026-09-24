// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

internal object NearbyDeadlinePolicy {
    fun deadline(nowMillis: Long, durationMillis: Long): Long {
        require(nowMillis >= 0) { "Current time must be non-negative" }
        require(durationMillis > 0) { "Duration must be positive" }
        return Math.addExact(nowMillis, durationMillis)
    }

    fun isExpired(nowMillis: Long, deadlineMillis: Long): Boolean = nowMillis >= deadlineMillis
}
