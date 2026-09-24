// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import kotlin.comparisons.reversed as kotlinReversed
import kotlin.collections.removeFirst as kotlinRemoveFirst
import kotlin.collections.removeLast as kotlinRemoveLast

fun <T> Comparator<T>.reversedCompat(): Comparator<T> = kotlinReversed()

fun <T> MutableList<T>.removeFirstCompat(): T = kotlinRemoveFirst()

fun <T> MutableList<T>.removeLastCompat(): T = kotlinRemoveLast()
