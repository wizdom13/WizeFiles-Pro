// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import android.os.Parcelable

/**
 * App-owned path abstraction (retrofile independent).
 */
interface AppPath : Parcelable {
    val rawPath: String
    val name: String
}
