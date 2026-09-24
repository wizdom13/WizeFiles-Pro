// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

fun AutoCloseable.closeSafe() {
    try {
        close()
    } catch (e: Exception) {
        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
    }
}
