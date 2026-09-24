// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os.syscall

import com.wisso.wizefiles.provider.common.ByteString

class StructDirent(
    val d_ino: Long, /*ino_t*/
    val d_off: Long, /*off64_t*/
    val d_reclen: Int, /*unsigned short*/
    val d_type: Int, /*unsigned char*/
    val d_name: ByteString
)
