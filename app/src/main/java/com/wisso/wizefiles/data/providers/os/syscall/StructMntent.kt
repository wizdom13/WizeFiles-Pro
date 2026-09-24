// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os.syscall

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.provider.common.ByteString

@Parcelize
class StructMntent(
    val mnt_fsname: ByteString,
    val mnt_dir: ByteString,
    val mnt_type: ByteString,
    val mnt_opts: ByteString,
    val mnt_freq: Int,
    val mnt_passno: Int
) : Parcelable
