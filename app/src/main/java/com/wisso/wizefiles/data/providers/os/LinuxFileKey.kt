// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class LinuxFileKey(
    private val deviceId: Long,
    private val inodeNumber: Long
) : Parcelable
