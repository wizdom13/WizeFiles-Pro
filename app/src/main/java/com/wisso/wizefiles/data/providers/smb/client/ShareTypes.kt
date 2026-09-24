// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb.client

object ShareTypes {
    const val STYPE_DISKTREE = 0x00000000
    const val STYPE_PRINTQ = 0x00000001
    const val STYPE_DEVICE = 0x00000002
    const val STYPE_IPC = 0x00000003
    const val STYPE_CLUSTER_FS = 0x02000000
    const val STYPE_CLUSTER_SOFS = 0x04000000
    const val STYPE_CLUSTER_DFS = 0x08000000
    const val STYPE_SPECIAL = 0x80000000.toInt()
    const val STYPE_TEMPORARY = 0x40000000
}
