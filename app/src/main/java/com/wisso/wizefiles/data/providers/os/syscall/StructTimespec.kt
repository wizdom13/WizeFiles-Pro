// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os.syscall

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * @see android.system.StructTimespec
 */
@Parcelize
class StructTimespec(
    val tv_sec: Long, /*time_t*/
    val tv_nsec: Long
) : Parcelable
