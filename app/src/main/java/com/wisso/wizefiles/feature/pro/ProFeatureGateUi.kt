// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.pro

import android.content.Context
import androidx.fragment.app.Fragment
import com.wisso.wizefiles.core.entitlement.ProFeature

/** Compatibility shim for former paywall entry points. All features are available in 1.0.0. */
fun Context.ensureProAccess(@Suppress("UNUSED_PARAMETER") feature: ProFeature): Boolean = true
fun Fragment.ensureProAccess(@Suppress("UNUSED_PARAMETER") feature: ProFeature): Boolean = true
