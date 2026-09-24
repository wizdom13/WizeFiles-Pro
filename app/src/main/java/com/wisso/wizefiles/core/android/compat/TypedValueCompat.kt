// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.util.TypedValue
import androidx.core.util.TypedValueCompat

val TypedValue.complexUnitCompat: Int
    get() = TypedValueCompat.getUnitFromComplexDimension(data)
