// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment

fun DialogFragment.show(fragment: Fragment) {
    show(fragment.childFragmentManager, null)
}
