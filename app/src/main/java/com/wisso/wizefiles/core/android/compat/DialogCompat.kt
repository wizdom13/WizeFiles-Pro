// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.app.Dialog
import android.view.View
import androidx.annotation.IdRes
import androidx.core.app.DialogCompat

@Suppress("UNCHECKED_CAST")
fun <T : View> Dialog.requireViewByIdCompat(@IdRes id: Int): T =
    DialogCompat.requireViewById(this, id) as T
