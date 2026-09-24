// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.content.pm.ApplicationInfo
import android.os.Build

val ApplicationInfo.longVersionCodeCompat: Long
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                ApplicationInfo::class.java.getDeclaredField("longVersionCode")
                    .also { it.isAccessible = true }
                    .getLong(this)
            }.getOrDefault(0L)
        } else {
            runCatching {
                ApplicationInfo::class.java.getDeclaredField("versionCode")
                    .also { it.isAccessible = true }
                    .getInt(this)
                    .toLong()
            }.getOrDefault(0L)
        }
