// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.app.Service
import androidx.core.app.ServiceCompat

fun Service.stopForegroundCompat(flags: Int) {
    ServiceCompat.stopForeground(this, flags)
}
