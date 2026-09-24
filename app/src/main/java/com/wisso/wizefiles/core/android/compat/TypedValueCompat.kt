package com.wisso.wizefiles.core.android.compat

import android.util.TypedValue
import androidx.core.util.TypedValueCompat

val TypedValue.complexUnitCompat: Int
    get() = TypedValueCompat.getUnitFromComplexDimension(data)
