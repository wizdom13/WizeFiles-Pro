// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import androidx.lifecycle.LiveData
import com.wisso.wizefiles.settings.SettingLiveData

@Suppress("UNCHECKED_CAST")
val <T> LiveData<T>.valueCompat: T
    get() = when (this) {
        is SettingLiveData<*> -> currentValueCompat() as T
        else -> value as T
    }
